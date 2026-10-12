//! `validate-image`: proving a freshly provisioned macOS image is the image the pipeline pinned, before Packer
//! seals it.
//!
//! Runs inside the guest as a `shell` provisioner of `air-macos.pkr.hcl`: after `provision-image`, after
//! sanitizing, immediately before the seal. Everything asserted here was asserted by the deleted
//! `validate-guest.sh`, whose failed `[[ ]]` printed nothing at all - so what this verb buys is that every check
//! names what it looked at and what it found.
//!
//! `air-audit-public-image` stays a shell script this verb execs. It is the publishability gate, argued path by
//! path against the image's own filesystem, and moving that argument into another language would move it away
//! from the reviewers who wrote it.
//!
//! **Refusals are classified by code, not by exit status**: Packer's `shell` provisioner treats every nonzero
//! status alike. Messages quote what a command answered, which is the opposite of the rule every other verb
//! follows: here the reader is a human looking at a Packer build log, the audit's own finding is the entire
//! diagnosis of an audit failure, and every quotation is attributed and length-capped.

use std::fs;
use std::io::{self, Read};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use serde::Serialize;

use super::{
    AUDIT_SCRIPT_NAME, IMAGE_ACCOUNT, IMAGE_HOME, REQUIRED_BASE_EXECUTABLES, SYSTEM_PATH, Stat, WORKER_SCRIPTS, installed_worker_script,
    is_executable, is_node_major, local_bin_directory, node_bin_directory, resolve,
};
use crate::cli::ImagePins;
use crate::reply::AgentRefusalExt;
use crate::reply::{AgentRefusal, quoted};

#[cfg(test)]
mod tests;

// The system tools, by absolute path: they are macOS's, not the image's own.
const SW_VERS_PATH: &str = "/usr/bin/sw_vers";
const UNAME_PATH: &str = "/usr/bin/uname";
const DEFAULTS_PATH: &str = "/usr/bin/defaults";
const PMSET_PATH: &str = "/usr/bin/pmset";
const SUDO_PATH: &str = "/usr/bin/sudo";
const SYSADMINCTL_PATH: &str = "/usr/sbin/sysadminctl";

// What the finished image is expected to hold.
const TART_GUEST_DAEMON_PLIST: &str = "/Library/LaunchDaemons/org.cirruslabs.tart-guest-daemon.plist";
const TART_GUEST_AGENT_PLIST: &str = "/Library/LaunchAgents/org.cirruslabs.tart-guest-agent.plist";
const LOGIN_WINDOW_DOMAIN: &str = "/Library/Preferences/com.apple.loginwindow";
const KCPASSWORD_PATH: &str = "/etc/kcpassword";

/// Tools whose *presence* is the defect: "It does not install Bazel/Bazelisk, Peekaboo, a checkout, or cache
/// contents" (`community/tools/vm/docs/vm-ui-tests.md`). A worker never builds, so a `bazel` on its PATH is a second toolchain
/// nothing gates; Peekaboo works only behind the TCC grants the pre-seal audit rejects.
const FORBIDDEN_IMAGE_TOOLS: [&str; 3] = ["peekaboo", "bazel", "bazelisk"];

/// Quoted subprocess output is capped: one audit finding is a line, a tool that broke in an interesting way can
/// answer megabytes, and a refusal message is read by a human in a build log.
const MAX_QUOTED_OUTPUT: usize = 4096;

/// Where this verb looks for the image's own tools, and the PATH it runs them with: `provision-image`'s plus the
/// directory the Junie installer wrote into. Declared because Packer's `shell` provisioner runs a non-login zsh,
/// which reads no `.zprofile`, so an inherited PATH would not contain Homebrew.
pub(crate) fn search_path(node_major: u32) -> Vec<String> {
    [node_bin_directory(node_major), local_bin_directory(Path::new(IMAGE_HOME))]
        .into_iter()
        .chain(SYSTEM_PATH.map(str::to_owned))
        .collect()
}

/// What one command answered: stdout and stderr together (`sysadminctl -screenLock status` reports on stderr),
/// and how it failed, if it did.
#[derive(Clone, Debug, Default)]
pub(crate) struct Answer {
    pub output: String,
    pub failure: Option<String>,
}

/// The guest surface this verb touches: the paths it stats and the commands it runs. A test presents an entire
/// macOS image as a temporary directory plus a table of command replies, and breaks exactly one thing per case.
pub(crate) trait ImageSurface {
    fn stat(&self) -> &Stat;
    fn run(&mut self, argv: &[&str]) -> Answer;
}

/// The guest itself.
pub(crate) struct LiveImage {
    search_path: Vec<String>,
    stat: Stat,
}

impl LiveImage {
    pub(crate) fn new(search_path: Vec<String>) -> Self {
        Self {
            search_path,
            stat: Box::new(|path| fs::metadata(path)),
        }
    }
}

impl ImageSurface for LiveImage {
    fn stat(&self) -> &Stat {
        &self.stat
    }

    /// Every command is invoked by absolute path, but a child that shells out inherits the image's own PATH rather
    /// than the provisioner's - per command, because a verb that only reads the image should not rewrite the
    /// environment it is reading.
    fn run(&mut self, argv: &[&str]) -> Answer {
        match combined_output(argv, &self.search_path.join(":")) {
            Ok((output, status)) if status.success() => Answer { output, failure: None },
            Ok((output, status)) => Answer {
                output,
                failure: Some(status.to_string()),
            },
            Err(error) => Answer {
                output: String::new(),
                failure: Some(error.to_string()),
            },
        }
    }
}

/// Runs `argv` with both streams into one pipe, in the order the child wrote them.
fn combined_output(argv: &[&str], path: &str) -> io::Result<(String, std::process::ExitStatus)> {
    let (program, arguments) = argv
        .split_first()
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "an empty command"))?;
    let (mut reader, writer) = io::pipe()?;
    // The command owns both write ends; it is dropped before the read so the read ends when the child exits.
    let mut child = Command::new(program)
        .args(arguments)
        .env("PATH", path)
        .stdin(Stdio::null())
        .stdout(writer.try_clone()?)
        .stderr(writer)
        .spawn()?;
    let mut output = Vec::new();
    let read = reader.read_to_end(&mut output);
    let status = child.wait()?;
    read?;
    Ok((String::from_utf8_lossy(&output).into_owned(), status))
}

/// What a passing validation answers: the evidence, not just the verdict. `community/tools/vm/docs/vm-ui-tests.md` records
/// which agent builds a golden contains from it.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
pub(crate) struct ImageReport {
    #[serde(rename = "macOS")]
    pub macos: String,
    pub architecture: String,
    pub node: String,
    pub junie: String,
    #[serde(rename = "tartGuestAgent")]
    pub tart_guest_agent: String,
    pub audit: String,
}

/// Renders what a command answered: JSON-quoted, so a multi-line answer stays one line of the build log, and
/// capped on a character boundary, so one broken tool cannot bury the message that named it.
pub(crate) fn quote_output(output: &str) -> String {
    if output.is_empty() {
        return "nothing".to_owned();
    }
    if output.len() > MAX_QUOTED_OUTPUT {
        let cut = output.floor_char_boundary(MAX_QUOTED_OUTPUT);
        return quoted(&format!("{}… (truncated)", &output[..cut]));
    }
    quoted(output)
}

// --- the verdicts: what one answer of the guest means, with no I/O ---------------------------------------------

/// The guest's macOS must be the pinned one. A base image that moved invalidates every check after it.
pub(crate) fn macos_verdict(pinned: &str, reported: &str) -> Result<(), AgentRefusal> {
    if reported == pinned {
        return Ok(());
    }
    Err(AgentRefusal::refused(
        "image_macos_version_mismatch",
        format!(
            "this build pins macOS {pinned} and the guest reports {}; the base digest in versions.env and \
             --macos-version no longer describe the same image",
            quoted(reported)
        ),
    ))
}

/// The guest must be Apple silicon: the IDE distribution, its JBR and the agent binaries are darwin-aarch64 builds.
pub(crate) fn architecture_verdict(reported: &str) -> Result<(), AgentRefusal> {
    if reported == "arm64" {
        return Ok(());
    }
    Err(AgentRefusal::refused(
        "image_architecture_mismatch",
        format!("this image is Apple-silicon only and uname -m reports {}", quoted(reported)),
    ))
}

/// The Node of the image must be of the pinned major; the patch inside it is Homebrew's to move.
pub(crate) fn node_verdict(pinned_major: u32, binary: &str, answered: &str) -> Result<(), AgentRefusal> {
    if is_node_major(answered, pinned_major) {
        return Ok(());
    }
    Err(AgentRefusal::refused(
        "image_node_version_mismatch",
        format!("this build pins Node {pinned_major} and {binary} answers {}", quoted(answered)),
    ))
}

/// The agent CLI must be the exact build pinned. A shim that self-updated past its pin runs, and answers a lane's
/// questions with another product's behaviour.
pub(crate) fn junie_verdict(pinned: &str, binary: &str, answered: &str) -> Result<(), AgentRefusal> {
    if contains_version_token(answered, pinned) {
        return Ok(());
    }
    Err(AgentRefusal::refused(
        "image_agent_version_mismatch",
        format!("this build pins junie {pinned} and {binary} answers {}", quote_output(answered)),
    ))
}

/// Autologin must name the image account: a worker that stops at the login window is unreachable after a power
/// cycle.
pub(crate) fn autologin_verdict(user: &str) -> Result<(), AgentRefusal> {
    if user == IMAGE_ACCOUNT {
        return Ok(());
    }
    Err(AgentRefusal::refused(
        "image_autologin_not_configured",
        format!(
            "autologin must name {IMAGE_ACCOUNT} and {LOGIN_WINDOW_DOMAIN} answers {}; a worker that stops at the \
             login window is unreachable after a power cycle",
            quoted(user)
        ),
    ))
}

/// The screen lock must be off. The lock arrives minutes into a run, so it presents as a lane that measured nothing.
pub(crate) fn screen_lock_verdict(answer: &str) -> Result<(), AgentRefusal> {
    if answer.contains("off") {
        return Ok(());
    }
    Err(AgentRefusal::refused(
        "image_screen_lock_enabled",
        format!("the screen lock is not disabled: sysadminctl answers {}", quote_output(answer)),
    ))
}

/// The public-image audit must pass. `failure` is how it failed, when it did, and `output` is what it reported.
pub(crate) fn audit_verdict(failure: Option<&str>, output: &str) -> Result<(), AgentRefusal> {
    let Some(failure) = failure else {
        return Ok(());
    };
    Err(AgentRefusal::refused(
        "image_audit_refused",
        format!(
            "the public-image audit refused this image: {failure}; it reported {}",
            quote_output(output)
        ),
    ))
}

/// Whether `output` names exactly this version, bounded by non-version characters.
///
/// The shell version matched `*"$PIN"*`, which accepts `0.84.10` for a pin of `0.84.1` - the wrong build of the
/// very tool it exists to pin. The substring search itself stays: `junie --version` answers `Junie 26.8.17
/// (2777.8)`, so there is no one format to compare a whole line against.
pub(crate) fn contains_version_token(output: &str, version: &str) -> bool {
    if version.is_empty() {
        return false;
    }
    let is_version_byte = |byte: Option<&u8>| byte.is_some_and(|byte| *byte == b'.' || byte.is_ascii_digit());
    let (bytes, pin) = (output.as_bytes(), version.as_bytes());
    // Every start, overlapping ones included: `1.1` inside `1.1.1.1` is refused at 0 and at 2 alike.
    (0..bytes.len()).any(|start| {
        bytes[start..].starts_with(pin)
            && !is_version_byte(start.checked_sub(1).and_then(|before| bytes.get(before)))
            && !is_version_byte(bytes.get(start + pin.len()))
    })
}

/// The image as this verb reads it.
pub(crate) struct ImageValidator<S> {
    pins: ImagePins,
    search_path: Vec<String>,
    pub surface: S,
}

impl<S: ImageSurface> ImageValidator<S> {
    pub(crate) fn new(pins: &ImagePins, surface: S) -> Self {
        Self {
            pins: pins.clone(),
            search_path: search_path(pins.node_major),
            surface,
        }
    }

    /// Runs one command and refuses when it does not answer, quoting what it said. `what` is how the command reads
    /// to a human, because the argv's absolute paths add nothing to a message already naming the tool.
    fn run_or_refuse(&mut self, what: &str, argv: &[&str]) -> Result<String, AgentRefusal> {
        let answer = self.surface.run(argv);
        let answered = answer.output.trim().to_owned();
        match answer.failure {
            None => Ok(answered),
            Some(failure) => Err(AgentRefusal::refused(
                "image_tool_not_runnable",
                format!(
                    "{what} did not run in the guest: {failure}; it answered {}",
                    quote_output(&answered)
                ),
            )),
        }
    }

    /// Where one of the image's own tools is, or a refusal naming the path searched: "junie is missing" and "junie
    /// is not where this image puts it" are different repairs, and only the search path tells them apart.
    fn require_tool(&self, name: &str, code: &'static str) -> Result<PathBuf, AgentRefusal> {
        resolve(self.surface.stat(), &self.search_path, name).ok_or_else(|| {
            AgentRefusal::refused(
                code,
                format!("{name} is not on this image's search path {}", self.search_path.join(":")),
            )
        })
    }

    fn is_regular(&self, path: &str, non_empty: bool) -> bool {
        (self.surface.stat())(Path::new(path)).is_ok_and(|info| info.is_file() && (!non_empty || info.len() > 0))
    }

    /// Answers what the guest is, or the first way in which it is not what was pinned.
    ///
    /// One linear function in the script's order, cheapest-first: identity, then what is installed, then the
    /// settings, then the audit that sweeps a filesystem. The four base executables are resolved together before
    /// any version is reasoned about, because "the base stopped shipping this" is one answer.
    pub(crate) fn validate(&mut self) -> Result<ImageReport, AgentRefusal> {
        // 1. The OS. A base image that moved invalidates every check after it.
        let macos = self.run_or_refuse("sw_vers -productVersion", &[SW_VERS_PATH, "-productVersion"])?;
        macos_verdict(&self.pins.macos_version, &macos)?;

        // 2. Apple silicon: the IDE distribution, its JBR and the agent binaries are darwin-aarch64 builds.
        let architecture = self.run_or_refuse("uname -m", &[UNAME_PATH, "-m"])?;
        architecture_verdict(&architecture)?;

        // 3. Everything the digest-pinned base must still provide, from the list `provision-image` checks. Resolved
        //    and not run: `tart-guest-agent` is a daemon's binary and starting it is not a version query.
        let mut resolved = Vec::new();
        for tool in REQUIRED_BASE_EXECUTABLES {
            resolved.push(self.require_tool(tool, "image_base_tool_missing")?);
        }
        let [git, node, npm, tart_guest_agent] =
            <[PathBuf; 4]>::try_from(resolved).unwrap_or_else(|_| unreachable!("one path per required executable"));

        // 4. Node, by the pinned major; the patch inside it is Homebrew's to move.
        let node_binary = node.to_string_lossy().into_owned();
        let node_version = self.run_or_refuse("node --version", &[&node_binary, "--version"])?;
        node_verdict(self.pins.node_major, &node_binary, &node_version)?;

        // 5. Git and npm answer, one fact more than resolving them: a Homebrew symlink outlives the keg it points
        //    into, so a pruned keg leaves a name on the search path and an exit 127 behind it.
        for (tool, binary) in [("git", &git), ("npm", &npm)] {
            let binary = binary.to_string_lossy().into_owned();
            self.run_or_refuse(&format!("{tool} --version"), &[&binary, "--version"])?;
        }

        // 6. The guest agent's two property lists. Its binary on disk says nothing about launchd running it, and
        //    the guest agent is the *only* channel the controller has into a worker.
        for (plist, role) in [
            (TART_GUEST_DAEMON_PLIST, "the LaunchDaemon that answers `tart exec`"),
            (TART_GUEST_AGENT_PLIST, "the LaunchAgent that runs inside the logged-in GUI session"),
        ] {
            if !self.is_regular(plist, false) {
                let name = Path::new(plist).file_name().unwrap_or_default().to_string_lossy();
                return Err(AgentRefusal::refused(
                    "image_guest_agent_plist_missing",
                    format!("{role} is not installed at {plist}, so {name} never starts"),
                ));
            }
        }

        // 7. The agent CLI provisioning installs, at the exact build pinned. Its own code, because the repair
        //    differs from a base tool's: the installer did not do what it reported. A shim that self-updated past
        //    its pin runs, and answers a lane's questions with another product's behaviour.
        let junie = self.require_tool("junie", "image_agent_missing")?;
        let junie = junie.to_string_lossy().into_owned();
        let junie_version = self.run_or_refuse("junie --version", &[&junie, "--version"])?;
        junie_verdict(&self.pins.junie_version, &junie, &junie_version)?;

        // 8. Autologin, in both halves: the plist *and* `/etc/kcpassword`; either one alone leaves a guest that
        //    stops at the login window after a power cycle, where no controller can reach it. `/etc/kcpassword`
        //    encodes the intentionally public Cirrus account, not a private credential.
        let auto_login_user = self.run_or_refuse(
            &format!("defaults read {LOGIN_WINDOW_DOMAIN} autoLoginUser"),
            &[DEFAULTS_PATH, "read", LOGIN_WINDOW_DOMAIN, "autoLoginUser"],
        )?;
        autologin_verdict(&auto_login_user)?;
        if !self.is_regular(KCPASSWORD_PATH, true) {
            return Err(AgentRefusal::refused(
                "image_autologin_password_missing",
                format!(
                    "{KCPASSWORD_PATH} is absent or empty, so autologin is configured in the plist alone and will \
                     not complete"
                ),
            ));
        }

        // 9. Screen lock off. The lock arrives minutes into a run, so it presents as a lane that measured nothing.
        let screen_lock = self.run_or_refuse("sysadminctl -screenLock status", &[SYSADMINCTL_PATH, "-screenLock", "status"])?;
        screen_lock_verdict(&screen_lock)?;

        // 10. Sleep and display sleep off, read back rather than assumed from the `pmset -a` provisioning ran.
        self.check_power_settings()?;

        // 11. Nothing the image is defined not to carry.
        for forbidden in FORBIDDEN_IMAGE_TOOLS {
            if let Some(location) = resolve(self.surface.stat(), &self.search_path, forbidden) {
                return Err(AgentRefusal::refused(
                    "image_forbidden_tool_present",
                    format!(
                        "this image must not carry {forbidden} and it is installed at {}",
                        location.display()
                    ),
                ));
            }
        }

        // 12. The scripts provisioning installs, executable: `install -m 0755` makes them so, and a `cp` in its
        //     place would leave one 0644 and fail only on a worker's first boot.
        for script in &WORKER_SCRIPTS {
            let path = installed_worker_script(script.installed);
            if !is_executable(self.surface.stat(), Path::new(&path)) {
                return Err(AgentRefusal::refused(
                    "image_helper_not_executable",
                    format!("{path} is not an executable file, and {}", script.purpose),
                ));
            }
        }

        // 13. The publishability gate. Its two flags are a decision: the image still holds the base's SSH host
        //     keys at this point (`seal-public-image.sh` deletes them last), and the sensitive TCC rows a booted
        //     guest accumulates are audited offline against the sealed clone, where the check is stricter.
        //     Dropping either fails every build; adding a third would weaken the gate quietly.
        let audit_script = installed_worker_script(AUDIT_SCRIPT_NAME);
        let audit = self
            .surface
            .run(&[SUDO_PATH, &audit_script, "--allow-ssh-host-keys", "--allow-sensitive-tcc"]);
        let audit_output = audit.output.trim().to_owned();
        audit_verdict(audit.failure.as_deref(), &audit_output)?;

        Ok(ImageReport {
            macos,
            architecture,
            node: node_version,
            junie: junie_version,
            tart_guest_agent: tart_guest_agent.to_string_lossy().into_owned(),
            audit: audit_output,
        })
    }

    /// Refuses when `pmset -g custom` reports a non-zero sleep or display sleep, or does not report them at all:
    /// the `awk` this replaces only refused a non-zero value, so an output that stopped naming `sleep` would have
    /// passed on every image forever.
    fn check_power_settings(&mut self) -> Result<(), AgentRefusal> {
        let output = self.run_or_refuse("pmset -g custom", &[PMSET_PATH, "-g", "custom"])?;
        let mut seen = [false; 2];
        for line in output.lines() {
            let mut fields = line.split_whitespace();
            let (Some(setting), Some(value)) = (fields.next(), fields.next()) else {
                continue;
            };
            let Some(index) = ["sleep", "displaysleep"].iter().position(|name| *name == setting) else {
                continue;
            };
            seen[index] = true;
            // A value that is not a number is not the zero this image requires, as the `awk` treated it.
            if value.parse::<i64>() != Ok(0) {
                return Err(AgentRefusal::refused(
                    "image_sleep_enabled",
                    format!(
                        "{setting} must be 0 and pmset -g custom reports {}; a worker that dozes mid-lane fails the \
                         run it was in the middle of",
                        quoted(value)
                    ),
                ));
            }
        }
        for (setting, seen) in ["sleep", "displaysleep"].into_iter().zip(seen) {
            if !seen {
                return Err(AgentRefusal::refused(
                    "image_power_settings_unreadable",
                    format!(
                        "pmset -g custom reports no {setting} setting, so this check cannot say whether it is off; \
                         it answered {}",
                        quote_output(&output)
                    ),
                ));
            }
        }
        Ok(())
    }
}
