use std::{
    collections::HashMap,
    fmt::Debug,
    path::{Path, PathBuf},
};

use anyhow::{Ok, Result};
use serde::Deserialize;
use serde_json::Value;

const VERSION_MANIFEST: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

struct Lib<'a> {
    url: &'a str,
    path: PathBuf,
}

impl<'a> Lib<'a> {
    fn new(url: &'a str, path: PathBuf) -> Lib<'a> {
        Self { url, path }
    }

    async fn spawn_download_proc(self) -> Result<PathBuf> {
        crate::download_file(self.url, self.path).await
    }
}

#[derive(Deserialize, Debug)]
struct Version {
    url: String,
    id: String,
}

#[derive(Deserialize, Debug)]
struct VersionManifest {
    versions: Vec<Version>,
}

pub async fn bootstrap(version: &str, output: &Path) -> Result<Vec<String>> {
    println!("[STEP] Bootstraping run environment...");
    let mf = reqwest::get(VERSION_MANIFEST)
        .await?
        .json::<VersionManifest>()
        .await?;
    let versions = mf
        .versions
        .into_iter()
        .map(|v| (v.id, v.url))
        .collect::<HashMap<_, _>>();
    let version_data = reqwest::get(&versions[version])
        .await?
        .json::<Value>()
        .await?;
    let urls: Vec<_> = version_data
        .get("libraries")
        .unwrap()
        .as_array()
        .unwrap()
        .iter()
        .filter_map(|lib| {
            let artifact = lib.get("downloads").unwrap().get("artifact")?;
            (
                artifact.get("url")?.as_str()?,
                output.join(artifact.get("path")?.as_str()?),
            )
                .into()
        })
        .map(|(url, path)| Lib::new(url, path))
        .map(Lib::spawn_download_proc)
        .collect();
    let paths = futures::future::join_all(urls)
        .await
        .iter()
        .flatten()
        .flat_map(|it| it.to_str())
        .map(str::to_string)
        .collect::<Vec<_>>();

    let client_url = version_data
        .get("downloads")
        .and_then(|downloads| downloads.get("client")?.get("url")?.as_str())
        .unwrap();

    let client_mappings = version_data
        .get("downloads")
        .and_then(|downloads| downloads.get("client_mappings")?.get("url")?.as_str())
        .unwrap();

    _ = tokio::join!(
        crate::download_file(
            client_url,
            format!("{}/{version}.jar", output.to_string_lossy())
        ),
        crate::download_file(
            client_mappings,
            format!("{}/{version}.proguard", output.to_string_lossy())
        ),
    );


    println!("[STEP] Finished bootstraping run environment");
    Ok(paths)
}
