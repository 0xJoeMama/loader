use anyhow::Result;
use serde::Deserialize;
use std::collections::HashMap;
use std::fmt::Debug;
use std::path::{Path, PathBuf};
use std::process::Command;
use tokio::io::AsyncWriteExt;
use tokio::task;

pub const RESOURCES_URL: &str = "https://resources.download.minecraft.net/";
pub const VERSION_MANIFEST: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

// TODO: probably do some SHA1 verification
pub async fn download_file<T, U>(url: U, dest: T) -> Result<PathBuf>
where
    T: Into<PathBuf> + Debug + Clone,
    U: AsRef<str>,
{
    let url = url.as_ref();
    use tokio::fs;
    let dest: PathBuf = dest.into();
    fs::create_dir_all(
        dest.parent()
            .expect("cannot accept root as a destination folder"),
    )
    .await?;
    let file = fs::OpenOptions::new()
        .create_new(true)
        .append(true)
        .open(&dest)
        .await;

    if let Result::Ok(mut file) = file {
        println!(
            "[INFO] Starting download of file: {} from {url}",
            dest.to_string_lossy()
        );

        let mut res = reqwest::get(url).await?;
        while let Some(chunk) = res.chunk().await? {
            _ = file.write(&chunk).await?;
        }

        file.flush().await?;

        println!(
            "[INFO] File {} finished downloading",
            dest.to_string_lossy()
        );
    }

    Ok(dest)
}

pub fn run_cmd(program: &str, args: &[&str]) -> Result<()> {
    let mut cmd = Command::new(program);
    cmd.args(args);

    print!("[CMD] {program} ");
    for i in cmd.get_args() {
        print!("{} ", i.to_string_lossy());
    }
    println!();

    _ = cmd.spawn().and_then(|c| c.wait_with_output())?;
    Ok(())
}

#[derive(Deserialize)]
pub struct Artifact {
    pub url: String,
    pub path: String,
}

#[derive(Deserialize)]
pub struct VersionDownload {
    pub url: String,
}

#[derive(Deserialize)]
pub struct Download {
    pub artifact: Artifact,
}

#[derive(Deserialize)]
pub struct Library {
    pub downloads: Download,
}

#[derive(Deserialize)]
pub struct AssetIndexMeta {
    pub id: String,
    pub url: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionMeta {
    pub libraries: Vec<Library>,
    pub downloads: HashMap<String, VersionDownload>,
    pub asset_index: AssetIndexMeta,
    pub id: String,
}

#[derive(Deserialize, Debug)]
pub struct Version {
    pub url: String,
    pub id: String,
}

#[derive(Deserialize, Debug)]
pub struct VersionManifest {
    pub versions: Vec<Version>,
}

impl VersionManifest {
    pub async fn fetch() -> Result<VersionManifest> {
        Ok(reqwest::get(VERSION_MANIFEST)
            .await?
            .json::<VersionManifest>()
            .await?)
    }

    pub async fn get_version(&self, version: impl AsRef<str>) -> Result<VersionMeta> {
        let version = self
            .versions
            .iter()
            .position(|v| v.id == version.as_ref())
            .map(|i| &self.versions[i])
            .expect("unknown version");

        Ok(reqwest::get(&version.url)
            .await?
            .json::<VersionMeta>()
            .await?)
    }
}

#[derive(Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
}

impl AssetIndex {
    pub async fn download_assets(&self, object_path: &Path) -> Result<()> {
        let mut objects = self.objects.values().collect::<Vec<_>>();

        // sort by size to group large downloads together
        objects.sort_unstable_by_key(|obj| obj.size);

        // fetch 50 asset files at once
        for chunk in objects.chunks(50) {
            let tasks = chunk
                .iter()
                .map(|obj| {
                    task::spawn(download_file(
                        obj.get_url(),
                        object_path.join(obj.get_path()),
                    ))
                })
                .collect::<Vec<_>>();
            for res in tasks {
                if let Result::Err(e) = res.await? {
                    println!("could not download file {}", object_path.to_string_lossy());
                    return Result::Err(e);
                }
            }
        }
        Ok(())
    }
}

#[derive(Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: usize,
}

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

pub mod assets;
pub mod bootstrap;
pub mod classpath;
pub mod decomp;
pub mod loader_deps;
pub mod make_loader;
