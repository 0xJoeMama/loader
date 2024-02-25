use std::{fmt::Debug, fs, path::PathBuf};

use anyhow::{Ok, Result};
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

#[tokio::main]
async fn main() -> Result<()> {
    let version_data = fs::read_to_string("1.20.json")?;

    let json: Value = serde_json::from_str(&version_data)?;
    let urls: Vec<_> = json
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
    let client_url = json
        .get("downloads")
        .and_then(|downloads| downloads.get("client")?.get("url")?.as_str())
        .unwrap();

    let client_mappins = json
        .get("downloads")
        .and_then(|downloads| downloads.get("client_mappings")?.get("url")?.as_str())
        .unwrap();

    _ = tokio::join!(
        download_file(client_url, "client.jar"),
        download_file(client_mappins, "client_mappings.mappings")
    );

    paths.push_str(":libs/client.jar");
    let mut run_cmd = String::new();
    run_cmd.push_str("java -cp \"");
    run_cmd.push_str(&paths);
    run_cmd.push_str("\" net.minecraft.client.main.Main --version JoeMamacraft --accessToken 69420");

    tokio::fs::write("start.sh", run_cmd).await?;

    Ok(())
}
