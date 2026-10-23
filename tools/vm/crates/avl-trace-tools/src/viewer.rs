//! The trace viewer's URLs and the header that names its server. `air-trace serve` is the server, and it writes the
//! header; the controller prints the links. Neither side spells a URL of the viewer itself, so the two cannot drift.
//! How a caller finds out that the server runs, and how it starts one, is `avl_host_sys::viewer`'s.
//!
//! The viewer's pages are under [DEFAULT_SERVE_PORT] or another port. The site's `router.ts` spells the same
//! paths (`runsRoute`, `runRoute`, `traceRoute`), and a test on each side pins the same literals. The CI pages of a
//! TeamCity build are on the hosted site, [CI_SITE], and `air-trace teamcity-publish` links each test to one.

/// Where `air-trace serve` listens, on 127.0.0.1 only.
pub const DEFAULT_SERVE_PORT: u16 = 7357;

/// The Runs page: every run and every bundle on this machine.
pub fn runs_url(port: u16) -> String {
    format!("http://127.0.0.1:{port}/air/runs")
}

/// The page of one controller run, live while the run goes on.
pub fn run_url(port: u16, run_id: &str) -> String {
    format!("{}/run?id={}", runs_url(port), query_escape(run_id))
}

/// The trace page of one bundle. The scenario name is in the URL too, so that a link whose bundle is gone still
/// says what it showed.
pub fn viewer_url(port: u16, bundle_id: &str, scenario: &str) -> String {
    format!(
        "{}/trace?src={}&scenario={}",
        runs_url(port),
        query_escape(bundle_id),
        query_escape(scenario)
    )
}

/// The hosted Air site. Its CI pages show the traces that a TeamCity build published in `air-traces.zip`, through a
/// proxy of the portal to TeamCity. `air-trace teamcity-publish --site` names another site, such as a staging portal.
pub const CI_SITE: &str = "https://ij-void-docs.labs.jb.gg/air";

/// The CI page of the TeamCity build `build_id` on the site `site`: the runs of the `air-traces.zip` of that build.
pub fn ci_build_url(site: &str, build_id: &str) -> String {
    format!("{}/ci/{}", site.trim_end_matches('/'), query_escape(build_id))
}

/// The CI trace page of the bundle zip `zip` of the build `build_id`. The zip is a path relative to `runs.json` in
/// the archive. It is written as `URLSearchParams` writes a value, with each `/` kept, so the link reads as the path.
pub fn ci_trace_url(site: &str, build_id: &str, zip: &str) -> String {
    format!(
        "{}/trace?zip={}",
        ci_build_url(site, build_id),
        query_escape(zip).replace("%2F", "/")
    )
}

/// A query value as `URLSearchParams` writes it.
fn query_escape(value: &str) -> String {
    form_urlencoded::byte_serialize(value.as_bytes()).collect()
}

/// On every answer of `air-trace serve`, with the value [IDENTITY]. A probe reads it to know that the program on
/// the port is the viewer and not another program.
pub const IDENTITY_HEADER: &str = "X-Air-Trace";
pub const IDENTITY: &str = "serve";

/// The listing that a probe of the server asks for.
pub const RUNS_ROUTE: &str = "/__air/runs";

#[cfg(test)]
mod tests;
