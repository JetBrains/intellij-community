//! The fake program of a host that runs no shell script. See [`avl_testkit::fakebin`].

use std::io::{self, Write as _};

use avl_testkit::fakebin::{self, Reply};

fn main() {
    let program = std::env::current_exe().expect("the fake has a path");
    let args: Vec<String> = std::env::args().skip(1).collect();
    let mut stdout = io::stdout().lock();
    let code = match fakebin::serve(&program, &args, &mut stdout, &mut io::stderr()) {
        Ok(Reply::Exit(code)) => code,
        Ok(Reply::Relay(port)) => fakebin::relay(port, io::stdin(), &mut stdout),
        Err(error) => {
            eprintln!("avl-fake: {error}");
            1
        }
    };
    let _ = stdout.flush();
    // An exit and not a return: the relay leaves its stdin copy blocked on a thread.
    std::process::exit(code);
}
