use anyhow::Result;
use serde_json::Value;
use std::{
    collections::HashMap,
    path::{Path, PathBuf},
};
use tokio::{fs, task};

use serde::Deserialize;

use crate::{bootstrap, download_file, VersionManifest};

#[derive(Deserialize)]
struct AssetIndex {
    objects: HashMap<String, AssetObject>,
}

impl AssetIndex {
    async fn download_assets(&self, object_path: &Path) -> Result<()> {
        let download_tasks = self
            .objects
            .values()
            .map(|obj| async {
                task::spawn(download_file(
                    obj.get_url(),
                    object_path.join(obj.get_path()),
                ))
                .await
            })
            .collect::<Vec<_>>();

        for res in download_tasks {
            if let Result::Err(e) = res.await? {
                println!("could not download file {}", object_path.to_string_lossy());
                return Result::Err(e);
            }
        }
        Ok(())
    }
}

#[derive(Deserialize)]
struct AssetObject {
    hash: String,
}
const RESOURCES_URL: &str = "https://resources.download.minecraft.net/";

impl AssetObject {
    fn get_url(&self) -> String {
        let mut path = self.get_path();
        path.insert_str(0, RESOURCES_URL);
        path
    }
    fn get_path(&self) -> String {
        let mut res = String::from(&self.hash[0..2]);
        res.push('/');
        res.push_str(&self.hash);
        res
    }
}

pub async fn assets(version: &str, output_path: &Path) -> Result<(String, PathBuf)> {
    println!("[STEP] Downloading assets...");
    let output_path = output_path.join("assets");
    let mf = reqwest::get(bootstrap::VERSION_MANIFEST)
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
    let idx = version_data.get("assetIndex").unwrap();

    let id = idx.get("id").unwrap().as_str().unwrap().to_string();
    let url = idx.get("url").unwrap().as_str().unwrap();

    let idx_json = reqwest::get(url).await?.bytes().await?;

    let idx = serde_json::from_slice::<AssetIndex>(&idx_json)?;

    let objects = output_path.join("objects");
    idx.download_assets(&objects).await?;

    let index = output_path.join(format!("indexes/{id}.json"));
    fs::create_dir_all(index.parent().expect("can't use root as object dir")).await?;
    fs::write(&index, idx_json).await?;

    println!("[STEP] Assets downloaded");
    Ok((id, output_path))
}
