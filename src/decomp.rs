use crate::download_file;
use crate::run_cmd;
use crate::BootstrapResult;
use anyhow::{Ok, Result};
use std::fs;
use std::path::Path;

// const ENIGMA_URL: &str =
//     "https://maven.quiltmc.org/repository/release/org/quiltmc/enigma-cli/2.2.1/enigma-cli-2.2.1-all.jar";
const RECONSTRUCTOR_URL: &str =
    "https://github.com/LXGaming/Reconstruct/releases/download/v1.3.25/reconstruct-cli-1.3.25.jar";
const CFR_URL: &str = "https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar";

pub async fn decomp(version: &str, output: &Path, bsp_res: &BootstrapResult) -> Result<()> {
    println!("[STEP] Decompiling the game...");

    let (recon, cfr) = tokio::join!(
        download_file(RECONSTRUCTOR_URL, output.join("reconstructor.jar")),
        download_file(CFR_URL, output.join("cfr.jar"))
    );
    let recon = recon.unwrap();
    let recon = recon.to_string_lossy();
    let cfr = cfr.unwrap();
    let cfr = cfr.to_string_lossy();

    let output = output.to_string_lossy();
    let source_jar = format!("{output}/{version}-sources.jar");
    let dest_dir = format!("{output}/{version}");

    run_cmd(
        "java",
        &[
            "-jar",
            &recon,
            "--input",
            &bsp_res.version_jar.to_string_lossy(),
            "--mapping",
            &bsp_res.mappings.to_string_lossy(),
            "--output",
            &source_jar,
        ],
    )
    .expect("failed to run deobfuscation");

    fs::create_dir_all(&dest_dir).expect("cannot create dir");

    run_cmd(
        "java",
        &["-jar", &cfr, &source_jar, "--outputpath", &dest_dir],
    )
    .expect("failed to decompile sources");

    println!("[STEP] Finished decompiling the game");
    Ok(())
}
