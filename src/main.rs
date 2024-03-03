use std::path::PathBuf;

use clap::Parser;
use loader_make::{bootstrap, decomp, loader_deps, make_loader};

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

    let mut cp = bootstrap::bootstrap(&target_version, &output_path)
        .await
        .expect("couldn't bootstrap");

    if !skip_decomp {
        decomp::decomp(&target_version, &output_path)
            .await
            .expect("couldnt' decompile");
    }

    let mut ld = loader_deps::loader_deps(&output_path)
        .await
        .expect("couldnt get loader dependencies");

    cp.append(&mut ld);

    if make_loader {
        make_loader::make_loader(&target_version, &output_path, &cp)
            .await
            .expect("couldn't create loader script");
    }

    let cp = cp.join(":");
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
