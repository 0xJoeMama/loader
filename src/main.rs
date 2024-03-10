use std::path::PathBuf;

use clap::Parser;
use loader_make::{
    assets::{self, AssetResult},
    bootstrap::{self, BootstrapResult},
    classpath, decomp, loader_deps, make_loader, VersionManifest,
};
use tokio::fs;

#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
#[command(propagate_version = true)]
struct Args {
    target_version: String,
    #[arg(short = 'n', long)]
    skip_decomp: bool,
    #[arg(short, long)]
    output_path: Option<PathBuf>,
    #[arg(long = "make-loader")]
    make_loader: bool,
}

#[tokio::main]
async fn main() {
    let Args {
        target_version,
        skip_decomp,
        output_path,
        make_loader,
    } = Args::parse();
    let output_path = output_path.unwrap_or_else(|| "libs".into());
    fs::create_dir_all(&output_path)
        .await
        .expect("couldn't create output directory");

    let output_path = fs::canonicalize(output_path)
        .await
        .expect("couldn't get absolute path");

    println!("[STEP] Getting version manifest");
    let mf = VersionManifest::fetch()
        .await
        .expect("couldn't get version manifest");
    println!("[STEP] Getting version metadata for version {target_version}");
    let version = mf
        .get_version(&target_version)
        .await
        .expect("unknown version");

    let bsp_res: BootstrapResult = bootstrap::bootstrap(&version, &output_path)
        .await
        .expect("couldn't bootstrap");

    let asset_res: AssetResult = assets::assets(&version, &output_path)
        .await
        .expect("couldn't fetch assets");

    let _decomp_res = decomp::decomp(&version.id, &output_path, &bsp_res, skip_decomp)
        .await
        .expect("couldnt' decompile");

    let ld = loader_deps::loader_deps(&output_path)
        .await
        .expect("couldnt get loader dependencies");

    let cp = classpath::classpath(&version, &output_path, &bsp_res, &ld)
        .await
        .expect("couldn't write classpath");

    if make_loader {
        make_loader::make_loader(&version.id, &output_path, &asset_res, &cp)
            .await
            .expect("couldn't create loader script");
    }
}
