use anyhow::Result;
use std::fmt::Debug;
use std::path::PathBuf;
use std::process::Command;
use tokio::io::AsyncWriteExt;

// TODO: Migrate all dest usages after adding output parameter
pub async fn download_file<T>(url: &str, dest: T) -> Result<PathBuf>
where
    T: Into<PathBuf> + Debug + Clone,
{
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
        println!("[INFO] Starting download of file: {:?} from {url}", dest);

        let mut res = reqwest::get(url).await?;
        while let Some(chunk) = res.chunk().await? {
            _ = file.write(&chunk).await?;
        }

        file.flush().await?;
    }
    println!(
        "[INFO] File {} finished downloading",
        dest.to_string_lossy()
    );

    Ok(dest)
}

pub fn run_cmd(program: &str, args: &[&str]) -> Result<()> {
    let mut cmd = Command::new(program);
    cmd.args(args);

    print!("[CMD] {program}");
    for i in cmd.get_args() {
        print!("{} ", i.to_string_lossy());
    }
    println!();

    _ = cmd.spawn().and_then(|c| c.wait_with_output())?;
    Ok(())
}

pub mod bootstrap;
pub mod decomp;
pub mod loader_deps;
