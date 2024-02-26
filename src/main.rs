use std::{collections::HashMap, env, fmt::Debug, path::PathBuf};

use anyhow::{Ok, Result};
use serde::Deserialize;
use serde_json::Value;
use tokio::io::AsyncWriteExt;

struct Lib<'a> {
    url: &'a str,
    path: PathBuf,
}

async fn download_file<T>(url: &str, dest: T) -> Result<PathBuf>
where
    T: Into<PathBuf> + Debug + Clone,
{
    use tokio::fs;
    println!("Attempting download of {:?}", dest);
    let init = PathBuf::from("libs");
    let mut init = init.join(dest.clone().into());
    let final_file = init.clone();
    // go to parent to get dir
    init.pop();
    fs::create_dir_all(init).await?;
    let file = fs::OpenOptions::new()
        .create_new(true)
        .append(true)
        .open(&final_file)
        .await;

    if let Result::Ok(mut file) = file {
        println!("Starting download of file: {:?} from {url}", dest);

        let mut res = reqwest::get(url).await?;
        while let Some(chunk) = res.chunk().await? {
            _ = file.write(&chunk).await?;
        }

        file.flush().await?;
    }

    Ok(final_file)
}

impl<'a> Lib<'a> {
    fn new(url: &'a str, path: impl Into<PathBuf>) -> Lib<'a> {
        Self {
            url,
            path: path.into(),
        }
    }

    async fn spawn_download_proc(self) -> Result<PathBuf> {
        download_file(self.url, self.path).await
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

#[tokio::main]
async fn main() -> Result<()> {
    let mut args = env::args();
    // omit program
    args.next().unwrap();
    let version = args.next().expect("pass version id");

    let mf = reqwest::get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        .await?
        .json::<VersionManifest>()
        .await?;
    let versions = mf
        .versions
        .into_iter()
        .map(|v| (v.id, v.url))
        .collect::<HashMap<_, _>>();

    let version_data = reqwest::get(&versions[&version])
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
                artifact.get("path")?.as_str()?,
            )
                .into()
        })
        .map(|(url, path)| Lib::new(url, path))
        .map(Lib::spawn_download_proc)
        .collect();
    let mut paths = futures::future::join_all(urls)
        .await
        .iter()
        .flatten()
        .flat_map(|it| it.to_str())
        .collect::<Vec<_>>()
        .join(":");
    let client_url = version_data
        .get("downloads")
        .and_then(|downloads| downloads.get("client")?.get("url")?.as_str())
        .unwrap();

    let client_mappins = version_data
        .get("downloads")
        .and_then(|downloads| downloads.get("client_mappings")?.get("url")?.as_str())
        .unwrap();

    _ = tokio::join!(
        download_file(client_url, format!("{version}.jar")),
        download_file(client_mappins, format!("{version}_mappings.jar"))
    );

    paths.push_str(&format!(":libs/{version}.jar"));
    let run_cmd = [
        "java -cp ",
        &paths,
        "net.minecraft.client.main.Main",
        "--version JoeMamaCraft",
        "--accessToken 69420",
    ]
    .join(" ");
    tokio::fs::write(format!("start-{version}.sh"), run_cmd).await?;

    Ok(())
}
