use crate::{bootstrap::BootstrapResult, loader_deps::LoaderDepsResult, VersionMeta};
use anyhow::{Ok, Result};
use std::path::Path;

pub async fn classpath<'b, 'a>(
    version: &'b VersionMeta,
    output_path: &'b Path,
    bsp: &'a BootstrapResult,
    ld: &'a LoaderDepsResult,
) -> Result<String> {
    println!("[STEP] Exporting class path...");

    let cp: Vec<&Path> = bsp
        .classpath
        .iter()
        .chain(ld.iter())
        .map(|it| it.as_path())
        .collect();

    let cp_loc = format!("{}/{}.classpath", output_path.to_string_lossy(), version.id);

    let mut cp_str = cp
        .iter()
        .map(|it| it.to_string_lossy())
        .fold(String::new(), |mut acc, it| {
            acc.push_str(it.as_ref());
            acc.push(':');
            acc
        });
    cp_str.pop();

    tokio::fs::write(&cp_loc, &cp_str)
        .await
        .expect("couldn't write classpath file");

    println!("[STEP] Exporting done");
    Ok(cp_str)
}
