//! The fake program `avl-fake`: the stand-in for a host tool on a host that runs no shell script.
//!
//! A Windows host cannot run the script of [`crate::tartfake`] or the script of the fake host `git`. So a Windows suite
//! gets this one Rust program, linked under the name of the tool it stands in for. The program reads its own file
//! stem to select the tool, as the script reads its own name. It answers as two tools:
//!
//! - `docker`: the `docker` verbs of the script, and the `exec` and shared verbs that a fake `docker` reaches there.
//!   It reads and writes the same [`Answer`] files and the same call log, so a suite seeds one fake the same way on
//!   every host.
//! - `git`: the fake host `git` of `avl-host-testkit`. It records each argv as one line, and answers the head that
//!   `head.txt` holds.
//!
//! On Unix the scripts stay the fakes. The tests of this module compare this port with the script, call by call, so
//! the two cannot drift apart unnoticed.
//!
//! [`serve`] holds the answers and takes its output streams as arguments, so a test drives it in the test process.
//! The relay needs the stdin of a process, so [`serve`] only selects it, and the program runs [`relay`].

use std::env;
use std::ffi::OsStr;
use std::fs::{self, OpenOptions};
use std::io::{self, Read, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;

use crate::tartfake::{ARG_END, Answer, CALL_END, CALLS, exec_verb_file};

#[cfg(test)]
mod tests;

/// The environment variable that names the built program under Bazel, as a `$(rlocationpath ...)`.
pub const PROGRAM_VARIABLE: &str = "AVL_FAKE";

/// The images a fake `docker` holds, one reference per line.
const IMAGES: &str = "images.txt";
/// How many containers a fake `docker` has created.
const CREATE_COUNT: &str = "create-count.txt";

/// The files of the fake `git`.
const GIT_CALLS: &str = "calls.txt";
const GIT_FAILS: &str = "fails.txt";
const GIT_OUTSIDE: &str = "outside.txt";
const GIT_STATUS: &str = "status.txt";
/// The head the fake `git` answers to a `rev-parse`.
pub const GIT_HEAD: &str = "head.txt";

/// What the program does after [`serve`].
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Reply {
    /// Exits with this code.
    Exit(i32),
    /// Relays its stdin and stdout to `127.0.0.1:<port>`: see [`relay`].
    Relay(u16),
}

/// The built program. Under Bazel, [`PROGRAM_VARIABLE`] names it in the test's runfiles. Under cargo, it is the
/// `avl-fake` binary of the target directory, which `cargo build -p avl-testkit --bin avl-fake` builds.
pub fn program() -> &'static Path {
    static PROGRAM: OnceLock<PathBuf> = OnceLock::new();
    PROGRAM.get_or_init(|| {
        let path = env::var(PROGRAM_VARIABLE).map_or_else(|_| cargo_program(), |rlocationpath| crate::rlocation(&rlocationpath));
        assert!(
            path.is_file(),
            "the fake program {} is not there. Under Bazel the test names it by {PROGRAM_VARIABLE} in its test_env, \
             and under cargo `cargo build -p avl-testkit --bin avl-fake` builds it",
            path.display()
        );
        path
    })
}

/// Where cargo puts the program: a test executable is `target/<profile>/deps/<name>-<hash>`, and a binary of the
/// workspace is `target/<profile>/<name>`.
fn cargo_program() -> PathBuf {
    let test = env::current_exe().expect("the test executable has a path");
    let profile = test
        .parent()
        .and_then(Path::parent)
        .expect("a cargo test executable is two levels under the target directory");
    profile.join(format!("avl-fake{}", env::consts::EXE_SUFFIX))
}

/// Links the program into `directory` as the tool `name`, e.g. `docker.exe`, and returns its path.
///
/// The link is a hard link to the one copy of the program that all test processes share, and the program finds its
/// answers and its tool from the path it was started by.
#[cfg(windows)]
pub fn place(directory: &Path, name: &str) -> PathBuf {
    let executable = directory.join(format!("{name}{}", env::consts::EXE_SUFFIX));
    let shared = crate::shared::program();
    if let Err(error) = crate::shared::place(shared, &executable) {
        panic!("install the fake {} from {}: {error}", executable.display(), shared.display());
    }
    executable
}

/// Answers one call of the tool that `program` names, in the directory of `program`.
///
/// `args` is the argv without the program name. What the tool prints goes to `stdout` and `stderr`.
pub fn serve(program: &Path, args: &[String], stdout: &mut dyn Write, stderr: &mut dyn Write) -> io::Result<Reply> {
    let files = Files {
        directory: program.parent().unwrap_or_else(|| Path::new(".")),
    };
    match program.file_stem().and_then(OsStr::to_str) {
        Some("docker") => docker(&files, args, stdout, stderr),
        Some("git") => git(&files, args, stdout, stderr),
        other => {
            writeln!(stderr, "avl-fake: no fake tool is named {other:?}")?;
            Ok(Reply::Exit(2))
        }
    }
}

/// Copies `input` to `127.0.0.1:<port>` and the socket to `output`, and answers the exit code.
///
/// It ends like the guest relay and the relay of the script: at the end of the socket, and not at the end of
/// `input`. It does not half-close the socket at the end of `input`. The copy of `input` runs on a thread that is
/// left behind, so the caller exits the process after it. A port that refuses the connection exits 70, as the
/// script does.
pub fn relay(port: u16, input: impl Read + Send + 'static, output: &mut dyn Write) -> i32 {
    let Ok(socket) = TcpStream::connect(("127.0.0.1", port)) else {
        return 70;
    };
    let Ok(mut to_socket) = socket.try_clone() else {
        return 70;
    };
    std::thread::spawn(move || {
        let mut input = input;
        let _ = io::copy(&mut input, &mut to_socket);
    });
    let mut from_socket = socket;
    let _ = io::copy(&mut from_socket, output);
    let _ = output.flush();
    0
}

/// The answer files in the directory of the program.
struct Files<'a> {
    directory: &'a Path,
}

impl Files<'_> {
    fn path(&self, name: &str) -> PathBuf {
        self.directory.join(name)
    }

    fn exists(&self, name: &str) -> bool {
        self.path(name).is_file()
    }

    /// The text of a file without its trailing newlines, as a shell `$(cat file)` reads it, or `default` for a file
    /// that is not there.
    fn text_or(&self, name: &str, default: &str) -> String {
        fs::read_to_string(self.path(name)).map_or_else(|_| default.to_owned(), |text| text.trim_end_matches('\n').to_owned())
    }

    /// Copies a file to `out`, as `cat file 2>/dev/null` does: a file that is not there prints nothing.
    fn cat(&self, name: &str, out: &mut dyn Write) -> io::Result<()> {
        match fs::read(self.path(name)) {
            Ok(content) => out.write_all(&content),
            Err(_) => Ok(()),
        }
    }

    /// The exit code a file holds, or `default`.
    fn exit_from(&self, name: &str, default: &str) -> Reply {
        Reply::Exit(exit_code(&self.text_or(name, default)))
    }

    fn write(&self, name: &str, content: &str) -> io::Result<()> {
        fs::write(self.path(name), content)
    }

    fn append(&self, name: &str, content: &str) -> io::Result<()> {
        OpenOptions::new()
            .create(true)
            .append(true)
            .open(self.path(name))?
            .write_all(content.as_bytes())
    }

    fn remove(&self, name: &str) {
        let _ = fs::remove_file(self.path(name));
    }
}

/// The code `exit "$text"` exits with. A shell refuses a word that is not a number, and exits 2.
fn exit_code(text: &str) -> i32 {
    text.parse::<i32>().map_or(2, |code| code & 0xff)
}

/// The argument `$n` of a shell, 1-based, or an empty word.
fn arg(args: &[String], n: usize) -> &str {
    args.get(n - 1).map_or("", String::as_str)
}

/// The fake `docker`: the `docker` branch of the script, then the `exec` arms it shares with the fake `tart`, then the
/// verbs every fake answers.
fn docker(files: &Files<'_>, args: &[String], stdout: &mut dyn Write, stderr: &mut dyn Write) -> io::Result<Reply> {
    // One write of the whole record, so two fakes spawned at once cannot interleave their records.
    let record: String = args
        .iter()
        .map(|arg| format!("{arg}{ARG_END}"))
        .chain(std::iter::once(CALL_END.to_owned()))
        .collect();
    files.append(CALLS, &record)?;
    // The exit a SIGKILL gives on Unix, and nothing printed: the program cannot kill itself by a signal on Windows.
    if files.exists(Answer::KilledVerb.file_name()) && files.text_or(Answer::KilledVerb.file_name(), "") == arg(args, 1) {
        return Ok(Reply::Exit(128 + 9));
    }
    if files.exists(Answer::SilentVerb.file_name()) && files.text_or(Answer::SilentVerb.file_name(), "") == arg(args, 1) {
        return Ok(Reply::Exit(0));
    }

    let code = files.text_or(Answer::Exit.file_name(), "0");
    let succeeds = code == "0";
    let state = Answer::ContainerState.file_name();
    match arg(args, 1) {
        "version" => {
            if files.exists(Answer::DockerVersion.file_name()) {
                files.cat(Answer::DockerVersion.file_name(), stdout)?;
            } else {
                stdout.write_all(b"linux/arm64\n")?;
            }
            return Ok(files.exit_from(Answer::VersionExit.file_name(), "0"));
        }
        "inspect" => {
            if format!("{} {}", arg(args, 2), arg(args, 3)) != "--type container" {
                stderr.write_all(b"fake docker: inspect without --type container\n")?;
                return Ok(Reply::Exit(2));
            }
            if !files.exists(state) {
                writeln!(stderr, "Error: No such container: {}", arg(args, 6))?;
                return Ok(Reply::Exit(1));
            }
            let format = arg(args, 5);
            if format.contains(".Id") {
                files.cat(Answer::ContainerId.file_name(), stdout)?;
            } else if format.contains(".State.Running") {
                let running = files.text_or(state, "").starts_with("running/");
                stdout.write_all(if running { "true\n" } else { "false\n" }.as_bytes())?;
            } else {
                match fs::read(files.path(state)) {
                    Ok(content) => stdout.write_all(&content)?,
                    Err(error) => writeln!(stderr, "fake docker: cannot print the state: {error}")?,
                }
            }
            return Ok(Reply::Exit(0));
        }
        "create" => {
            if succeeds {
                let created = files.text_or(CREATE_COUNT, "0").parse::<u64>().unwrap_or(0) + 1;
                files.write(CREATE_COUNT, &format!("{created}\n"))?;
                files.write(Answer::ContainerId.file_name(), &format!("fake-container-{created}\n"))?;
                files.write(state, "created/0\n")?;
                writeln!(stdout, "fake-container-{created}")?;
            }
            return Ok(Reply::Exit(exit_code(&code)));
        }
        "start" => {
            if succeeds {
                if files.exists(Answer::StartedState.file_name()) {
                    fs::copy(files.path(Answer::StartedState.file_name()), files.path(state))?;
                } else {
                    files.write(state, "running/0\n")?;
                }
            }
            return Ok(Reply::Exit(exit_code(&code)));
        }
        "stop" => {
            if succeeds && files.exists(state) {
                files.write(state, "exited/0\n")?;
            }
            return Ok(Reply::Exit(exit_code(&code)));
        }
        "rm" => {
            if arg(args, 2) != "--force" && files.exists(state) && files.text_or(state, "").starts_with("running/") {
                stderr.write_all(b"Error: cannot remove container: container is running\n")?;
                return Ok(Reply::Exit(1));
            }
            if succeeds {
                files.remove(state);
                files.remove(Answer::ContainerId.file_name());
            }
            return Ok(Reply::Exit(exit_code(&code)));
        }
        "build" | "tag" => {
            if succeeds {
                files.append(IMAGES, &format!("{}\n", arg(args, 3)))?;
            }
            return Ok(Reply::Exit(exit_code(&code)));
        }
        "pull" => {
            let pulled = files.text_or(Answer::PullExit.file_name(), "1");
            if pulled == "0" {
                files.append(IMAGES, &format!("{}\n", arg(args, 2)))?;
            }
            return Ok(Reply::Exit(exit_code(&pulled)));
        }
        "buildx" => return Ok(files.exit_from(Answer::PushExit.file_name(), "0")),
        "image" => {
            let reference = args.last().map_or("", String::as_str);
            let images = files.text_or(IMAGES, "");
            match arg(args, 2) {
                "inspect" => {
                    let present = files.exists(Answer::ImagePresent.file_name())
                        || (files.exists(IMAGES) && images.lines().any(|line| line == reference));
                    if !present {
                        return Ok(Reply::Exit(1));
                    }
                    if args.join(" ").contains("Labels") {
                        files.cat(Answer::ImageRevision.file_name(), stdout)?;
                    }
                    return Ok(Reply::Exit(0));
                }
                "rm" => {
                    if succeeds && files.exists(IMAGES) {
                        let kept: String = images
                            .lines()
                            .filter(|line| *line != reference)
                            .map(|line| format!("{line}\n"))
                            .collect();
                        files.write(IMAGES, &kept)?;
                    }
                    return Ok(Reply::Exit(exit_code(&code)));
                }
                _ => {}
            }
        }
        _ => {}
    }
    match arg(args, 1) {
        "run" => return Ok(files.exit_from(Answer::Exit.file_name(), "0")),
        "exec" => {
            if args.len() == 6 && arg(args, 2) == "-i" && arg(args, 5) == "relay" {
                let port = arg(args, 6);
                if !port.is_empty() && port.bytes().all(|byte| byte.is_ascii_digit()) {
                    // A port out of range is a connect that fails, as the script's `/dev/tcp` fails.
                    return Ok(port.parse().map_or(Reply::Exit(70), Reply::Relay));
                }
            }
            for word in args {
                if word.is_empty() || word.contains('/') {
                    continue;
                }
                let verb = exec_verb_file(word);
                if files.exists(&verb) {
                    files.cat(&verb, stdout)?;
                    return Ok(Reply::Exit(0));
                }
            }
            if args.iter().any(|word| word == "read-file") {
                if files.exists(Answer::ReadFileFails.file_name()) {
                    return Ok(Reply::Exit(1));
                }
                if files.exists(Answer::ReadFile.file_name()) {
                    files.cat(Answer::ReadFile.file_name(), stdout)?;
                    files.cat(Answer::ReadFileReceipt.file_name(), stderr)?;
                    return Ok(Reply::Exit(0));
                }
            }
        }
        _ => {}
    }
    Ok(match arg(args, 1) {
        "--version" => {
            files.cat(Answer::Version.file_name(), stdout)?;
            files.exit_from(Answer::VersionExit.file_name(), "0")
        }
        "list" => {
            let listing = if args.join(" ").contains("--quiet") {
                Answer::ListQuiet
            } else {
                Answer::ListJson
            };
            files.cat(listing.file_name(), stdout)?;
            files.exit_from(Answer::ListExit.file_name(), "0")
        }
        "status" => {
            files.cat(Answer::Status.file_name(), stdout)?;
            files.exit_from(Answer::StatusExit.file_name(), "0")
        }
        "exec" => {
            files.cat(Answer::ExecStdout.file_name(), stdout)?;
            files.cat(Answer::ExecStderr.file_name(), stderr)?;
            files.exit_from(Answer::ExecExit.file_name(), "0")
        }
        _ => files.exit_from(Answer::Exit.file_name(), "0"),
    })
}

/// The fake host `git`. The work-tree probe is a `rev-parse` too, so it is matched first.
fn git(files: &Files<'_>, args: &[String], stdout: &mut dyn Write, stderr: &mut dyn Write) -> io::Result<Reply> {
    let joined = args.join(" ");
    files.append(GIT_CALLS, &format!("{joined}\n"))?;
    if files.exists(GIT_FAILS) {
        stderr.write_all(b"fatal: not a git repository (or any of the parent directories): .git\n")?;
        return Ok(Reply::Exit(128));
    }
    let padded = format!(" {joined} ");
    if padded.contains(" --is-inside-work-tree ") {
        if files.exists(GIT_OUTSIDE) {
            stdout.write_all(b"false\n")?;
            return Ok(Reply::Exit(1));
        }
        stdout.write_all(b"true\n")?;
    } else if padded.contains(" rev-parse ") {
        writeln!(stdout, "{}", files.text_or(GIT_HEAD, ""))?;
    } else if padded.contains(" status ") {
        files.cat(GIT_STATUS, stdout)?;
    }
    Ok(Reply::Exit(0))
}
