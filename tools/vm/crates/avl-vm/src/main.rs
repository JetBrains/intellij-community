//! The `vm` binary: the process boundary and nothing else.

use std::process::ExitCode;

fn main() -> ExitCode {
    ExitCode::from(avl_vm::run(std::env::args_os().skip(1), std::io::stdout(), std::io::stderr()))
}
