//! `affected.json` of `air-traces.zip`: for each commit of the build, which suites its changed files reach.
//!
//! TeamCity writes the changed files of a build into the file of [CHANGED_FILES]. Each line is
//! `<path>:<change type>:<revision>`. The path is relative to the checkout directory. The change type is `ADDED`,
//! `CHANGED`, `REMOVED`, `NOT_CHANGED` or a `DIRECTORY_` type. The revision is the commit of the change. For a file of
//! the patch of a personal build it is `<personal>`, and the document keeps that word as the revision of the commit.
//!
//! The step groups the files by their revision, in the order of the file. It joins the paths of each commit with
//! [avl_affected::affected_suites] on the checkout of the build. That join owns the rule from a path to a suite. The
//! document keeps its words: the `via` of a reached suite and the `reason` of an unmapped path. The lane of a suite is
//! the catalog lane of its suite document, such as `UI`. The join reads the checkout at the revision of the build, not
//! at the commit, so a file that a later commit removes is unreadable.

use std::fs;
use std::io;
use std::path::Path;

use bt_core::Area;
use bt_core::runtime::Runtime;
use serde::Serialize;

use super::teamcity::{Build, CHANGED_FILES};

/// The entry of `air-traces.zip` that the document is, beside `runs.json`.
pub(crate) const AFFECTED_FILE: &str = "affected.json";
/// The version of the document.
const SCHEMA: u32 = 1;

/// One line of the file of the changed files.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ChangedFile {
    pub path: String,
    /// The change type as TeamCity writes it, such as `CHANGED`.
    pub change: String,
    pub revision: String,
}

/// The document `affected.json`.
#[derive(Debug, PartialEq, Eq, Serialize)]
pub(crate) struct AffectedDocument {
    pub schema: u32,
    /// The revision of the build.
    pub revision: String,
    pub commits: Vec<AffectedCommit>,
}

/// The changed files of one commit, and what they reach.
#[derive(Debug, PartialEq, Eq, Serialize)]
pub(crate) struct AffectedCommit {
    pub revision: String,
    pub paths: Vec<ChangedPath>,
    pub reached: Vec<ReachedSuite>,
    pub unmapped: Vec<UnmappedPath>,
}

#[derive(Debug, PartialEq, Eq, Serialize)]
pub(crate) struct ChangedPath {
    pub path: String,
    pub change: String,
}

/// A suite that the paths of a commit reach.
#[derive(Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ReachedSuite {
    pub suite: String,
    /// The simple name of the test class, as a scenario of `runs.json` names it.
    pub test_class_name: String,
    /// The catalog lane, such as `UI` or `GUI_CHAT`.
    pub lane: String,
    /// Why the join reached the suite, such as `flow:<id>` or `lane-wide:<path>`.
    pub via: Vec<String>,
}

/// A path of a commit that reaches no suite, with the reason code of the join.
#[derive(Debug, PartialEq, Eq, Serialize)]
pub(crate) struct UnmappedPath {
    pub path: String,
    pub reason: String,
    /// The flow ids that the path declares, when the reason is about them.
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub flows: Vec<String>,
    /// The JPS module of the path, when the reason is about it.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub module: Option<String>,
}

/// The document of the build, or `None` when the build has no changed files. An error says why the step writes no
/// document, such as a property that the build does not name or a refusal of the join.
///
/// The checkout is `repo`, else the checkout directory of the build, else `workspace`.
pub(crate) fn of_build(build: &Build, repo: Option<&Path>, workspace: Option<&Path>) -> Result<Option<AffectedDocument>, String> {
    let no_document = "so the archive holds no affected.json";
    let file = build.changed_files.as_deref().ok_or_else(|| {
        format!("the build properties name no {CHANGED_FILES}, which TeamCity leaves out for a build without changes; {no_document}")
    })?;
    let text = match fs::read(file) {
        Ok(content) => String::from_utf8_lossy(&content).into_owned(),
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            return Err(format!(
                "{CHANGED_FILES} {} is not there, as for a build without changes; {no_document}",
                file.display()
            ));
        }
        Err(error) => return Err(format!("cannot read {CHANGED_FILES} {}: {error}; {no_document}", file.display())),
    };
    let files = parse_changed_files(&text).map_err(|reason| format!("{CHANGED_FILES} {}: {reason}; {no_document}", file.display()))?;
    if files.is_empty() {
        return Ok(None);
    }
    let revision = build
        .revision
        .as_deref()
        .ok_or_else(|| format!("the build properties name no build.vcs.number; {no_document}"))?;
    let root = repo.or(build.checkout_dir.as_deref()).or(workspace).ok_or_else(|| {
        format!("no --repo, no teamcity.build.checkoutDir and no BUILD_WORKSPACE_DIRECTORY name the checkout; {no_document}")
    })?;
    avl_affected::bridge::install(root).map_err(|refused| format!("{}: {}; {no_document}", refused.code, refused.message))?;
    let runtime = bt_core::OsRuntime::resolver(root, |_| {});
    affected(&runtime, avl_affected::air_area(), revision, &files).map(Some)
}

/// The changed files of the file that TeamCity writes, in its order. A blank line is skipped. A line that is not
/// `<path>:<change type>:<revision>` refuses the file, with its number.
pub(crate) fn parse_changed_files(text: &str) -> Result<Vec<ChangedFile>, String> {
    let mut files = Vec::new();
    for (index, line) in text.lines().enumerate() {
        if line.trim().is_empty() {
            continue;
        }
        // The change type and the revision hold no colon, so a colon of the path stays in the path.
        let mut parts = line.rsplitn(3, ':');
        let (Some(revision), Some(change), Some(path)) = (parts.next(), parts.next(), parts.next()) else {
            return Err(format!("line {} is not <path>:<change type>:<revision>: {line}", index + 1));
        };
        if path.is_empty() || change.is_empty() || revision.is_empty() {
            return Err(format!("line {} is not <path>:<change type>:<revision>: {line}", index + 1));
        }
        files.push(ChangedFile {
            path: path.to_owned(),
            change: change.to_owned(),
            revision: revision.to_owned(),
        });
    }
    Ok(files)
}

/// The document of the changed files `files` of the build at `revision`: one commit for each revision of the files,
/// in the order that the files first name it. A refusal of the join refuses the document.
pub(crate) fn affected(runtime: &dyn Runtime, area: &Area, revision: &str, files: &[ChangedFile]) -> Result<AffectedDocument, String> {
    let mut commits: Vec<(&str, Vec<&ChangedFile>)> = Vec::new();
    for file in files {
        match commits.iter_mut().find(|(known, _)| *known == file.revision) {
            Some((_, of_commit)) => of_commit.push(file),
            None => commits.push((&file.revision, vec![file])),
        }
    }
    let lanes = area.lanes();
    let mut document = AffectedDocument {
        schema: SCHEMA,
        revision: revision.to_owned(),
        commits: Vec::with_capacity(commits.len()),
    };
    for (commit, of_commit) in commits {
        let paths: Vec<String> = of_commit.iter().map(|file| file.path.clone()).collect();
        let joined = avl_affected::affected_suites(runtime, area, &paths).map_err(|refused| {
            format!(
                "the suite join refused the files of {commit}: {}: {}",
                refused.code, refused.message
            )
        })?;
        document.commits.push(AffectedCommit {
            revision: commit.to_owned(),
            paths: of_commit
                .iter()
                .map(|file| ChangedPath {
                    path: file.path.clone(),
                    change: file.change.clone(),
                })
                .collect(),
            reached: joined
                .suites
                .into_iter()
                .map(|suite| ReachedSuite {
                    lane: lanes
                        .get(&suite.lane)
                        .and_then(|lane| lane.catalog_lane.clone())
                        .unwrap_or(suite.lane),
                    suite: suite.suite,
                    test_class_name: suite.class,
                    via: suite.via,
                })
                .collect(),
            unmapped: joined
                .unmapped
                .into_iter()
                .map(|unmapped| UnmappedPath {
                    path: unmapped.path,
                    reason: unmapped.reason,
                    flows: unmapped.flows,
                    module: unmapped.module,
                })
                .collect(),
        });
    }
    Ok(document)
}

#[cfg(test)]
mod tests;
