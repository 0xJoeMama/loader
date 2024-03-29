use std::path::Path;

use anyhow::{Ok, Result};
use tokio::fs;

use crate::assets::AssetResult;

const LOADER_SCRIPT_TEMPLATE: &str = include_str!("../loader.sh.template");

pub async fn make_loader(
    version: &str,
    lib_dir: &Path,
    assets: &AssetResult,
    cp: &str,
) -> Result<()> {
    println!("[STEP] --make-loader flag was used; Making loader script...");

    let script = LOADER_SCRIPT_TEMPLATE
        .replace("${version}", version)
        .replace("${lib_dir}", &lib_dir.to_string_lossy())
        .replace("${cp}", cp)
        .replace("${assets_dir}", &assets.asset_path.to_string_lossy())
        .replace("${assets_id}", &assets.id);

    fs::write("loader.sh", script).await?;

    #[cfg(unix)]
    {
        use fs::File;
        use std::os::unix::fs::PermissionsExt;

        const EXECUTE: u32 = 100 | 10 | 1;

        let f = File::open("loader.sh").await?;
        let mut perms = f.metadata().await?.permissions();
        perms.set_mode(perms.mode() | EXECUTE);
        f.set_permissions(perms).await?;
    }

    println!("[STEP] Loader script has been written");
    Ok(())
}
