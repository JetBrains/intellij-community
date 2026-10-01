//! The areas `bt` resolves in: each is a directory and its lane table, and `bt.json` at the repository root lists
//! them.
//!
//! An area is how a product tree joins `bt` without this crate naming any of its paths. A name selector scans every
//! area's directory, `--lane` looks the lane up in every area's table, and a flow or a suite selector reads the one
//! area whose table names a suite catalog.
//!
//! `bt.json` is a flat object with one key:
//!
//! ```json
//! { "areas": [ { "dir": "plugins/air", "lanes": "plugins/air/tests/integration/lanes.json" } ] }
//! ```
//!
//! - `dir`: the repo-relative directory the area owns, which resolution scans.
//! - `lanes`: the repo-relative path of the area's lane table; [`Lanes`] gives its shape.
//!
//! A checkout without `bt.json` has no area. A label and a pattern still resolve there, since they name their
//! targets themselves.

use refusal::Refusal;
use serde::Deserialize;

use crate::exit::{fail_infra, fail_usage};
use crate::lanes::{Catalog, LaneSpec, Lanes};
use crate::runtime::{Runtime, repo_file};

/// The file that lists the areas, relative to the repository root.
pub const AREAS_FILE: &str = "bt.json";

/// One area: the directory it owns and its lane table.
#[derive(Debug)]
pub struct Area {
    dir: String,
    lanes_file: String,
    lanes: Lanes,
}

impl Area {
    /// An area over an already parsed lane table, for a caller that does not read `bt.json`.
    pub fn new(dir: impl Into<String>, lanes_file: impl Into<String>, lanes: Lanes) -> Self {
        Self {
            dir: dir.into(),
            lanes_file: lanes_file.into(),
            lanes,
        }
    }

    /// The repo-relative directory the area owns.
    pub fn dir(&self) -> &str {
        &self.dir
    }

    /// The repo-relative path of the lane table.
    pub fn lanes_file(&self) -> &str {
        &self.lanes_file
    }

    pub const fn lanes(&self) -> &Lanes {
        &self.lanes
    }

    /// The suite catalog the lane table names, or the refusal for an area that names none.
    pub fn catalog(&self) -> Result<Catalog<'_>, Refusal> {
        self.lanes.catalog().ok_or_else(|| {
            fail_usage(format!(
                "the lane table {} of area {} names no suite catalog",
                self.lanes_file, self.dir
            ))
        })
    }

    /// Whether a repo-relative path is the area's directory or a path under it.
    pub fn owns(&self, path: &str) -> bool {
        path == self.dir || path.strip_prefix(&self.dir).is_some_and(|rest| rest.starts_with('/'))
    }
}

/// Every area of the checkout, in the order `bt.json` lists them.
#[derive(Debug, Default)]
pub struct Areas {
    areas: Vec<Area>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct AreasFile {
    areas: Vec<AreaEntry>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct AreaEntry {
    dir: String,
    lanes: String,
}

impl Areas {
    pub const fn new(areas: Vec<Area>) -> Self {
        Self { areas }
    }

    /// Reads `bt.json` and every lane table it names. A checkout without the file has no area; a file that is there
    /// and cannot be read or parsed is refused, and so is a lane table that cannot.
    pub fn load(runtime: &dyn Runtime) -> Result<Self, Refusal> {
        let file = repo_file(runtime, AREAS_FILE);
        if !runtime.exists(&file) {
            return Ok(Self::default());
        }
        let text = runtime
            .read_text_file(&file)
            .map_err(|error| fail_infra(format!("{AREAS_FILE} is unreadable: {error}")))?;
        let held: AreasFile = serde_json::from_str(&text).map_err(|error| fail_infra(format!("{AREAS_FILE} is malformed: {error}")))?;
        let mut areas = Vec::with_capacity(held.areas.len());
        for entry in held.areas {
            let dir = entry.dir.trim_end_matches('/').to_owned();
            if dir.is_empty() || dir.starts_with('/') {
                return Err(fail_infra(format!(
                    "{AREAS_FILE} names the area directory `{}`; a directory is repo-relative and not the root",
                    entry.dir
                )));
            }
            let lanes = Lanes::load(runtime, &entry.lanes)?;
            areas.push(Area::new(dir, entry.lanes, lanes));
        }
        Ok(Self { areas })
    }

    pub fn iter(&self) -> impl Iterator<Item = &Area> {
        self.areas.iter()
    }

    pub const fn is_empty(&self) -> bool {
        self.areas.is_empty()
    }

    /// Every area directory, in listed order.
    pub fn dirs(&self) -> Vec<&str> {
        self.areas.iter().map(Area::dir).collect()
    }

    /// The area that owns a repo-relative path. A nested area wins over the area around it.
    pub fn by_dir(&self, path: &str) -> Option<&Area> {
        self.areas.iter().filter(|area| area.owns(path)).max_by_key(|area| area.dir.len())
    }

    /// The lane of that name and its area, or `None` for a name no area declares.
    ///
    /// Two areas that declare one name are refused rather than chosen between: which of the two runs would depend
    /// on the order of `bt.json`, and the caller could not tell from the command which one it got.
    pub fn lane(&self, name: &str) -> Result<Option<(&Area, &LaneSpec)>, Refusal> {
        let found: Vec<(&Area, &LaneSpec)> = self.areas.iter().filter_map(|area| Some((area, area.lanes.get(name)?))).collect();
        match found.as_slice() {
            [] => Ok(None),
            [only] => Ok(Some(*only)),
            _ => Err(fail_infra(format!(
                "lane {name} is declared by the areas {}; rename it in one of their lane tables",
                found.iter().map(|(area, _)| area.dir.as_str()).collect::<Vec<_>>().join(", ")
            ))),
        }
    }

    /// Every lane name of every area, in listed order.
    pub fn lane_names(&self) -> Vec<&str> {
        self.areas.iter().flat_map(|area| area.lanes.names()).collect()
    }

    /// The one area whose lane table names a suite catalog, which a flow or a suite selector reads.
    pub fn with_catalog(&self) -> Result<&Area, Refusal> {
        let found: Vec<&Area> = self.areas.iter().filter(|area| area.lanes.catalog().is_some()).collect();
        match found.as_slice() {
            [only] => Ok(only),
            [] => Err(fail_usage(format!(
                "no area of {AREAS_FILE} names a suite catalog, so a flow or a suite selector names nothing here"
            ))),
            _ => Err(fail_infra(format!(
                "the areas {} each name a suite catalog; {AREAS_FILE} allows one",
                found.iter().map(|area| area.dir.as_str()).collect::<Vec<_>>().join(", ")
            ))),
        }
    }
}

#[cfg(test)]
mod tests;
