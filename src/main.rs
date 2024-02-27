use clap::Parser;
use piston_meta::{bootstrap, decomp, loader_deps};

#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
#[command(propagate_version = true)]
struct Args {
    target_version: String,
    #[arg(short = 'n', long)]
    skip_decomp: bool,
}

#[tokio::main]
async fn main() {
    let Args {
        target_version,
        skip_decomp,
    } = Args::parse();

    let mut cp = bootstrap::bootstrap(&target_version)
        .await
        .expect("couldn't bootstrap");

    if !skip_decomp {
        decomp::decomp(&target_version)
            .await
            .expect("couldnt' decompile");
    }

    let mut ld = loader_deps::loader_deps()
        .await
        .expect("couldnt get loader dependencies");

    cp.append(&mut ld);
    let cp = cp.join(":");

    tokio::fs::write(format!("{target_version}.classpath"), cp)
        .await
        .expect("couldn't write classpath file");
}
