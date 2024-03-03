use std::{collections::HashMap, path::Path};

use anyhow::{Ok, Result};
use serde_json::Value;
use tokio::task;

use crate::{download_file, VersionManifest};

pub(crate) const VERSION_MANIFEST: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

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
            let artifact = lib.get("downloads")?.get("artifact")?;
            (
                artifact.get("url")?.as_str()?.to_owned(),
                output.join(artifact.get("path")?.as_str()?),
            )
                .into()
        })
        .map(|(url, path)| task::spawn(download_file(url, path)))
        .collect();

    // TODO: Make sure all files are properly returned
    let mut paths = Vec::with_capacity(urls.len());
    for res in urls {
        paths.push(res.await??.to_string_lossy().to_string());
    }

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
            &client_url,
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
