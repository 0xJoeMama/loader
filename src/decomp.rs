use crate::download_file;
use crate::run_cmd;
use anyhow::{Ok, Result};
use std::fs;
use std::path::Path;

const ENIGMA_URL: &str =
    "https://maven.quiltmc.org/repository/release/org/quiltmc/enigma-cli/2.2.1/enigma-cli-2.2.1-all.jar";

pub async fn decomp(version: &str, output: &Path) -> Result<()> {
    let output = output.to_string_lossy();
    download_file(ENIGMA_URL, format!("{output}/enigma.jar")).await?;

    let source_jar = format!("{output}/{version}-sources.jar");
    let dest_dir = format!("{output}/{version}");

    run_cmd(
        "java",
        &[
            "-jar",
            &format!("{output}/enigma.jar"),
            "deobfuscate",
            &format!("{output}/{version}.jar"),
            &source_jar,
            &format!("{output}/{version}.proguard"),
        ],
    )
    .expect("failed to run deobfuscation");

    fs::create_dir_all(&dest_dir).expect("cannot create dir");

    run_cmd(
        "java",
        &[
            "-jar",
            &format!("{output}/enigma.jar"),
            "decompile",
            "cfr",
            &source_jar,
            &dest_dir,
        ],
    )
    .expect("failed to decompile sources");

    Ok(())
}
