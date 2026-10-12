//! Finding a downloaded external file on the host.

use std::path::PathBuf;

use avl_base::config::label_target;
use avl_base::{Exit, Refusal};

use super::agent::{BazelHost, cquery_output_file, last_non_empty_line};
use crate::ctx::Ctx;

#[cfg(test)]
mod tests;

/// The absolute host path of the one file of a target in a downloaded external repository.
///
/// One `cquery` names the file, and one `info output_base` roots it. The `cquery` also fetches the repository when
/// nothing has fetched it yet, because loading the target needs its package. So a caller that must not download on
/// its own path has something else fetch first, and a caller that may download (the Tart backend's first command)
/// gets the fetch here.
///
/// Asked rather than assembled by hand, because the repository directory carries the canonical name Bazel derives
/// from the module graph, which nothing here can spell. What `cquery` prints is `external/<canonical>/<file>`,
/// relative to the output base - and the output base rather than the execution root, because the execution root's
/// copy of that directory is a symlink only the *build phase* creates, and no caller here builds.
///
/// It checks nothing on disk: each caller refuses a missing file with its own code and its own remedy.
pub async fn external_file(ctx: &Ctx, bazel: &dyn BazelHost, label: &str) -> Result<PathBuf, Refusal> {
    let stdout = bazel.query(ctx, "cquery", &cquery_output_file(label)).await?;
    let Some(relative) = last_non_empty_line(&stdout) else {
        return Err(unresolved(label));
    };
    Ok(output_base(ctx, bazel).await?.join(relative))
}

/// [`external_file`] of each label of `labels`, in the order of `labels`, from one `cquery` and one `info
/// output_base`.
///
/// One query names every target, so a caller that needs several pinned tools pays one analysis and not one per tool.
/// `cquery` prints the files in an order of its own, so each line is matched to its label by the repository
/// ([`files_by_label`]).
pub async fn external_files(ctx: &Ctx, bazel: &dyn BazelHost, labels: &[String]) -> Result<Vec<PathBuf>, Refusal> {
    let stdout = bazel.query(ctx, "cquery", &cquery_output_file(&labels.join(" + "))).await?;
    let relative = files_by_label(labels, &stdout)?;
    let base = output_base(ctx, bazel).await?;
    Ok(relative.into_iter().map(|relative| base.join(relative)).collect())
}

/// The output line of each label, in the order of `labels`.
///
/// A line is `external/<canonical>/<file>`. Each pin label names an alias of the controller's workspace, and the alias
/// has the name of the repository that it forwards to. The canonical name of a repository that a repository rule of a
/// module makes ends with `+<name>`, so a line belongs to the label whose target is the last `+` segment of the line's
/// repository. The `~` separator of an older Bazel counts too. Each label must name a repository of its own: two lines
/// of one repository refuse, as a label with no line does.
fn files_by_label<'a>(labels: &[String], stdout: &'a str) -> Result<Vec<&'a str>, Refusal> {
    let lines: Vec<&str> = stdout.lines().map(str::trim).filter(|line| !line.is_empty()).collect();
    labels
        .iter()
        .map(|label| {
            let repository = label_target(label);
            let mut matching = lines.iter().filter(|line| {
                line.strip_prefix("external/")
                    .and_then(|rest| rest.split('/').next())
                    .and_then(|canonical| canonical.rsplit(['+', '~']).next())
                    == Some(repository)
            });
            match (matching.next(), matching.next()) {
                (Some(line), None) => Ok(*line),
                (None, _) => Err(unresolved(label)),
                (Some(_), Some(_)) => Err(Refusal::new(
                    "bazel_external_file_unresolved",
                    Exit::SOFTWARE,
                    format!("bazel cquery reported more than one output file in the repository of {label}"),
                )),
            }
        })
        .collect()
}

/// The output base of the host Bazel, which roots the `external/` path that `cquery` prints.
async fn output_base(ctx: &Ctx, bazel: &dyn BazelHost) -> Result<PathBuf, Refusal> {
    let base = bazel.query(ctx, "info", &["output_base".to_owned()]).await?;
    let base = base.trim();
    if base.is_empty() {
        return Err(Refusal::new(
            "bazel_output_base_unknown",
            Exit::SOFTWARE,
            "bazel info output_base answered nothing",
        ));
    }
    Ok(PathBuf::from(base))
}

fn unresolved(label: &str) -> Refusal {
    Refusal::new(
        "bazel_external_file_unresolved",
        Exit::SOFTWARE,
        format!("bazel cquery did not report an output file for {label}"),
    )
}
