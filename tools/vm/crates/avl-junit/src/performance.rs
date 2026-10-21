//! The IJ Perf file of a scenario, `metrics.performance.json`: the metrics of its `testFinished` in the format that the
//! IJ Perf dashboard collects from the artifacts of a TeamCity build.
//!
//! The lane document names the dashboard series of each scenario that reports metrics. A [`Plan`] holds the build of
//! the IDE and the series of the lane. The controller and `lane-run` both make the plan before a run, and both write
//! one file for each case that reported a metric and has a series, under the artifact path of the series
//! ([`File::path_below`]). The controller publishes the plan before an iteration of the lane daemon, and its results
//! write the files when the report of the iteration is published. `lane-run` writes the files after its run. On
//! TeamCity each of them publishes the file there. A case with metrics and no series, such as a warm-up, gets no file.
//! Its metrics still reach TeamCity as `testMetadata`.
//!
//! The format is `PerformanceMetricsDto` of `intellij.tools.ide.metrics.collector`, version 1, as the JVM host wrote it.
//! A metric whose name ends with `Ms` is a duration. A metric whose name starts with `growth.` is a ratio, and it is the
//! counter `<name>.x100` of its value times 100. Every other metric is a counter. The format keeps whole numbers, so a
//! value is rounded, and a half goes up.

use std::collections::BTreeMap;
use std::io;
use std::path::{Path, PathBuf};

use avl_wire::daemon::RunEvent;
use avl_wire::program::lane::{PerformanceSeries, parse_lane_document};
use serde::{Deserialize, Serialize};

use crate::cases::{StreamCase, simple_class_name};

#[cfg(test)]
mod tests;

/// The name of the file in the artifact directory of its series, which the dashboard collects.
pub const FILE_NAME: &str = "metrics.performance.json";

/// The `event` of the journal record of a [`Plan`].
pub const PLAN_EVENT: &str = "performancePlan";

/// The file of an IDE distribution that names its build, such as `IU-271.1234`.
pub const BUILD_TXT: &str = "build.txt";

/// What the files of one iteration need beside its run stream: the build of the IDE, without the product code, and
/// the series that the lane document names.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Plan {
    pub ide_build: String,
    pub series: Vec<PerformanceSeries>,
}

impl Plan {
    /// The data of the journal record of the plan, with [`PLAN_EVENT`] as its `event`.
    pub fn record(&self) -> serde_json::Value {
        let mut data = serde_json::json!({ "event": PLAN_EVENT });
        if let (Some(object), Ok(serde_json::Value::Object(fields))) = (data.as_object_mut(), serde_json::to_value(self)) {
            object.extend(fields);
        }
        data
    }

    /// The plan of a journal record, or `None` for a record of another event.
    pub fn read(data: &serde_json::Value) -> Option<Self> {
        if data.get("event")?.as_str()? != PLAN_EVENT {
            return None;
        }
        let mut fields = data.as_object()?.clone();
        fields.remove("event");
        serde_json::from_value(serde_json::Value::Object(fields)).ok()
    }

    /// The series of `case`. A class matches by its simple name, because a record can name the class with its package.
    fn series_of(&self, case: &StreamCase) -> Option<&PerformanceSeries> {
        let class = simple_class_name(&case.class_name);
        self.series
            .iter()
            .find(|series| series.scenario == case.name && simple_class_name(&series.test_class_name) == class)
    }
}

/// The plan of `lane` from `lane_documents`, the lane documents by their file name, and the IDE build `ide_build`. It is
/// `None` for a lane whose document names no series. An error says why the plan cannot be made.
pub fn plan(lane_documents: &BTreeMap<String, Vec<u8>>, lane: &str, ide_build: Result<String, String>) -> Result<Option<Plan>, String> {
    let name = format!("{lane}.json");
    let content = lane_documents
        .get(&name)
        .ok_or_else(|| format!("the lane documents hold no {name}"))?;
    let document = parse_lane_document(content).map_err(|problem| format!("the lane document {name} {problem}"))?;
    if document.performance_series.is_empty() {
        return Ok(None);
    }
    Ok(Some(Plan {
        ide_build: ide_build?,
        series: document.performance_series,
    }))
}

/// The build of the IDE from `text`, the read of the [`BUILD_TXT`] at `path`: the build without the product code, or
/// why it cannot be read. An empty build is refused.
pub fn ide_build(path: &Path, text: io::Result<String>) -> Result<String, String> {
    let text = text.map_err(|error| format!("cannot read {}: {error}", path.display()))?;
    match build_without_product_code(&text) {
        build if build.is_empty() => Err(format!("{} is empty", path.display())),
        build => Ok(build),
    }
}

/// One file to write: its directory below the artifacts of the build, and its text.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct File {
    pub artifact_path: String,
    pub text: String,
}

impl File {
    /// The path of the file below `directory`: `<directory>/<artifactPath>/metrics.performance.json`.
    pub fn path_below(&self, directory: &Path) -> PathBuf {
        directory.join(&self.artifact_path).join(FILE_NAME)
    }
}

/// The files of the iteration that streamed `events`, written at `generated`, an RFC 1123 time ([`rfc_1123`]): one for
/// each case that reported a metric and has a series in `plan`.
pub fn files(plan: &Plan, events: &[RunEvent], generated: &str) -> Vec<File> {
    crate::cases::cases(events)
        .into_iter()
        .filter(|case| !case.report.metrics.is_empty())
        .filter_map(|case| {
            plan.series_of(&case).map(|series| File {
                artifact_path: series.artifact_path.clone(),
                text: render(series, &plan.ide_build, generated, &case.report.metrics),
            })
        })
        .collect()
}

/// The text of the file of `series` with `metrics`, as pretty JSON with a final line end.
pub fn render(series: &PerformanceSeries, ide_build: &str, generated: &str, metrics: &BTreeMap<String, serde_json::Number>) -> String {
    let document = Document {
        version: "1",
        generated,
        project: &series.project,
        project_url: "",
        project_description: format!("Air performance test: {}", series.project),
        build: ide_build,
        build_date: generated,
        method_name: &series.method,
        mode: "",
        owner: "",
        metrics: metrics.iter().map(|(name, value)| metric(name, value)).collect(),
    };
    let mut text = serde_json::to_string_pretty(&document).unwrap_or_default();
    text.push('\n');
    text
}

/// The build of `build_txt`, the `build.txt` of an IDE distribution, without its product code: `IU-271.1234` is
/// `271.1234`.
pub fn build_without_product_code(build_txt: &str) -> String {
    let build = build_txt.trim();
    match build.split_once('-') {
        Some((code, rest)) if !code.is_empty() && code.bytes().all(|byte| byte.is_ascii_uppercase()) => rest.to_owned(),
        _ => build.to_owned(),
    }
}

/// `time` in the RFC 1123 form of `DateTimeFormatter.RFC_1123_DATE_TIME`, such as `Mon, 5 Oct 2026 15:49:56 +0200`. A
/// zero offset is `GMT`.
pub fn rfc_1123(time: &jiff::Zoned) -> String {
    let stamp = time.strftime("%a, %-d %b %Y %H:%M:%S").to_string();
    if time.offset().seconds() == 0 {
        format!("{stamp} GMT")
    } else {
        format!("{stamp} {}", time.strftime("%z"))
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Document<'a> {
    version: &'static str,
    generated: &'a str,
    project: &'a str,
    #[serde(rename = "projectURL")]
    project_url: &'static str,
    project_description: String,
    build: &'a str,
    build_date: &'a str,
    method_name: &'a str,
    mode: &'static str,
    owner: &'static str,
    metrics: Vec<Metric>,
}

/// One metric: `d` for a duration or `c` for a counter, and `v`, the same value.
#[derive(Debug, PartialEq, Eq, Serialize)]
struct Metric {
    n: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    d: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    c: Option<i32>,
    v: i32,
}

fn metric(name: &str, value: &serde_json::Number) -> Metric {
    let value = value.as_f64().unwrap_or_default();
    if name.ends_with("Ms") {
        let duration = whole(value);
        Metric {
            n: name.to_owned(),
            d: Some(duration),
            c: None,
            v: duration,
        }
    } else if name.starts_with("growth.") {
        counter(format!("{name}.x100"), whole(value * 100.0))
    } else {
        counter(name.to_owned(), whole(value))
    }
}

const fn counter(name: String, count: i32) -> Metric {
    Metric {
        n: name,
        d: None,
        c: Some(count),
        v: count,
    }
}

/// `value` rounded to a whole number, a half up, and held to the range of `i32`, as Kotlin `roundToInt` does.
fn whole(value: f64) -> i32 {
    let floor = value.floor();
    let rounded = if value - floor >= 0.5 { floor + 1.0 } else { floor };
    #[expect(
        clippy::cast_possible_truncation,
        reason = "the value is held to the range of i32 first, and the cast saturates a NaN to zero"
    )]
    let whole = rounded.clamp(f64::from(i32::MIN), f64::from(i32::MAX)) as i32;
    whole
}
