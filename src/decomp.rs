use crate::bootstrap::BootstrapResult;
use crate::download_file;
use crate::run_cmd;
use anyhow::{Ok, Result};
use std::fs;
use std::path::Path;
use std::path::PathBuf;

const RECONSTRUCTOR_URL: &str =
    "https://github.com/LXGaming/Reconstruct/releases/download/v1.3.25/reconstruct-cli-1.3.25.jar";
const CFR_URL: &str = "https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar";

#[allow(dead_code)]
pub struct DecompResult {
    mapped_jar: PathBuf,
    sources: Option<PathBuf>,
}

pub async fn decomp(
    version: &str,
    output: &Path,
    bsp_res: &BootstrapResult,
    skip_decomp: bool,
) -> Result<DecompResult> {
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

    // TODO: Check if decompilation/deobfuscation has already happened
    let sources = if !skip_decomp {
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
        Some(dest_dir.into())
    } else {
        None
    };

    println!("[STEP] Finished decompiling the game");
    Ok(DecompResult {
        mapped_jar: source_jar.into(),
        sources,
    })
}
