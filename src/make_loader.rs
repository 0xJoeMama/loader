use std::path::Path;

use anyhow::{Ok, Result};
use tokio::fs;

const LOADER_SCRIPT_TEMPLATE: &str = include_str!("../loader.sh.template");

pub async fn make_loader(version: &str, lib_dir: &Path, cp: &[String]) -> Result<()> {
    println!("[STEP] --make-loader flag was used; Making loader script...");
    let script = LOADER_SCRIPT_TEMPLATE
        .replace("${version}", version)
        .replace("${lib_dir}", &lib_dir.to_string_lossy())
        .replace("${cp}", &cp.join(":"));
    fs::write("loader.sh", script).await?;
    println!("[STEP] Loader script has been written");
    Ok(())
}
