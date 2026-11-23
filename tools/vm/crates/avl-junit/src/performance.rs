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
//! The lane can run a scenario more than once: each pass is a case of its own, and the lane document names one series
//! for each pass, all with the same artifact path. The file of a series holds the median of each metric over its passes
//! ([`median_of_passes`]), so one noisy run does not move the series. Each pass also gets a file of its own values, at
//! `<artifactPath>/pass-<k>/`, whose project is the project of the series with [`PASS_PROJECT_SUFFIX`]. The charts and
//! the detector of IJ Perf read the project of the series, so the values of the passes stay out of them, and the
//! spread of a night stays in IJ Perf.
//!
//! The format is `PerformanceMetricsDto` of `intellij.tools.ide.metrics.collector`, version 1, as the JVM host wrote it.
//! A metric whose name starts with `growth.` is a ratio, and it is the counter `<name>.x100` of its value times 100,
//! also when the name of its step ends with `Ms`. Another metric whose name ends with `Ms` is a duration. Every other
//! metric is a counter. The format keeps whole numbers, so a
//! value is rounded, and a half goes up.

use std::collections::BTreeMap;
use std::io;
use std::path::{Path, PathBuf};

use avl_wire::daemon::RunEvent;
use avl_wire::program::lane::{PerformanceSeries, parse_lane_document};
use serde::{Deserialize, Serialize};

use crate::cases::{CaseOutcome, StreamCase, simple_class_name};

#[cfg(test)]
mod tests;

/// The name of the file in the artifact directory of its series, which the dashboard collects.
pub const FILE_NAME: &str = "metrics.performance.json";

/// The end of the project of the file of one pass. The project of the series comes before it.
pub const PASS_PROJECT_SUFFIX: &str = "/passes";

/// The counter of the file of one pass that names the pass: 1 for the first pass.
pub const PASS_METRIC: &str = "pass";

/// The `event` of the journal record of a [`Plan`].
pub const PLAN_EVENT: &str = "performancePlan";

/// The file of an IDE distribution that names its build, such as `IU-271.1234`.
pub const BUILD_TXT: &str = "build.txt";

/// The directory name of the home of a macOS app, the home of a distribution below its `.dist` directory.
const MACOS_APP_HOME: &str = "Contents";

/// The [`BUILD_TXT`] of the IDE home `home` of the guest `platform`, such as `linux_aarch64`. `home.path` of the IDE
/// config names the home, see [`avl_wire::runtime::dist_home`].
///
/// Production puts the file in `Resources/` of a macOS app home, which is the `Contents` directory. It puts the file at
/// the root of every other home, also of a macOS language server.
pub fn build_txt(home: &Path, platform: &str) -> PathBuf {
    let macos_app = platform.starts_with("darwin_") && home.file_name().is_some_and(|name| name == MACOS_APP_HOME);
    if macos_app {
        home.join("Resources").join(BUILD_TXT)
    } else {
        home.join(BUILD_TXT)
    }
}

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
/// each series in `plan` that a case reported a metric for, with the median of its passes, and for a series of more
/// than one pass, one file for each pass that reported a metric.
pub fn files(plan: &Plan, events: &[RunEvent], generated: &str) -> Vec<File> {
    let mut passes: Vec<(&PerformanceSeries, Vec<StreamCase>)> = Vec::new();
    for case in crate::cases::cases(events)
        .into_iter()
        .filter(|case| !case.report.metrics.is_empty())
    {
        let Some(series) = plan.series_of(&case) else {
            continue;
        };
        match passes.iter_mut().find(|(known, _)| known.artifact_path == series.artifact_path) {
            Some((_, cases)) => cases.push(case),
            None => passes.push((series, vec![case])),
        }
    }
    let mut files = Vec::new();
    for (series, cases) in passes {
        files.push(File {
            artifact_path: series.artifact_path.clone(),
            text: render(series, &plan.ide_build, generated, &median_of_passes(&cases)),
        });
        let planned = plan
            .series
            .iter()
            .filter(|known| known.artifact_path == series.artifact_path)
            .count();
        if planned < 2 {
            continue;
        }
        let pass_series = PerformanceSeries {
            project: format!("{}{PASS_PROJECT_SUFFIX}", series.project),
            ..series.clone()
        };
        for case in last_attempts(&cases) {
            let number = pass_number(&case.name);
            let mut metrics = case.report.metrics.clone();
            metrics.insert(PASS_METRIC.to_owned(), serde_json::Number::from(number));
            files.push(File {
                artifact_path: format!("{}/pass-{number}", series.artifact_path),
                text: render(&pass_series, &plan.ide_build, generated, &metrics),
            });
        }
    }
    files
}

/// The number of the pass that `name` names: `k` of a name that ends with `, pass <k> of <n>`, else 1, the first pass.
pub fn pass_number(name: &str) -> u32 {
    name.rsplit_once(", pass ")
        .and_then(|(_, rest)| rest.split_once(" of "))
        .and_then(|(number, _)| number.parse().ok())
        .unwrap_or(1)
}

/// The last attempt of each pass of `cases`, in stream order: a rerun repeats the name of its scenario.
fn last_attempts(cases: &[StreamCase]) -> Vec<&StreamCase> {
    let mut last: Vec<&StreamCase> = Vec::new();
    for case in cases {
        last.retain(|known| known.name != case.name);
        last.push(case);
    }
    last
}

/// The metrics of a series from the `cases` of its passes, in stream order: the median of each metric.
///
/// A rerun repeats the name of its scenario, and its last attempt counts. Only the passes that passed count, because a
/// failed pass can stop in the middle of its measurement. When no pass passed, every pass counts, as the one pass of a
/// failed scenario did before passes existed. A metric that a pass did not report takes the median of the others.
pub fn median_of_passes(cases: &[StreamCase]) -> BTreeMap<String, serde_json::Number> {
    let last = last_attempts(cases);
    let passed: Vec<&StreamCase> = last.iter().copied().filter(|case| case.outcome == CaseOutcome::Passed).collect();
    let counted = if passed.is_empty() { last } else { passed };
    let mut values: BTreeMap<&str, Vec<f64>> = BTreeMap::new();
    for case in counted {
        for (name, value) in &case.report.metrics {
            values.entry(name).or_default().extend(value.as_f64());
        }
    }
    values
        .into_iter()
        .filter_map(|(name, mut values)| {
            values.sort_by(f64::total_cmp);
            let middle = values.len() / 2;
            let median = if values.len() % 2 == 0 {
                f64::midpoint(*values.get(middle.checked_sub(1)?)?, *values.get(middle)?)
            } else {
                *values.get(middle)?
            };
            Some((name.to_owned(), serde_json::Number::from_f64(median)?))
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
    if name.starts_with("growth.") {
        counter(format!("{name}.x100"), whole(value * 100.0))
    } else if name.ends_with("Ms") {
        let duration = whole(value);
        Metric {
            n: name.to_owned(),
            d: Some(duration),
            c: None,
            v: duration,
        }
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
