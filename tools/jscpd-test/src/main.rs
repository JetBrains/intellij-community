// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The runner of `jscpd_test` (`defs.bzl`): a Bazel test that fails on a copy-paste clone.
//!
//! The test has two cases, and it writes both to the JUnit report at `XML_OUTPUT_FILE`:
//!
//! - The clone case runs the pinned jscpd with the configuration of the target. It fails when jscpd finds a clone,
//!   and it prints each clone as `<path>:<start>-<end> ~ <path>:<start>-<end>`, relative to the root.
//! - The marker case checks the clone markers of the scanned files, see the `markers` module.
//!
//! The root is the directory of the repository that holds the configuration. A hermetic target scans the root in its
//! runfiles. The runner then writes a copy of the configuration with absolute `path` entries, because jscpd can
//! resolve a relative entry against the real directory of the linked file. jscpd skips a link unless it gets
//! `--follow-symlinks`. A target with `working_copy` scans the checkout: the runner resolves the link of the
//! configuration file in the runfiles to the checkout, and it runs jscpd there with the file as it is.

mod clones;
mod config;
mod markers;

use crate::clones::Clone;
use crate::config::{Config, Listing};
use runfiles::Runfiles;
use std::error::Error;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::process::{self, Command, Stdio};
use std::{env, fmt::Write as _};

/// The runfiles path of the jscpd binary.
const JSCPD_ENV_VAR: &str = "JSCPD_TEST_JSCPD";

/// The runfiles path of the configuration file.
const CONFIG_ENV_VAR: &str = "JSCPD_TEST_CONFIG";

/// The path of the configuration file inside its repository.
const CONFIG_PATH_ENV_VAR: &str = "JSCPD_TEST_CONFIG_PATH";

/// Set when the test scans the checkout instead of the runfiles.
const WORKING_COPY_ENV_VAR: &str = "JSCPD_TEST_WORKING_COPY";

/// The class name and the two case names of the report.
const CLASS_ENV_VAR: &str = "JSCPD_TEST_CLASS";
const CLONE_CASE_ENV_VAR: &str = "JSCPD_TEST_CLONE_CASE";
const MARKER_CASE_ENV_VAR: &str = "JSCPD_TEST_MARKER_CASE";

/// One sentence that a failure prints, such as the guide to removing a clone.
const GUIDE_ENV_VAR: &str = "JSCPD_TEST_GUIDE";

/// Where Bazel wants the report, and the private directory of a test.
const REPORT_ENV_VAR: &str = "XML_OUTPUT_FILE";
const TMPDIR_ENV_VAR: &str = "TEST_TMPDIR";

/// The name of the report that `--reporters json` writes into `--output`.
const JSON_REPORT: &str = "jscpd-report.json";

/// The outcome of one case: `None` when it passed, or the reason of its failure.
struct Case {
    name: String,
    failure: Option<String>,
}

fn main() {
    match run() {
        Ok(passed) => process::exit(i32::from(!passed)),
        Err(error) => {
            eprintln!("jscpd-test: {error}");
            process::exit(1);
        }
    }
}

/// Runs both cases, prints their failures, writes the report, and answers whether both passed.
fn run() -> Result<bool, Box<dyn Error>> {
    let config_path = env_var(CONFIG_PATH_ENV_VAR)?;
    let working_copy = env::var_os(WORKING_COPY_ENV_VAR).is_some();
    let runfiles = Runfiles::create()?;
    let jscpd = resolve(&runfiles, &env_var(JSCPD_ENV_VAR)?)?;
    let mut config_file = resolve(&runfiles, &env_var(CONFIG_ENV_VAR)?)?;
    if working_copy {
        config_file = fscopy::resolve_links(&config_file)?;
    }
    let root = root_of(&config_file, &config_path)?;
    let config = Config::read(&config_file)?;
    let work = PathBuf::from(env::var_os(TMPDIR_ENV_VAR).unwrap_or_else(|| env::temp_dir().into_os_string()));
    let guide = env::var(GUIDE_ENV_VAR).unwrap_or_default();

    let scanned_config = if working_copy {
        config_file.clone()
    } else {
        let copy = work.join("jscpd.json");
        fs::write(&copy, config.with_absolute_paths(&config_file)?)?;
        copy
    };
    let clone_case = Case {
        name: env_var(CLONE_CASE_ENV_VAR)?,
        failure: clone_failure(&jscpd, &scanned_config, &root, &work, !working_copy, &guide),
    };
    let marker_case = Case {
        name: env_var(MARKER_CASE_ENV_VAR)?,
        failure: marker_failure(
            &config,
            &config_file,
            &root,
            if working_copy { Listing::Git } else { Listing::Runfiles },
        ),
    };

    let cases = [clone_case, marker_case];
    for case in &cases {
        if let Some(failure) = &case.failure {
            println!("{}: {failure}\n", case.name);
        }
    }
    if let Some(path) = env::var_os(REPORT_ENV_VAR) {
        fs::write(path, junit_report(&env_var(CLASS_ENV_VAR)?, &cases))?;
    }
    Ok(cases.iter().all(|case| case.failure.is_none()))
}

/// The failure of the clone case: the run of jscpd failed, or it found clones.
fn clone_failure(jscpd: &Path, config: &Path, root: &Path, work: &Path, follow_links: bool, guide: &str) -> Option<String> {
    match find_clones(jscpd, config, root, work, follow_links) {
        Ok(clones) if clones.is_empty() => None,
        Ok(clones) => {
            let mut message = format!("jscpd found {} copy-paste clones. Remove each one by an extraction.", clones.len());
            if !guide.is_empty() {
                message.push(' ');
                message.push_str(guide);
            }
            for clone in &clones {
                let _ = write!(message, "\n{clone}");
            }
            Some(message)
        }
        Err(error) => Some(error.to_string()),
    }
}

/// Runs jscpd from `root` and reads the clones of its JSON report.
fn find_clones(jscpd: &Path, config: &Path, root: &Path, work: &Path, follow_links: bool) -> Result<Vec<Clone>, Box<dyn Error>> {
    let output = work.join("jscpd");
    fs::create_dir_all(&output)?;
    let mut command = Command::new(jscpd);
    command
        .arg("-c")
        .arg(config)
        .args(["--absolute", "--reporters", "json", "--fail-on-empty", "--no-colors", "--output"])
        .arg(&output)
        .current_dir(root)
        .stdin(Stdio::null());
    if follow_links {
        command.arg("--follow-symlinks");
    }
    let ran = command.output()?;
    let report = output.join(JSON_REPORT);
    if !ran.status.success() || !report.exists() {
        return Err(io::Error::other(format!(
            "jscpd failed with {}:\n{}{}",
            ran.status,
            String::from_utf8_lossy(&ran.stdout),
            String::from_utf8_lossy(&ran.stderr)
        ))
        .into());
    }
    let mut roots = vec![root.to_path_buf()];
    if let Ok(real) = fscopy::resolve_links(root) {
        roots.push(real);
    }
    Ok(clones::read(&fs::read_to_string(report)?, &roots)?)
}

/// The failure of the marker case: a scanned file holds a clone marker that is not valid.
fn marker_failure(config: &Config, config_file: &Path, root: &Path, listing: Listing) -> Option<String> {
    let files = match config.files(config_file, listing) {
        Ok(files) => files,
        Err(error) => return Some(error.to_string()),
    };
    let mut violations = Vec::new();
    for file in files {
        let Ok(text) = fs::read_to_string(&file) else {
            continue;
        };
        let name = file.strip_prefix(root).unwrap_or(&file).display().to_string();
        violations.extend(markers::check(&text).into_iter().map(|violation| format!("{name}:{violation}")));
    }
    if violations.is_empty() {
        None
    } else {
        Some(format!(
            "{} clone markers are not valid:\n{}",
            violations.len(),
            violations.join("\n")
        ))
    }
}

/// The root of the repository that holds the configuration file: its resolved path without its path inside the
/// repository.
fn root_of(config_file: &Path, config_path: &str) -> io::Result<PathBuf> {
    let mut root = config_file;
    for _ in Path::new(config_path).components() {
        root = root
            .parent()
            .ok_or_else(|| io::Error::other(format!("{} does not end with {config_path}", config_file.display())))?;
    }
    if root.join(config_path) == config_file {
        Ok(root.to_path_buf())
    } else {
        Err(io::Error::other(format!(
            "{} does not end with {config_path}",
            config_file.display()
        )))
    }
}

fn resolve(runfiles: &Runfiles, rlocation_path: &str) -> io::Result<PathBuf> {
    runfiles
        .rlocation(rlocation_path)
        .ok_or_else(|| io::Error::other(format!("cannot resolve the runfile {rlocation_path}")))
}

fn env_var(name: &str) -> io::Result<String> {
    env::var(name).map_err(|error| io::Error::other(format!("the rule sets {name}: {error}")))
}

/// The JUnit report of the cases, all under one class.
fn junit_report(class: &str, cases: &[Case]) -> String {
    let failures = cases.iter().filter(|case| case.failure.is_some()).count();
    let mut xml = String::from("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    let _ = writeln!(xml, "<testsuites tests=\"{}\" failures=\"{failures}\">", cases.len());
    let _ = writeln!(
        xml,
        "  <testsuite name=\"{}\" tests=\"{}\" failures=\"{failures}\">",
        xml_text(class),
        cases.len()
    );
    for case in cases {
        let head = format!("    <testcase classname=\"{}\" name=\"{}\"", xml_text(class), xml_text(&case.name));
        match &case.failure {
            None => {
                let _ = writeln!(xml, "{head}/>");
            }
            Some(failure) => {
                let first = failure.lines().next().unwrap_or_default();
                let _ = writeln!(
                    xml,
                    "{head}>\n      <failure message=\"{}\">{}</failure>\n    </testcase>",
                    xml_text(first),
                    xml_text(failure)
                );
            }
        }
    }
    xml.push_str("  </testsuite>\n</testsuites>\n");
    xml
}

/// Text that XML 1.0 can hold between double quotes and in an element: the five markup characters as entities, and
/// no control character other than a tab or a line end.
fn xml_text(text: &str) -> String {
    text.chars()
        .filter(|&character| matches!(character, '\t' | '\n' | '\r') || !character.is_control())
        .fold(String::with_capacity(text.len()), |mut out, character| {
            match character {
                '&' => out.push_str("&amp;"),
                '<' => out.push_str("&lt;"),
                '>' => out.push_str("&gt;"),
                '"' => out.push_str("&quot;"),
                '\'' => out.push_str("&apos;"),
                _ => out.push(character),
            }
            out
        })
}

#[cfg(test)]
#[path = "main_test.rs"]
mod test;
