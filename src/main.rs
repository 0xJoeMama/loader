use clap::Parser;
use piston_meta::{bootstrap, decomp};

#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
#[command(propagate_version = true)]
struct Args {
    target_version: String,
}

#[tokio::main]
async fn main() {
    let args = Args::parse();
    bootstrap::bootstrap(&args.target_version)
        .await
        .expect("couldn't bootstrap");
    decomp::decomp(&args.target_version)
        .await
        .expect("couldnt' decompile");
}
