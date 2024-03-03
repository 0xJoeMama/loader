use std::path::Path;

use anyhow::{Ok, Result};
use tokio::task;

use crate::{download_file, VersionMeta};

pub async fn bootstrap(version: &VersionMeta, output: &Path) -> Result<Vec<String>> {
    println!("[STEP] Bootstraping run environment...");
    let urls: Vec<_> = version
        .libraries
        .iter()
        .filter_map(|lib| {
            let artifact = &lib.downloads.artifact;
            (artifact.url.to_owned(), output.join(&artifact.path)).into()
        })
        .map(|(url, path)| task::spawn(download_file(url, path)))
        .collect();

    let mut paths = Vec::with_capacity(urls.len());
    for res in urls {
        paths.push(res.await??.to_string_lossy().to_string());
    }

    let client_url = &version.downloads["client"].url;

    let client_mappings = &version.downloads["client_mappings"].url;

    _ = tokio::join!(
        crate::download_file(
            &client_url,
            format!("{}/{}.jar", output.to_string_lossy(), version.id)
        ),
        crate::download_file(
            client_mappings,
            format!("{}/{}.proguard", output.to_string_lossy(), version.id)
        ),
    );

    println!("[STEP] Finished bootstraping run environment");
    Ok(paths)
}
