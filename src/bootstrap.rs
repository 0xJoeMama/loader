use std::path::{Path, PathBuf};

use anyhow::{Ok, Result};
use serde::Serialize;
use tokio::task;

use crate::{download_file, VersionMeta};

#[derive(Serialize)]
pub struct BootstrapResult {
    pub mappings: PathBuf,
    pub version_jar: PathBuf,
    pub classpath_entries: Vec<PathBuf>,
}

pub async fn bootstrap(version: &VersionMeta, output: &Path) -> Result<BootstrapResult> {
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

    // TODO: When async closures are allowed, make sure we switch to an iterator
    let mut classpath = Vec::with_capacity(urls.len());
    for res in urls {
        classpath.push(res.await??)
    }

    let client_url = &version.downloads["client"].url;
    let client_mappings = &version.downloads["client_mappings"].url;

    let (jar, mappings) = tokio::join!(
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
    Ok(BootstrapResult {
        mappings: mappings?,
        version_jar: jar?,
        classpath_entries: classpath,
    })
}
