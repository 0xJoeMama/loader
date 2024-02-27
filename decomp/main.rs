use std::{env, fs};

use anyhow::{Ok, Result};
use piston_meta::run_cmd;
const ENIGMA_URL: &str =
    "https://maven.quiltmc.org/repository/release/org/quiltmc/enigma-cli/2.2.1/enigma-cli-2.2.1-all.jar";

#[tokio::main]
async fn main() -> Result<()> {
    let mut args = env::args();

    // omit program
    args.next().unwrap();
    let version = args.next().expect("please provide a version to decompile");

    piston_meta::download_file(ENIGMA_URL, "enigma.jar").await?;

    let source_jar = format!("libs/{version}-sources.jar");
    let dest_dir = format!("libs/{version}");

    run_cmd(
        "java",
        &[
            "-jar",
            "libs/enigma.jar",
            "deobfuscate",
            &format!("libs/{version}.jar"),
            &source_jar,
            &format!("libs/{version}.proguard"),
        ],
    )
    .expect("failed to run deobfuscation");

    fs::create_dir_all(&dest_dir).expect("cannot create dir");

    run_cmd(
        "java",
        &[
            "-jar",
            "libs/enigma.jar",
            "decompile",
            "vineflower",
            &source_jar,
            &dest_dir
        ],
    )
    .expect("failed to decompile sources");

    Ok(())
}
