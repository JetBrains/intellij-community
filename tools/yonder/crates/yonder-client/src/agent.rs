//! The local build of the agent for `prepare`, when no `--agent` names a binary. In a checkout of the monorepo,
//! `./bazel.cmd build` builds `@community//tools/yonder:yonderd` from the checkout root. Then `./bazel.cmd cquery
//! --output=files` names the output file relative to the execution root, and `./bazel.cmd info execution_root` names
//! that root. So the client does not guess the output path. `bt --host --prepare` builds the same target with the same
//! quiet limit.

use std::path::{Path, PathBuf};
use std::time::Duration;

use crate::process::{Call, Limit, Ran, Stopped};
use crate::{Client, Refusal, code, refused};

/// The Bazel target of the agent, from a community root. An ultimate root names it in the module `@community`.
pub const AGENT_TARGET: &str = "//tools/yonder:yonderd";

/// How long the build of the agent may write nothing before it is stopped. A build that writes progress takes as long
/// as it needs.
pub const AGENT_BUILD_QUIET: Duration = Duration::from_mins(30);

/// The base name of the agent binary.
const AGENT_NAME: &str = "yonderd";

/// How many lines of a failed Bazel call a refusal shows.
const SHOWN_LINES: usize = 20;

/// The label of the agent in the checkout `root`: `@community//tools/yonder:yonderd` in an ultimate root, the plain
/// target in a community root, and `None` outside the monorepo.
pub fn agent_label(root: &Path) -> Option<String> {
    if root.join("community/tools/yonder").is_dir() {
        Some(format!("@community{AGENT_TARGET}"))
    } else if root.join("tools/yonder").is_dir() && root.join("bazel.cmd").is_file() {
        Some(AGENT_TARGET.to_owned())
    } else {
        None
    }
}

impl Client<'_> {
    /// Builds the agent with Bazel in the checkout `root` of the monorepo, and answers the path of its binary.
    pub fn build_agent(&self, root: &Path) -> Result<PathBuf, Refusal> {
        let Some(label) = agent_label(root) else {
            return Err(no_monorepo(&format!("{} is no checkout of the monorepo", root.display())));
        };
        let build = bazel(root, &["build", &label]);
        (self.say)(&format!(
            "> ./bazel.cmd build {label} in {}; its output shows only when it fails",
            root.display()
        ));
        let built = self.child(&build);
        if !built.ok() {
            return Err(bazel_refusal(&format!("./bazel.cmd build {label}"), &built));
        }
        let query = bazel(root, &["cquery", "--output=files", &label]);
        let queried = self.child(&query);
        if !queried.ok() {
            return Err(bazel_refusal(&format!("./bazel.cmd cquery --output=files {label}"), &queried));
        }
        // cquery names each file relative to the execution root. The checkout has no `bazel-out` link, because the
        // monorepo turns the convenience links off.
        let info = bazel(root, &["info", "execution_root"]);
        let answered = self.child(&info);
        let execroot = PathBuf::from(answered.stdout_text().trim());
        if !answered.ok() || !execroot.is_absolute() {
            return Err(bazel_refusal("./bazel.cmd info execution_root", &answered));
        }
        let text = queried.stdout_text();
        text.lines()
            .map(str::trim)
            .filter(|line| Path::new(line).file_name().and_then(|name| name.to_str()) == Some(AGENT_NAME))
            .map(|line| execroot.join(line))
            .find(|path| path.is_file())
            .ok_or_else(|| {
                refused(
                    code::AGENT_FAILED,
                    format!(
                        "`./bazel.cmd cquery --output=files {label}` named no {AGENT_NAME} file: {}",
                        text.trim()
                    ),
                )
            })
    }
}

/// The refusal of `prepare` without `--agent` outside the monorepo.
pub fn no_monorepo(why: &str) -> Refusal {
    refused(
        code::USAGE,
        format!(
            "{why}, so yonder cannot build the agent: run it in a checkout of the monorepo, or name a yonderd binary with `--agent <yonderd>`"
        ),
    )
}

/// A call of the Bazel launcher of the checkout, from its root, under the quiet limit of the agent build.
fn bazel(root: &Path, args: &[&str]) -> Call {
    let wrapper = root.join("bazel.cmd").to_string_lossy().into_owned();
    let (program, head): (&str, &[&str]) = if cfg!(windows) { ("cmd.exe", &["/c"]) } else { ("/bin/sh", &[]) };
    let args: Vec<&str> = head.iter().copied().chain([wrapper.as_str()]).chain(args.iter().copied()).collect();
    Call::new(program, &args, AGENT_BUILD_QUIET)
        .with_limit(Limit::Quiet(AGENT_BUILD_QUIET))
        .in_dir(root.to_path_buf())
}

fn bazel_refusal(shown: &str, ran: &Ran) -> Refusal {
    if ran.stopped == Some(Stopped::Limit) {
        return refused(
            code::AGENT_FAILED,
            format!(
                "`{shown}` wrote nothing for {} min and was stopped",
                AGENT_BUILD_QUIET.as_secs() / 60
            ),
        );
    }
    let lines: Vec<&str> = ran.stderr.lines().filter(|line| !line.trim().is_empty()).collect();
    let tail = lines[lines.len().saturating_sub(SHOWN_LINES)..].join("\n");
    let code = ran.code.map_or_else(|| "no exit code".to_owned(), |code| format!("exit {code}"));
    refused(code::AGENT_FAILED, format!("`{shown}` failed ({code}):\n{tail}"))
}

#[cfg(test)]
mod tests;
