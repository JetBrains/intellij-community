//! The command line: clap parses it, and the help is the curated text agents already know.

use std::ffi::OsString;

use bt_core::selector::is_target_pattern;
use bt_core::{AREAS_FILE, Areas, Refusal, exit, fail_usage};
use clap::Parser;
use clap::error::ErrorKind;

/// The help text, kept from the tool's first version so a caller reading either sees one tool. `{lanes}` is where
/// [`usage`] lists the lanes of the areas.
const USAGE: &str = r#"Agent-friendly wrapper over `bazel test`.

Usage:
  bt <selector> [options] [-- <raw bazel args>...]
  bt --lane <name> [options] [-- <raw bazel args>...]
  bt --module <jps module> [options] [-- <raw bazel args>...]
  bt build <pattern>... [options] [-- <raw bazel args>...]   (see bt build --help)

A green run in a linked work tree, such as a lease, records the tree that it saw, for
`bd lease pick`: one line in bd-checks.jsonl of the git directory. A run during which the files
changed records nothing. A raw ./bazel.cmd run records nothing either.

Selectors (at most one):
  ClassName                  bare simple name; resolves the label and the FQN
  ClassName#method           bare simple name plus a single test method
  a.b.c.ClassName[#method]   fully-qualified name; resolves the label only
  a.b.c                      all-lowercase dotted package; runs the whole package
  plugins/air/backend/vcs    directory; every test target under it, as //<dir>/..., or as
                             @community//<rest>/... for community/<rest> in the ultimate root
  //pkg:target               explicit label, no resolution (works outside every area); in the
                             ultimate root, //community/<rest> runs as @community//<rest>
  @repo//pkg:target          explicit label of another repository, such as @community, the same way
  //pkg/...                  wildcard pattern; a filter may not be combined with it
  flow-rename-session        flow id; every generated suite that tells or walks the flow, as its lane
                             narrowed to those classes (launches a real IDE on this machine)
  rename-session             generated suite id; that one suite, the same way
  --module <jps module>      the module's jps_test target, from .idea/modules.xml and its BUILD.bazel;
                             refused when the module is neither in
                             community/build/bazel-migrated-test-modules.txt nor inside an area

To validate a change, run --lane fast rather than a directory: a subtree misses the modules that
depend on the one you changed, while the lane reports everything your change does not reach as
cached instead of running it again.

A run covering several targets (a directory or a //pkg/... pattern) gets
--build_tests_only and the integration tag exclusions of the areas, so it cannot accidentally launch a real IDE;
pass "-- --test_tag_filters=..." to override. It also shards each target less, because bazel already
parallelises across them; the digest's cost line reports what actually ran.

{lanes}
A flow or a suite selector runs one lane. When the flow's suites span two lanes it refuses and
asks for --lane, which is then accepted beside the selector.

Options:
  --filter <FQN[#method]|pkg>
                           filter for an explicit label or --module (not valid with a wildcard pattern);
                           an all-lowercase dotted value runs that whole package
  --shards <n>             shard count per target (default 2 for a run covering many targets,
                           6 for a single one; ignored when a filter pins one class, which
                           always runs unsharded)
  --max-failures <n>       failures rendered in full before collapsing to one-liners (default 3)
  --test-env <NAME>        forward a host env var into the test sandbox (repeatable)
  --no-cache               --cache_test_results=no
  --json                   emit one JSON object on stdout and nothing else
  --dry-run                print the resolved label, filter, and argv; run nothing
  --list                   print the resolution candidates for the selector; run nothing
  --verbose                also echo bazel's own captured output
  --host <ssh alias>       run the committed HEAD on that machine through bd-host of Branch Deck: send
                           the missing git objects, check the commit out in a host slot that the
                           machine lends to this run only, run the same command, stream its output,
                           copy back the kept logs of out/bt-runs; Ctrl+C cancels the run there;
                           refused when the checkout has local changes; --prepare installs the agent
                           first; `bd install` puts bd-host into ~/.local/bin
  --host-repo <path>       the main repository on the --host machine (env BT_HOST_REPO, default
                           the one of the host file of --prepare, ~/projects/idea-1)
  --prepare                with --host: make that machine ready, step by step (ssh, the repository,
                           the PATH, the tools, the bd-host agent, the credentials, the remote
                           cache), and say ok, done or missing for each; --dry-run changes nothing.
                           Run it in a terminal: the credential copy asks y/N and refuses without a
                           terminal
  --yes                    with --prepare: copy the missing credentials without the question
  --help

Examples:
  bt AgentThreadCliTest
  bt 'AgentThreadIdentityTest#buildsAndParsesValidIdentity'
  bt com.intellij.air.shared.core
  bt plugins/air/shared/core
  bt flow-new-session --lane ui
  bt --lane fast
  bt --host studio --lane fast
  bt //tests/ideaProjectStructure:projectStructureTests_test \
      --filter com.intellij.ideaProjectStructure.fast.KotlinFacetsConfigurationTest
  bt --module intellij.platform.util.tests --filter com.intellij.openapi.util.io.FileUtilLightTest

Exit codes: 0 green, 2 usage, 3 tests failed, 4 zero tests executed,
            5 build failure before tests, 6 infrastructure."#;

/// The help text of `bt build`.
const BUILD_USAGE: &str = r"Agent-friendly wrapper over `bazel build`.

Usage:
  bt build <pattern>... [options] [-- <raw bazel args>...]

Builds the target patterns with -k and the UI of bazel silenced, and answers one digest: BUILD OK,
or BUILD FAILED with the compile errors. A green build in a linked work tree, such as a lease,
records the tree that it saw, for `bd lease pick`: one line in bd-checks.jsonl of the git
directory. A build during which the files changed records nothing. A raw ./bazel.cmd build
records nothing either.

Patterns:
  //pkg:target   //pkg/...   @repo//pkg/...

bt runs every pattern in the checkout root, whatever the working directory. In the ultimate root,
//community/<rest> runs as @community//<rest>, because bazel ignores the community directory there.
A // pattern whose directory is not in the checkout is refused. A green build of bazel that built no
target, or that dropped a pattern under an ignored directory, is BUILD EMPTY and records nothing.

Options:
  --json                   emit one JSON object on stdout and nothing else
  --dry-run                print the argv; run nothing
  --verbose                also echo bazel's own captured output
  --host <ssh alias>       build the committed HEAD on that machine, as bt --host does
  --host-repo <path>       the main repository on the --host machine (env BT_HOST_REPO)
  --help

Examples:
  bt build //plugins/air/...
  bt build //plugins/air/frontend/llm:air-frontend-llm //plugins/air/shared/core/...

Exit codes: 0 green, 2 usage, 4 no target built, 5 build failure, 6 infrastructure.";

/// The verb of `bt build`, the first argument.
pub(crate) const BUILD_VERB: &str = "build";

/// The column a lane's description starts at, and the width it wraps at.
const LANE_COLUMN: usize = 13;
const HELP_WIDTH: usize = 100;

/// The help text, with the lanes of every area.
pub(crate) fn usage(areas: &Areas) -> String {
    USAGE.replace("{lanes}", &lanes_text(areas))
}

/// One block per area: its lanes in declared order, each with its description wrapped under it.
fn lanes_text(areas: &Areas) -> String {
    if areas.is_empty() {
        return format!("Lanes: none, because {AREAS_FILE} names no area.\n");
    }
    let mut blocks = Vec::new();
    for area in areas.iter() {
        let mut text = format!("Lanes of {}:\n", area.dir());
        for lane in area.lanes().iter() {
            let name = format!("  {}", lane.name);
            let words: Vec<&str> = lane.description.as_deref().unwrap_or_default().split_whitespace().collect();
            let mut line = if name.len() < LANE_COLUMN {
                format!("{name:<LANE_COLUMN$}")
            } else {
                text.push_str(&name);
                text.push('\n');
                " ".repeat(LANE_COLUMN)
            };
            let mut empty = true;
            for word in words {
                if !empty && line.len() + 1 + word.len() > HELP_WIDTH {
                    text.push_str(line.trim_end());
                    text.push('\n');
                    line = " ".repeat(LANE_COLUMN);
                    empty = true;
                }
                if !empty {
                    line.push(' ');
                }
                line.push_str(word);
                empty = false;
            }
            text.push_str(line.trim_end());
            text.push('\n');
        }
        blocks.push(text);
    }
    blocks.join("\n")
}

/// One parsed invocation.
///
/// clap enforces what used to be hand-written: `--lane`, `--filter`, `--shards` and `--max-failures` may appear
/// once, `--test-env` repeats, a repeated boolean flag is harmless, and a count is checked before any work starts,
/// so a typo does not surface after a five-minute build. A value that looks like another long flag is a missing
/// value (`--lane --json` is a typo, and adopting `--json` as the lane name would report an unknown lane instead of
/// the real mistake), while a single-dash value is left alone, because a bazel-style `-k` can legitimately be one.
/// Everything after `--` is bazel's, verbatim and unexamined.
#[derive(Clone, Debug, Default, PartialEq, Eq, Parser)]
#[command(name = "bt", disable_version_flag = true, arg_required_else_help = true)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "each bool is an independent command-line switch, as clap derives it"
)]
pub(crate) struct Args {
    /// The one selector.
    pub(crate) selector: Option<String>,
    #[arg(long, allow_hyphen_values = true, value_parser = flag_value)]
    pub(crate) lane: Option<String>,
    /// A JPS module name. It is a selector of its own, so it takes the place of the positional one and of `--lane`.
    #[arg(long, allow_hyphen_values = true, value_parser = flag_value, conflicts_with_all = ["selector", "lane"])]
    pub(crate) module: Option<String>,
    /// `--filter ''` is a caller asking for an empty filter, a different invocation from no `--filter`.
    #[arg(long, allow_hyphen_values = true, value_parser = flag_value)]
    pub(crate) filter: Option<String>,
    /// Defaulted later, from the resolution's breadth and the lane.
    #[arg(long, allow_hyphen_values = true, value_parser = count_parser(50))]
    pub(crate) shards: Option<u32>,
    #[arg(long, default_value_t = 3, allow_hyphen_values = true, value_parser = count_parser(100))]
    pub(crate) max_failures: u32,
    #[arg(long = "test-env", value_name = "NAME", allow_hyphen_values = true, value_parser = flag_value)]
    pub(crate) test_env: Vec<String>,
    #[arg(long, overrides_with = "no_cache")]
    pub(crate) no_cache: bool,
    #[arg(long, overrides_with = "json")]
    pub(crate) json: bool,
    #[arg(long, overrides_with = "dry_run")]
    pub(crate) dry_run: bool,
    #[arg(long, overrides_with = "list")]
    pub(crate) list: bool,
    #[arg(long, overrides_with = "verbose")]
    pub(crate) verbose: bool,
    /// An ssh alias: the run goes to that machine.
    #[arg(long, allow_hyphen_values = true, value_parser = flag_value)]
    pub(crate) host: Option<String>,
    /// The main repository on the `--host` machine. A run without `--host` ignores it, so an exported variable
    /// changes no local run.
    #[arg(long, env = "BT_HOST_REPO", allow_hyphen_values = true, value_parser = flag_value)]
    pub(crate) host_repo: Option<String>,
    /// Makes the `--host` machine ready for runs, and runs nothing.
    #[arg(long, overrides_with = "prepare")]
    pub(crate) prepare: bool,
    /// With `--prepare`: copy the missing credentials without the question.
    #[arg(long, overrides_with = "yes")]
    pub(crate) yes: bool,
    #[arg(last = true)]
    pub(crate) passthrough: Vec<String>,
}

/// One parsed invocation of `bt build`.
#[derive(Clone, Debug, Default, PartialEq, Eq, Parser)]
#[command(name = "bt build", disable_version_flag = true, arg_required_else_help = true)]
pub(crate) struct BuildArgs {
    /// The target patterns, as bazel takes them.
    #[arg(required = true, value_parser = target_pattern)]
    pub(crate) patterns: Vec<String>,
    #[arg(long, overrides_with = "json")]
    pub(crate) json: bool,
    #[arg(long, overrides_with = "dry_run")]
    pub(crate) dry_run: bool,
    #[arg(long, overrides_with = "verbose")]
    pub(crate) verbose: bool,
    /// An ssh alias: the build goes to that machine.
    #[arg(long, allow_hyphen_values = true, value_parser = flag_value)]
    pub(crate) host: Option<String>,
    /// The main repository on the `--host` machine, as for a test run.
    #[arg(long, env = "BT_HOST_REPO", allow_hyphen_values = true, value_parser = flag_value)]
    pub(crate) host_repo: Option<String>,
    #[arg(last = true)]
    pub(crate) passthrough: Vec<String>,
}

/// A target pattern: a label or a pattern from the root of a repository. A relative pattern resolves against the
/// working directory of bazel, which is the checkout root, so it is refused as the likely typo of a selector.
fn target_pattern(value: &str) -> Result<String, String> {
    if is_target_pattern(value) {
        return Ok(value.to_owned());
    }
    Err(format!("a target pattern starts with // or @, and {value} does not"))
}

/// A flag's value, refused when it is another long flag (or the `--` separator) rather than a value.
fn flag_value(value: &str) -> Result<String, String> {
    if value.starts_with("--") {
        return Err(format!("a value is required, and {value} is not one"));
    }
    Ok(value.to_owned())
}

/// A positive count up to `max`, spelled in plain decimal: no sign and no leading zero, so `07` or `+7` is refused
/// as the typo it most likely is.
fn count_parser(max: u32) -> impl Fn(&str) -> Result<u32, String> + Clone + Send + Sync + 'static {
    move |value: &str| {
        let plain = value.starts_with(|first: char| matches!(first, '1'..='9')) && value.bytes().all(|byte| byte.is_ascii_digit());
        if !plain {
            return Err(format!("must be a positive integer, got: {value}"));
        }
        // Parsing fails only on overflow here, and an overflowing count is over the maximum by definition.
        match value.parse::<u32>() {
            Ok(count) if count <= max => Ok(count),
            _ => Err(format!("must be at most {max}")),
        }
    }
}

/// What a command line asks for: a run, or the help text with the status it leaves by.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Invocation {
    Run(Args),
    Build(BuildArgs),
    /// `--help` is green; no arguments at all is a usage failure that still prints the help, because the help is
    /// the answer to it.
    Help {
        text: String,
        exit: u8,
    },
}

/// Parses a command line without the program name. A parse error is a usage refusal carrying clap's message.
pub(crate) fn parse_args<I, T>(argv: I, areas: &Areas) -> Result<Invocation, Refusal>
where
    I: IntoIterator<Item = T>,
    T: Into<OsString> + Clone,
{
    let mut argv = argv.into_iter().map(Into::<OsString>::into).peekable();
    if argv.next_if(|first| first.as_os_str() == BUILD_VERB).is_some() {
        let argv = std::iter::once(OsString::from("bt build")).chain(argv);
        return BuildArgs::try_parse_from(argv).map_or_else(
            |error| help_or_refusal(&error, BUILD_USAGE.to_owned()),
            |args| Ok(Invocation::Build(args)),
        );
    }
    let argv = std::iter::once(OsString::from("bt")).chain(argv);
    Args::try_parse_from(argv).map_or_else(|error| help_or_refusal(&error, usage(areas)), |args| Ok(Invocation::Run(args)))
}

/// A parse error of clap as the help it asked for, or as a usage refusal with its message.
fn help_or_refusal(error: &clap::Error, help: String) -> Result<Invocation, Refusal> {
    match error.kind() {
        ErrorKind::DisplayHelp | ErrorKind::DisplayVersion => Ok(Invocation::Help {
            text: help,
            exit: exit::GREEN,
        }),
        ErrorKind::DisplayHelpOnMissingArgumentOrSubcommand => Ok(Invocation::Help {
            text: help,
            exit: exit::USAGE,
        }),
        _ => Err(fail_usage(usage_message(error))),
    }
}

/// clap's message without its `Usage:` tail, its `--help` hint and its `tip:` lines: the curated help is one flag
/// away, the generated usage line would contradict it, and a tip such as "use '-- -k'" is wrong for bt, where
/// everything after `--` goes to bazel.
fn usage_message(error: &clap::Error) -> String {
    let rendered = error.render().to_string();
    let message = rendered.split("\n\nUsage:").next().unwrap_or(&rendered);
    let message = message
        .lines()
        .filter(|line| {
            let line = line.trim_start();
            !line.starts_with("tip:") && !line.starts_with("For more information, try")
        })
        .collect::<Vec<_>>()
        .join("\n");
    let message = message.trim();
    message.strip_prefix("error: ").unwrap_or(message).to_owned()
}

#[cfg(test)]
mod tests;
