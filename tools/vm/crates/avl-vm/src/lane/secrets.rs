//! The run secrets of `run --test-env NAME=@FILE`: the grammar, the one read of each host file, the guest directory
//! the files go to, and the scan of the artifacts a run fetched back.
//!
//! A run hands its guest test JVM a file, never a variable. The daemon's environment is fixed at boot and is part of
//! the launch digest, so a value there would outlive the run and re-key the daemon. The daemon environment carries
//! only the directory, as [`RUN_SECRETS_VARIABLE`]; a test reads `<directory>/<NAME>`.
//!
//! A value reaches the guest over the exec channel's stdin ([`Guest::write_secret_file`]) and nowhere else: not an
//! argv, not the daemon environment, not a host state file, not a phase line, not a refusal. [`RunSecrets`] prints
//! the names alone, so a `{:?}` of it cannot leak one either.

use std::fmt;
use std::io::Read as _;
use std::path::{Path, PathBuf};
use std::time::Duration;

use avl_base::format::words;
use avl_base::{Exit, Refusal};
use avl_host_sys::SpawnOptions;
use avl_host_sys::guest::{Guest, guest_join};

use avl_base::RefusalExt;

#[cfg(test)]
mod tests;

/// The daemon environment variable that names the guest directory of the run secrets. Set to a path, never to a
/// value, and the same path for every run, so it changes the launch digest once and never again.
pub(crate) const RUN_SECRETS_VARIABLE: &str = "AIR_VM_RUN_SECRETS";

/// The shortest value a run secret may have. The artifact scan looks for the value in text, and a value this short
/// would match ordinary text and refuse every run. A credential is far longer.
pub(crate) const MIN_SECRET_BYTES: usize = 16;

/// The largest file a run secret may be. A login export is a few KiB, and the file is held in memory and written
/// through one exec's stdin.
pub(crate) const MAX_SECRET_BYTES: u64 = 1 << 20;

/// How long a removal of the secret files may take. It runs after an interrupt too, under a context nothing cancels,
/// so it is bounded tightly: one `rm` answers in a second.
const SECRET_REMOVAL_TIMEOUT: Duration = Duration::from_secs(15);

/// The usage refusal of every `--test-env` form but `NAME=@-` and `NAME=@FILE`.
const NOT_A_FILE: &str = "--test-env expects NAME=@- or NAME=@FILE: the daemon's environment is fixed at boot; a run hands the \
                          guest a file, not a variable";

/// The `--test-env` file that stands for the controller's stdin.
pub(crate) const STDIN_SOURCE: &str = "-";

/// Where one run secret is read from: the controller's stdin (`@-`), or a host file (`@FILE`).
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum SecretSource {
    /// Read once, all of it, so a pipe such as `central login export | vm.cmd run ...` leaves no host file behind.
    Stdin,
    File(PathBuf),
}

/// One `--test-env NAME=@-` or `NAME=@FILE` as the command line names it: a variable name and where its value is,
/// and no value.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RunSecretFile {
    pub name: String,
    pub source: SecretSource,
}

/// Parses one `--test-env` value. Only `NAME=@-` and `NAME=@FILE` are accepted; `NAME=VALUE` and a bare `NAME` are
/// refused, because a value on the command line is a value in the shell history and in `ps`. A host file named `-`
/// is `@./-`.
///
/// The name is a shell variable name, because a test reads the file under that name, and the name is a file name in
/// the guest directory as well.
///
/// A refusal, not a `String`: clap quotes the whole value in front of a `String` error, and the value of a mistaken
/// `NAME=VALUE` is the secret itself. A refusal is answered as it is, and none of these repeats what was given,
/// except the name of a `NAME=@` with no file.
pub(crate) fn parse_test_env(value: &str) -> Result<RunSecretFile, Refusal> {
    let Some((name, file)) = value.split_once('=') else {
        return Err(Refusal::usage(format!("{NOT_A_FILE}; a bare word was given")));
    };
    if !is_variable_name(name) {
        return Err(Refusal::usage(
            "--test-env NAME=@FILE: NAME must be a variable name (a letter or '_', then letters, digits or '_')",
        ));
    }
    let Some(path) = file.strip_prefix('@') else {
        // Not even the name: a token with `=` padding looks like `NAME=VALUE`, and its head is part of the secret.
        return Err(Refusal::usage(format!("{NOT_A_FILE}; a value was given")));
    };
    if path.is_empty() {
        return Err(Refusal::usage(format!("--test-env {name}=@ names no file; @- reads stdin")));
    }
    Ok(RunSecretFile {
        name: name.to_owned(),
        source: if path == STDIN_SOURCE {
            SecretSource::Stdin
        } else {
            SecretSource::File(PathBuf::from(path))
        },
    })
}

/// The controller's stdin, as `--test-env NAME=@-` reads it. A seam, so a suite hands a run its bytes and says
/// whether they come from a terminal.
pub(crate) trait SecretStdin: Send + Sync {
    /// Whether stdin is a terminal, which holds no secret a run could read: what a person types there is echoed
    /// and kept in the scrollback.
    fn is_terminal(&self) -> bool;

    /// Up to `limit` bytes of stdin, until its end.
    fn read_to_end(&self, limit: u64) -> std::io::Result<Vec<u8>>;
}

/// The process's own stdin.
pub(crate) struct ProcessStdin;

impl SecretStdin for ProcessStdin {
    fn is_terminal(&self) -> bool {
        std::io::IsTerminal::is_terminal(&std::io::stdin())
    }

    /// A blocking read, once, before any work: the writer of a pipe ends it as soon as it has written the export.
    fn read_to_end(&self, limit: u64) -> std::io::Result<Vec<u8>> {
        let mut value = Vec::new();
        std::io::stdin().lock().take(limit).read_to_end(&mut value)?;
        Ok(value)
    }
}

fn is_variable_name(name: &str) -> bool {
    let mut bytes = name.bytes();
    bytes.next().is_some_and(|first| first.is_ascii_alphabetic() || first == b'_')
        && bytes.all(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
}

/// A stdin a suite scripts: its bytes, whether it is a terminal, and how often it was read.
#[cfg(test)]
#[derive(Default)]
pub(crate) struct ScriptedStdin {
    bytes: std::sync::Mutex<Vec<u8>>,
    terminal: std::sync::atomic::AtomicBool,
    reads: std::sync::atomic::AtomicUsize,
}

#[cfg(test)]
impl ScriptedStdin {
    /// What the pipe holds.
    pub(crate) fn feed(&self, bytes: &[u8]) {
        *avl_base::sync::lock(&self.bytes) = bytes.to_vec();
    }

    pub(crate) fn set_terminal(&self, terminal: bool) {
        self.terminal.store(terminal, std::sync::atomic::Ordering::SeqCst);
    }

    /// How many times the controller read it.
    pub(crate) fn reads(&self) -> usize {
        self.reads.load(std::sync::atomic::Ordering::SeqCst)
    }
}

#[cfg(test)]
impl SecretStdin for ScriptedStdin {
    fn is_terminal(&self) -> bool {
        self.terminal.load(std::sync::atomic::Ordering::SeqCst)
    }

    fn read_to_end(&self, limit: u64) -> std::io::Result<Vec<u8>> {
        self.reads.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
        let bytes = avl_base::sync::lock(&self.bytes);
        Ok(bytes[..bytes.len().min(usize::try_from(limit).unwrap_or(usize::MAX))].to_vec())
    }
}

/// One secret, read.
#[derive(Clone, PartialEq, Eq)]
struct RunSecret {
    name: String,
    value: Vec<u8>,
}

/// The secrets of one run, each host file read exactly once, before any worker is leased.
///
/// `Debug` names the secrets and never prints a value.
#[derive(Clone, Default, PartialEq, Eq)]
pub(crate) struct RunSecrets {
    secrets: Vec<RunSecret>,
}

impl fmt::Debug for RunSecrets {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("RunSecrets").field("names", &self.names()).finish()
    }
}

impl RunSecrets {
    /// Reads every named source once: stdin for `@-`, a host file otherwise. Refused before any lease, and before
    /// anything is read when the command line itself is wrong: a name given twice, `@-` given twice, or `@-` while
    /// stdin is a terminal. Then refused for a source that is missing, not a regular file, unreadable, larger than
    /// [`MAX_SECRET_BYTES`], or shorter than [`MIN_SECRET_BYTES`] once its surrounding whitespace is trimmed. A
    /// refusal names the variable and the source, never what the source holds.
    pub(crate) fn read(files: &[RunSecretFile], stdin: &dyn SecretStdin) -> Result<Self, Refusal> {
        check_sources(files, stdin)?;
        let secrets = files
            .iter()
            .map(|file| {
                let value = match &file.source {
                    SecretSource::Stdin => read_secret_stdin(&file.name, stdin)?,
                    SecretSource::File(path) => read_secret_file(&file.name, path)?,
                };
                Ok(RunSecret {
                    name: file.name.clone(),
                    value,
                })
            })
            .collect::<Result<Vec<_>, Refusal>>()?;
        Ok(Self { secrets })
    }

    pub(crate) const fn is_empty(&self) -> bool {
        self.secrets.is_empty()
    }

    /// The variable names, in the order the command line gave them.
    pub(crate) fn names(&self) -> Vec<&str> {
        self.secrets.iter().map(|secret| secret.name.as_str()).collect()
    }

    /// The name of the first secret whose value, or a part of it, appears anywhere in `text`, or `None`.
    ///
    /// Every needle is found as a substring, inside a longer line too. The needles of one value are, when each is
    /// [`MIN_SECRET_BYTES`] or longer:
    /// - the value trimmed, because a file written by `> file` ends with a newline that a log line does not repeat;
    /// - each line of a multi-line value, trimmed;
    /// - each token of a line: a run of bytes between whitespace, quotes and the punctuation of JSON and of
    ///   `key=value` forms, holding a letter and a digit. A login export is a document, and a log that prints one
    ///   field of it, a token, prints no line of it whole. The letter and the digit keep a long key name or a host
    ///   name of the export from matching ordinary text;
    /// - the JSON-escaped form of the value and of each line, which is how a report or a journal carries them. A
    ///   token holds no byte JSON escapes, so it is its own escaped form.
    ///
    /// This finds a leak that repeats one of these needles. A log that prints only a part of a token, or the value
    /// re-encoded, is not found.
    pub(crate) fn found_in(&self, text: &[u8]) -> Option<&str> {
        self.secrets
            .iter()
            .find(|secret| needles(&secret.value).iter().any(|needle| contains(text, needle)))
            .map(|secret| secret.name.as_str())
    }
}

/// The refusals of the command line itself, before any source is read: a name twice, stdin twice, and stdin on a
/// terminal.
fn check_sources(files: &[RunSecretFile], stdin: &dyn SecretStdin) -> Result<(), Refusal> {
    for (index, file) in files.iter().enumerate() {
        if files[..index].iter().any(|earlier| earlier.name == file.name) {
            return Err(Refusal::new(
                "run_secret_duplicate",
                Exit::USAGE,
                format!("--test-env names {} twice; a run hands the guest one file per name", file.name),
            ));
        }
    }
    let readers: Vec<&str> = files
        .iter()
        .filter(|file| file.source == SecretSource::Stdin)
        .map(|file| file.name.as_str())
        .collect();
    match readers.as_slice() {
        [name] if stdin.is_terminal() => Err(Refusal::usage(format!(
            "--test-env {name}=@- reads the secret from a pipe, and stdin is a terminal; pipe it in, as \
             `<command> | vm.cmd run ... --test-env {name}=@-`, or name a file with --test-env {name}=@FILE"
        ))),
        [] | [_] => Ok(()),
        several => Err(Refusal::usage(format!(
            "--test-env takes @- once, and {} each name it: stdin holds one secret; name a file with NAME=@FILE for \
             the others",
            several.join(" and ")
        ))),
    }
}

fn read_secret_stdin(name: &str, stdin: &dyn SecretStdin) -> Result<Vec<u8>, Refusal> {
    let unusable = |why: String| Refusal::new("run_secret_unreadable", Exit::NO_INPUT, format!("--test-env {name}=@-: {why}"));
    let value = stdin
        .read_to_end(MAX_SECRET_BYTES + 1)
        .map_err(|error| unusable(format!("cannot read stdin: {error}")))?;
    check_secret_size(&value, &unusable)?;
    Ok(value)
}

/// The size bounds every source shares: at most [`MAX_SECRET_BYTES`], and at least [`MIN_SECRET_BYTES`] besides
/// the surrounding whitespace.
fn check_secret_size(value: &[u8], unusable: &dyn Fn(String) -> Refusal) -> Result<(), Refusal> {
    if u64::try_from(value.len()).unwrap_or(u64::MAX) > MAX_SECRET_BYTES {
        return Err(unusable(format!("it is larger than {MAX_SECRET_BYTES} bytes")));
    }
    if value.trim_ascii().len() < MIN_SECRET_BYTES {
        return Err(unusable(format!(
            "it holds fewer than {MIN_SECRET_BYTES} bytes besides whitespace, too few to scan the artifacts for"
        )));
    }
    Ok(())
}

fn read_secret_file(name: &str, path: &Path) -> Result<Vec<u8>, Refusal> {
    let unusable = |why: String| {
        Refusal::new(
            "run_secret_unreadable",
            Exit::NO_INPUT,
            format!("--test-env {name}=@{}: {why}", path.display()),
        )
    };
    // Asked before the open: opening a FIFO for reading would wait for a writer.
    let metadata = std::fs::metadata(path).map_err(|error| unusable(format!("cannot read it: {error}")))?;
    if !metadata.is_file() {
        return Err(unusable("it is not a regular file".to_owned()));
    }
    if metadata.len() > MAX_SECRET_BYTES {
        return Err(unusable(format!("it is larger than {MAX_SECRET_BYTES} bytes")));
    }
    let mut value = Vec::new();
    std::fs::File::open(path)
        .and_then(|opened| opened.take(MAX_SECRET_BYTES + 1).read_to_end(&mut value))
        .map_err(|error| unusable(format!("cannot read it: {error}")))?;
    check_secret_size(&value, &unusable)?;
    Ok(value)
}

/// What [`RunSecrets::found_in`] looks for in one value.
fn needles(value: &[u8]) -> Vec<Vec<u8>> {
    let mut found: Vec<Vec<u8>> = Vec::new();
    let mut add = |needle: &[u8]| {
        if needle.len() >= MIN_SECRET_BYTES && !found.iter().any(|seen| seen == needle) {
            found.push(needle.to_vec());
        }
        if let Ok(text) = std::str::from_utf8(needle)
            && let Ok(quoted) = serde_json::to_string(text)
        {
            let escaped = quoted.as_bytes()[1..quoted.len() - 1].to_vec();
            if escaped.len() >= MIN_SECRET_BYTES && !found.contains(&escaped) {
                found.push(escaped);
            }
        }
    };
    let trimmed = value.trim_ascii();
    add(trimmed);
    for line in trimmed.split(|byte| *byte == b'\n') {
        let line = line.trim_ascii();
        add(line);
        for token in line.split(|byte| is_token_separator(*byte)) {
            if token.iter().any(u8::is_ascii_alphabetic) && token.iter().any(u8::is_ascii_digit) {
                add(token);
            }
        }
    }
    found
}

/// What ends a token of a secret's line: whitespace, the quotes and the punctuation of JSON, and the `=`, `;` and
/// `&` of `key=value` forms. The bytes of base64, base64url, hex, a JWT and a UUID are never one: letters, digits,
/// `-`, `_`, `.`, `+` and `/`. A trailing base64 `=` is cut, which shortens the needle and still finds the token.
const fn is_token_separator(byte: u8) -> bool {
    byte.is_ascii_whitespace()
        || matches!(
            byte,
            b'"' | b'\'' | b'`' | b'\\' | b',' | b':' | b';' | b'=' | b'&' | b'?' | b'{' | b'}' | b'[' | b']' | b'(' | b')' | b'<' | b'>'
        )
}

/// Whether `needle` occurs in `haystack`. Naive, because a scan reads a few files of a few MiB once per run.
fn contains(haystack: &[u8], needle: &[u8]) -> bool {
    !needle.is_empty() && haystack.windows(needle.len()).any(|window| window == needle)
}

/// The guest file of one secret.
pub(crate) fn secret_path(guest: &Guest<'_>, name: &str) -> String {
    guest_join(&guest.settings.vm_run_secrets, name)
}

/// Puts the run's secrets in the guest: the directory first, 0700 and the worker user's, then one 0600 file per
/// name through [`Guest::write_secret_file`]. A directory that already exists is made 0700 again, because a mode an
/// earlier hand left there would otherwise stand.
pub(crate) async fn stage_run_secrets(guest: &Guest<'_>, secrets: &RunSecrets) -> Result<(), Refusal> {
    let directory = &guest.settings.vm_run_secrets;
    guest
        .as_user(
            &words([
                "/bin/sh",
                "-c",
                "umask 077 && /bin/mkdir -p \"$1\" && /bin/chmod 700 \"$1\"",
                "sh",
                directory,
            ]),
            &SpawnOptions::within(avl_host_sys::guest::GUEST_COMMAND_TIMEOUT),
        )
        .await?;
    for secret in &secrets.secrets {
        guest.write_secret_file(&secret_path(guest, &secret.name), &secret.value).await?;
    }
    Ok(())
}

/// Removes the files of this run's secrets, and leaves the directory. Runs under a context nothing cancels, and as
/// a child the interrupt service does not signal ([`SpawnOptions::survives_interrupt`]), so an interrupt that ended
/// the run still removes them: a child spawned after the first signal is otherwise signalled at once.
pub(crate) async fn remove_run_secrets(guest: &Guest<'_>, secrets: &RunSecrets) -> Result<(), Refusal> {
    let background = avl_host_sys::Ctx::background();
    let guest = Guest {
        ctx: &background,
        ..*guest
    };
    let mut argv = words(["/bin/rm", "-f", "--"]);
    argv.extend(secrets.secrets.iter().map(|secret| secret_path(&guest, &secret.name)));
    guest
        .as_user(&argv, &SpawnOptions::within(SECRET_REMOVAL_TIMEOUT).surviving_interrupt())
        .await
        .map(drop)
}

/// Removes the whole secrets directory: what `daemon stop` and a lease release do, so no file of an earlier run that
/// a controller crash left behind survives the worker's next holder.
///
/// Only the directory the controller derives for the guest is removed ([`avl_base::Config::removable_run_secrets_dir`]):
/// any other path is refused before a guest command runs.
pub(crate) async fn clear_run_secrets(guest: &Guest<'_>) -> Result<(), Refusal> {
    let directory = guest.settings.removable_run_secrets_dir()?;
    guest
        .as_user(
            &words(["/bin/rm", "-rf", "--", directory]),
            &SpawnOptions::within(SECRET_REMOVAL_TIMEOUT),
        )
        .await
        .map(drop)
}

/// One fetched artifact that holds a secret.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct SecretHit {
    pub name: String,
    pub path: PathBuf,
}

/// Scans the fetched text artifacts for every secret, and answers each file that holds one. A file that cannot be
/// read is skipped: what was never fetched cannot be published.
pub(crate) fn scan_artifacts(secrets: &RunSecrets, paths: &[PathBuf]) -> Vec<SecretHit> {
    if secrets.is_empty() {
        return Vec::new();
    }
    paths
        .iter()
        .filter_map(|path| {
            let text = std::fs::read(path).ok()?;
            secrets.found_in(&text).map(|name| SecretHit {
                name: name.to_owned(),
                path: path.clone(),
            })
        })
        .collect()
}

/// Moves each file that holds a secret into `withheld`, a 0700 directory, and answers the refusal of the run.
///
/// Moved and not deleted, so the owner can read what leaked and fix it; moved out of the report tree, so a publish
/// rule over that tree never ships it. The refusal names the file and the variable, never the value.
pub(crate) fn withhold(hits: &[SecretHit], withheld: &Path) -> Refusal {
    let created = avl_base::fs::create_private_dir(withheld);
    let mut lines = Vec::with_capacity(hits.len());
    for hit in hits {
        let file_name = hit
            .path
            .file_name()
            .map_or_else(|| "artifact".into(), |name| name.to_string_lossy());
        let destination = withheld.join(format!("{}-{file_name}", avl_base::new_id()));
        let moved = match &created {
            Ok(()) => std::fs::rename(&hit.path, &destination).map_err(|error| error.to_string()),
            Err(refusal) => Err(refusal.message.clone()),
        };
        lines.push(match moved {
            Ok(()) => format!("{} held {}; moved to {}", hit.path.display(), hit.name, destination.display()),
            Err(error) => format!("{} held {}; it could not be moved aside: {error}", hit.path.display(), hit.name),
        });
    }
    Refusal::new(
        "secret_in_artifact",
        Exit::FAILURE,
        format!(
            "a run secret reached a fetched artifact, so nothing of this run may be published:\n{}",
            lines.join("\n")
        ),
    )
    .with_details(serde_json::json!({
        "artifacts": hits.iter().map(|hit| serde_json::json!({
            "name": hit.name,
            "path": hit.path.to_string_lossy(),
        })).collect::<Vec<_>>(),
    }))
}
