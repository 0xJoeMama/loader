use anyhow::Result;
use std::fmt::Debug;
use std::path::PathBuf;
use std::process::Command;
use tokio::io::AsyncWriteExt;

pub async fn download_file<T>(url: &str, dest: T) -> Result<PathBuf>
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

pub fn run_cmd(program: &str, args: &[&str]) -> Result<()> {
    let mut cmd = Command::new(program);
    cmd.args(args);

    for i in cmd.get_args() {
        print!("{} ", i.to_string_lossy());
    }
    println!();

    _ = cmd.spawn().and_then(|c| c.wait_with_output())?;
    Ok(())
}

pub mod bootstrap;
pub mod decomp;
