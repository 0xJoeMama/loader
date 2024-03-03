use std::path::PathBuf;

use anyhow::{Ok, Result};
use tokio::{fs::File, io::AsyncWriteExt};

const LOADER_SCRIPT_TEMPLATE: &str = include_str!("../loader.sh.template");

pub async fn make_loader(version: &str, lib_dir: &PathBuf, cp: &[String]) -> Result<()> {
    println!("[STEP] --make-loader flag was used; Making loader script...");
    let mut ld_script = File::options()
        .write(true)
        .create(true)
        .open("loader.sh")
        .await?;
    let script = LOADER_SCRIPT_TEMPLATE
        .replace("${version}", version)
        .replace("${lib_dir}", &lib_dir.to_string_lossy())
        .replace("${cp}", &cp.join(":"));
    ld_script.write(script.as_bytes()).await?;
    println!("[STEP] Loader script has been written");
    Ok(())
}
