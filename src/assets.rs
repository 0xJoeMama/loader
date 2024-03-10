use anyhow::{Ok, Result};
use std::path::{Path, PathBuf};
use tokio::fs;

use crate::{AssetIndex, VersionMeta};

pub struct AssetResult {
    pub id: String,
    pub asset_path: PathBuf,
}

pub async fn assets<'a>(version: &'a VersionMeta, output_path: &Path) -> Result<AssetResult> {
    println!("[STEP] Assuring assets exist...");

    let output_path = output_path.join("assets");
    let idx = &version.asset_index;

    let id = idx.id.clone();
    let url = &idx.url;

    let idx_json = reqwest::get(url).await?.bytes().await?;

    let idx = serde_json::from_slice::<AssetIndex>(&idx_json)?;

    let objects = output_path.join("objects");
    idx.download_assets(&objects).await?;

    let index = output_path.join(format!("indexes/{id}.json"));
    fs::create_dir_all(index.parent().expect("can't use root as object dir")).await?;
    fs::write(&index, idx_json).await?;

    println!("[STEP] Assets downloaded");
    Ok(AssetResult {
        id,
        asset_path: output_path,
    })
}
