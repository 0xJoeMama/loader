use std::path::Path;

use anyhow::{Ok, Result};
use serde::Deserialize;
use tokio::fs;

use crate::download_file;

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
    let dep_mf = fs::read_to_string("loader_deps.json").await?;
    let dep_mf: DepManifest = serde_json::from_str(&dep_mf)?;

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
