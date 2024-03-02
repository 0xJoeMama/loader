use std::path::Path;

use anyhow::{Ok, Result};
use serde::Deserialize;

use crate::download_file;

// HARDCODED because why not. At the end of the day nobody should be messing with the loader
// dependencies
const LOADER_DEPS: &str = include_str!("../loader_deps.json");

#[derive(Deserialize)]
struct DepManifest {
    deps: Vec<Dep>,
}

#[derive(Deserialize)]
struct Dep {
    url: String,
    name: String,
}

pub async fn loader_deps(output: &Path) -> Result<Vec<String>> {
    let dep_mf: DepManifest = serde_json::from_str(LOADER_DEPS)?;

    let files = dep_mf
        .deps
        .iter()
        .map(|url| {
            download_file(
                &url.url,
                format!("{}/loader_libs/{}", output.to_string_lossy(), url.name),
            )
        })
        .collect::<Vec<_>>();

    Ok(futures::future::join_all(files)
        .await
        .into_iter()
        .flatten()
        .map(|it| it.to_string_lossy().to_string())
        .collect::<Vec<_>>())
}
