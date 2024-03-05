use std::path::PathBuf;

use clap::Parser;
use loader_make::{assets, bootstrap, decomp, loader_deps, make_loader, VersionManifest};

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
    println!("[STEP] Getting version manifest");
    let mf = VersionManifest::fetch()
        .await
        .expect("couldn't get version manifest");
    println!("[STEP] Getting version metadata for version {target_version}");
    let version = mf
        .get_version(&target_version)
        .await
        .expect("unknown version");

    let bsp_res = bootstrap::bootstrap(&version, &output_path)
        .await
        .expect("couldn't bootstrap");

    let (asset_id, asset_dir) = assets::assets(&version, &output_path)
        .await
        .expect("couldn't fetch assets");

    if !skip_decomp {
        decomp::decomp(&version.id, &output_path, &bsp_res)
            .await
            .expect("couldnt' decompile");
    }

    let mut ld = loader_deps::loader_deps(&output_path)
        .await
        .expect("couldnt get loader dependencies");

    let mut cp = bsp_res.classpath;
    cp.append(&mut ld);

    if make_loader {
        make_loader::make_loader(&version.id, &output_path, &cp, &asset_dir, asset_id)
            .await
            .expect("couldn't create loader script");
    }

    let cp = cp
        .iter()
        .map(|i| i.to_string_lossy())
        .collect::<Vec<_>>()
        .join(":");

    println!("[STEP] Exporting class path...");
    tokio::fs::write(
        format!(
            "{}/{target_version}.classpath",
            output_path.to_string_lossy()
        ),
        cp,
    )
    .await
    .expect("couldn't write classpath file");
    println!("[STEP] Exporting done");
}
