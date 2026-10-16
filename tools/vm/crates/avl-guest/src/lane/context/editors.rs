//! The editor tabs that a lane IDE reopens at its start, and their removal before a recycle on the same context.
//!
//! The platform keeps the open editors of a project in the product workspace file of the project,
//! `<config>/workspace/<project workspace id>.xml`. The component `FileEditorManager` holds the tabs of the main window,
//! and `DockManager` holds the editor windows of the dock. The project's `.idea/workspace.xml` holds the id of that file
//! and no editor tab. A start reopens each tab of these components. An IDE that a kill ended keeps the tabs of its last
//! save there, so a start on the same context reopens the file that the killed scenario showed.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};

#[cfg(test)]
mod tests;

/// The directory below the config directory that holds the product workspace file of each project,
/// `CONFIG_WORKSPACE_DIR` of the project store of the platform.
pub(crate) const PRODUCT_WORKSPACE_DIR: &str = "workspace";

/// The components of a product workspace file from which the platform reopens editors at the start.
pub(crate) const RESTORED_EDITOR_COMPONENTS: [&str; 2] = ["DockManager", "FileEditorManager"];

/// The start of a component element.
const OPEN: &str = "<component";
/// The end of a component element that has content.
const CLOSE: &str = "</component>";

/// What [`clear_restored_editors`] did to one product workspace file.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Cleared {
    /// The file held a component of [`RESTORED_EDITOR_COMPONENTS`], and the file keeps every other component.
    Components(PathBuf),
    /// The file was no text, or it held a component element that does not close, so the file is gone. The platform
    /// would read no state from it either.
    File { file: PathBuf, reason: String },
}

impl Cleared {
    /// The line of the run log.
    pub(crate) fn note(&self) -> String {
        match self {
            Self::Components(file) => format!(
                "removed the restored editor tabs ({}) from {}",
                RESTORED_EDITOR_COMPONENTS.join(", "),
                file.display()
            ),
            Self::File { file, reason } => format!("removed {}, because {reason}", file.display()),
        }
    }
}

/// Removes the restored editor tabs from each product workspace file below `config`, the config directory of a context.
/// Every other component of a file stays, and a file without such a component stays byte-equal. Answers one entry for
/// each file that changed, in path order. A config directory without product workspace files has nothing to clear.
pub(crate) fn clear_restored_editors(config: &Path) -> io::Result<Vec<Cleared>> {
    let directory = config.join(PRODUCT_WORKSPACE_DIR);
    let entries = match fs::read_dir(&directory) {
        Ok(entries) => entries,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(error) => return Err(error),
    };
    let mut files = Vec::new();
    for entry in entries {
        let entry = entry?;
        if entry.file_type()?.is_file() && entry.path().extension().is_some_and(|extension| extension == "xml") {
            files.push(entry.path());
        }
    }
    files.sort();
    let mut cleared = Vec::new();
    for file in files {
        let bytes = fs::read(&file)?;
        let kept = String::from_utf8(bytes)
            .map_err(|error| format!("it is no UTF-8 text: {error}"))
            .and_then(|text| without_components(&text, &RESTORED_EDITOR_COMPONENTS));
        match kept {
            Ok(None) => {}
            Ok(Some(kept)) => {
                fs::write(&file, kept)?;
                cleared.push(Cleared::Components(file));
            }
            Err(reason) => {
                fs::remove_file(&file)?;
                cleared.push(Cleared::File { file, reason });
            }
        }
    }
    Ok(cleared)
}

/// `xml` without each component element `<component name="NAME" ...>` whose name is one of `names`, or `None` when it
/// holds none. Only a component that no other component holds counts. A removed element that has its line alone takes
/// its indentation and its line end along, so the file keeps the layout that the platform writes. The error names a
/// component element that does not close.
pub(crate) fn without_components(xml: &str, names: &[&str]) -> Result<Option<String>, String> {
    let mut kept = String::with_capacity(xml.len());
    let mut copied = 0;
    let mut at = 0;
    while let Some(start) = next_open(xml, at) {
        let tag_end = start_tag_end(xml, start)?;
        let end = element_end(xml, start, tag_end)?;
        if component_name(&xml[start..tag_end]).is_some_and(|name| names.contains(&name)) {
            let line_start = xml[..start].rfind('\n').map_or(0, |newline| newline + 1);
            let alone = xml[line_start..start].bytes().all(|byte| byte == b' ' || byte == b'\t');
            let (cut_start, cut_end) = if alone {
                let rest = &xml[end..];
                let line_end = if rest.starts_with("\r\n") {
                    2
                } else {
                    usize::from(rest.starts_with('\n'))
                };
                (line_start, end + line_end)
            } else {
                (start, end)
            };
            kept.push_str(&xml[copied..cut_start]);
            copied = cut_end;
        }
        at = end;
    }
    if copied == 0 {
        return Ok(None);
    }
    kept.push_str(&xml[copied..]);
    Ok(Some(kept))
}

/// The byte of the next start of a component element at or after `from`. `<componentX` is no component.
fn next_open(xml: &str, from: usize) -> Option<usize> {
    let mut at = from;
    while let Some(offset) = xml[at..].find(OPEN) {
        let start = at + offset;
        match xml[start + OPEN.len()..].bytes().next() {
            Some(b' ' | b'\t' | b'\r' | b'\n' | b'/' | b'>') => return Some(start),
            _ => at = start + OPEN.len(),
        }
    }
    None
}

/// The byte after the `>` of the start tag at `start`.
fn start_tag_end(xml: &str, start: usize) -> Result<usize, String> {
    xml[start..]
        .find('>')
        .map(|offset| start + offset + 1)
        .ok_or_else(|| format!("the component element at byte {start} has no end of its start tag"))
}

/// The byte after the component element whose start tag spans `start..tag_end`: after `/>` for an empty element, and
/// after the `</component>` that closes it otherwise.
fn element_end(xml: &str, start: usize, tag_end: usize) -> Result<usize, String> {
    if xml[..tag_end].ends_with("/>") {
        return Ok(tag_end);
    }
    let mut depth = 1_usize;
    let mut at = tag_end;
    loop {
        let close = xml[at..].find(CLOSE).map(|offset| at + offset);
        match (next_open(xml, at), close) {
            (Some(open), Some(close)) if open < close => {
                let nested_end = start_tag_end(xml, open)?;
                if !xml[..nested_end].ends_with("/>") {
                    depth += 1;
                }
                at = nested_end;
            }
            (_, Some(close)) => {
                depth -= 1;
                at = close + CLOSE.len();
                if depth == 0 {
                    return Ok(at);
                }
            }
            (_, None) => return Err(format!("the component element at byte {start} does not close")),
        }
    }
}

/// The value of the attribute `name` of the start tag `tag`.
fn component_name(tag: &str) -> Option<&str> {
    let value = &tag[tag.find(" name=\"")? + " name=\"".len()..];
    value.get(..value.find('"')?)
}
