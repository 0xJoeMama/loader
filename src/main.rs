use std::path::PathBuf;

use clap::Parser;
use loader_make::{bootstrap, decomp, loader_deps};

#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
#[command(propagate_version = true)]
struct Args {
    target_version: String,
    #[arg(short = 'n', long)]
    skip_decomp: bool,
    #[arg(short, long)]
    output_path: Option<PathBuf>,
}

#[tokio::main]
async fn main() {
    let Args {
        target_version,
        skip_decomp,
        output_path,
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
    let cp = cp.join(":");

    tokio::fs::write(
        format!(
            "{}/{target_version}.classpath",
            output_path.to_string_lossy()
        ),
        cp,
    )
    .await
    .expect("couldn't write classpath file");
}
