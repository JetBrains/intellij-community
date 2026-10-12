//! The routes.
//!
//! Nothing here cleans a path: a traversal attempt is refused, never redirected to where it pointed.

use std::collections::HashMap;
use std::convert::Infallible;
use std::io;
use std::ops::Not;
use std::path::{Path as FsPath, PathBuf};
use std::sync::Arc;
use std::time::{Duration, SystemTime};

use avl_base::sync::lock;
use avl_trace::bundle::bundle_file;
use avl_trace_tools::discover::{Location, clean_bundle_path};
use avl_trace_tools::viewer::{IDENTITY, IDENTITY_HEADER, RUNS_ROUTE};
use axum::Router;
use axum::body::{Body, Bytes};
use axum::extract::{DefaultBodyLimit, Path, Query, Request, State};
use axum::middleware::{self, Next};
use axum::response::sse::{Event, KeepAlive, Sse};
use axum::response::{IntoResponse, Response};
use axum::routing::{any, get, post};
use futures::{Stream, StreamExt, stream};
use http::{HeaderMap, StatusCode, header};
use serde::{Deserialize, Serialize};
use tokio::sync::broadcast;

use super::files::{content_type, file_etag, json_response, matches_etag, serve_file, set, text_error};
use super::journals::{read_records, valid_run_id};
use super::service::Activity;
use super::state::{Scenario, Server, listed, runs_of};
use super::{EVENTS_ROUTE, PLAN_ROUTE, RUN_ROUTE};
use crate::plan::Input;
use crate::{Exit, Refusal, code};

/// The whole server as one service.
pub(crate) fn router(server: Arc<Server>) -> Router {
    Router::new()
        .route("/", any(|| async { found("/air/runs") }))
        .route(RUNS_ROUTE, get(serve_runs))
        .route(&format!("{RUN_ROUTE}{{*run_id}}"), get(serve_run))
        .route("/__air/bundle/{*rest}", get(serve_bundle))
        .route(EVENTS_ROUTE, get(serve_events))
        .route(PLAN_ROUTE, post(serve_plan).layer(DefaultBodyLimit::max(MAX_PLAN_BODY)))
        .route("/air", any(|| async { found("/air/") }))
        .route("/air/", get(serve_site_root))
        .route("/air/{*file}", get(serve_site))
        .fallback(|| async { text_error(StatusCode::NOT_FOUND, "404 page not found") })
        .layer(middleware::from_fn_with_state(Arc::clone(&server), guard))
        .with_state(server)
}

fn found(location: &'static str) -> Response {
    (StatusCode::FOUND, [(header::LOCATION, location)]).into_response()
}

/// Counts every request as activity, names the server on every answer, and answers only requests addressed to a
/// loopback name and not marked cross-site.
async fn guard(State(server): State<Arc<Server>>, request: Request, next: Next) -> Response {
    let _active = Activity::begin(&server);
    let host = request
        .headers()
        .get(header::HOST)
        .and_then(|value| value.to_str().ok())
        .map(str::to_owned)
        .or_else(|| request.uri().authority().map(ToString::to_string))
        .unwrap_or_default();
    let cross_site = request.headers().get("sec-fetch-site").is_some_and(|value| value == "cross-site");
    let mut response = if !loopback_host(&host) {
        text_error(
            StatusCode::FORBIDDEN,
            "air-trace serve answers only requests addressed to 127.0.0.1 or localhost",
        )
    } else if cross_site {
        text_error(StatusCode::FORBIDDEN, "air-trace serve refuses cross-site requests")
    } else {
        next.run(request).await
    };
    if let Ok(name) = header::HeaderName::from_bytes(IDENTITY_HEADER.as_bytes()) {
        set(response.headers_mut(), name, IDENTITY);
    }
    response
}

/// Whether a request's Host names this machine's loopback. A page on another origin can resolve its own name to
/// 127.0.0.1 and read a local server as same-origin; its Host header still carries its own name, which is what this
/// refuses.
fn loopback_host(host_header: &str) -> bool {
    let host = if let Some(bracketed) = host_header.strip_prefix('[') {
        bracketed.split(']').next().unwrap_or_default()
    } else if host_header.matches(':').count() == 1 {
        host_header.split(':').next().unwrap_or_default()
    } else {
        host_header
    };
    ["127.0.0.1", "localhost", "::1"]
        .iter()
        .any(|loopback| host.eq_ignore_ascii_case(loopback))
}

// --- runs ----------------------------------------------------------------------------------------------------

async fn serve_runs(State(server): State<Arc<Server>>) -> Response {
    // A listing at most a second old: the viewer asks on every `runs-changed`, and a burst of those should not be a
    // burst of walks.
    let current = server.blocking(|server| server.snapshot_now(Duration::from_secs(1))).await;
    json_response(StatusCode::OK, &runs_of(&current))
}

/// One controller run: its summary and its journal's records from `?from=`, 0 by default.
async fn serve_run(
    State(server): State<Arc<Server>>,
    Path(run_id): Path<String>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    if !valid_run_id(&run_id) {
        return text_error(
            StatusCode::FORBIDDEN,
            "a run id is one path component of letters, digits, '.', '_' and '-'",
        );
    }
    if server.journals.dir().is_none() {
        return text_error(StatusCode::NOT_FOUND, "this server reads no controller runs");
    }
    let from = match query.get("from").map(|value| value.parse::<u64>()) {
        None => 0,
        Some(Ok(from)) => from,
        Some(Err(_)) => return text_error(StatusCode::BAD_REQUEST, "from must be a byte offset"),
    };
    server
        .blocking(move |server| {
            let real = match server.journals.real_journal(&run_id) {
                Ok(Some(real)) => real,
                Ok(None) => return text_error(StatusCode::FORBIDDEN, "outside the runs directory"),
                Err(error) if error.kind() == io::ErrorKind::NotFound => {
                    return text_error(StatusCode::NOT_FOUND, &format!("no run {run_id}"));
                }
                Err(error) => {
                    return text_error(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string());
                }
            };
            match read_records(&real, from) {
                Ok(mut answer) => {
                    answer.run = server.journals.summary(&run_id, SystemTime::now());
                    json_response(StatusCode::OK, &answer)
                }
                Err(error) => text_error(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
            }
        })
        .await
}

// --- bundles -------------------------------------------------------------------------------------------------

/// One file of a bundle in the listing `GET /__air/bundle/<id>` answers.
#[derive(Serialize)]
struct BundleFile {
    path: String,
    size: u64,
    /// A zip entry kept uncompressed, which is served in ranges straight from the zip.
    #[serde(skip_serializing_if = "Not::not")]
    stored: bool,
}

#[derive(Serialize)]
struct BundleListing {
    #[serde(flatten)]
    scenario: Scenario,
    files: Vec<BundleFile>,
}

async fn serve_bundle(State(server): State<Arc<Server>>, Path(rest): Path<String>, request: Request) -> Response {
    let (id, file) = rest.split_once('/').unwrap_or((&rest, ""));
    let (id, file) = (id.to_owned(), file.to_owned());
    let lookup_id = id.clone();
    let Some(found) = server.blocking(move |server| server.lookup(&lookup_id)).await else {
        return text_error(StatusCode::NOT_FOUND, &format!("no bundle {id}"));
    };
    if file.is_empty() {
        return server.blocking(move |server| bundle_listing(server, &found)).await;
    }
    let Some(clean) = clean_bundle_path(&file) else {
        return text_error(StatusCode::FORBIDDEN, "a bundle path must stay inside its bundle");
    };
    match &found.location {
        Location::Zip { path, prefix } => {
            server
                .serve_zip_entry(request, path.clone(), format!("{prefix}{clean}"), clean)
                .await
        }
        Location::Dir(dir) => serve_dir_file(request, dir, clean).await,
        Location::File => text_error(StatusCode::NOT_FOUND, "404 page not found"),
    }
}

async fn serve_dir_file(request: Request, dir: &FsPath, clean: &str) -> Response {
    let full = bundle_file(dir, clean);
    let resolved = tokio::task::spawn_blocking(move || fscopy::resolve_links(&full))
        .await
        .unwrap_or_else(|error| Err(io::Error::other(error)));
    let real = match resolved {
        Ok(real) => real,
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            return text_error(StatusCode::NOT_FOUND, "404 page not found");
        }
        Err(error) => return text_error(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
    };
    if !real.starts_with(dir) {
        return text_error(StatusCode::FORBIDDEN, "a bundle path must stay inside its bundle");
    }
    let Ok(info) = tokio::fs::metadata(&real)
        .await
        .map_err(drop)
        .and_then(|info| if info.is_file() { Ok(info) } else { Err(()) })
    else {
        return text_error(StatusCode::NOT_FOUND, "404 page not found");
    };
    // A running bundle's JSON Lines grow, so the tag carries the size as well as the time: two writes within one
    // second would otherwise answer 304 to a request that should see the second.
    let etag = file_etag('d', &info);
    let mut response = if matches_etag(request.headers(), &etag) {
        StatusCode::NOT_MODIFIED.into_response()
    } else {
        serve_file(request, &real, &content_type(clean), false).await
    };
    let headers = response.headers_mut();
    set(headers, header::CACHE_CONTROL, "no-cache");
    set(headers, header::ETAG, &etag);
    response
}

fn bundle_listing(server: &Server, found: &avl_trace_tools::discover::Bundle) -> Response {
    let mut files = Vec::new();
    match &found.location {
        Location::Zip { path, prefix } => {
            let index = match server.scanner.zips().get(path) {
                Ok(index) => index,
                Err(error) => {
                    return text_error(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string());
                }
            };
            for (name, entry) in index.entries() {
                if let Some(relative) = name.strip_prefix(prefix.as_str())
                    && !relative.is_empty()
                    && !relative.ends_with('/')
                {
                    files.push(BundleFile {
                        path: relative.to_owned(),
                        size: entry.size,
                        stored: entry.stored,
                    });
                }
            }
        }
        Location::Dir(dir) => {
            for entry in walkdir::WalkDir::new(dir).into_iter().filter_map(Result::ok) {
                if !entry.file_type().is_file() {
                    continue;
                }
                let (Ok(relative), Ok(info)) = (entry.path().strip_prefix(dir), entry.metadata()) else {
                    continue;
                };
                let path = relative
                    .components()
                    .map(|component| component.as_os_str().to_string_lossy())
                    .collect::<Vec<_>>()
                    .join("/");
                files.push(BundleFile {
                    path,
                    size: info.len(),
                    stored: false,
                });
            }
        }
        Location::File => {}
    }
    files.sort_by(|left, right| left.path.cmp(&right.path));
    json_response(
        StatusCode::OK,
        &BundleListing {
            scenario: listed(&found.summary),
            files,
        },
    )
}

// --- events --------------------------------------------------------------------------------------------------

/// Keeps an idle stream from being closed by whatever sits between: the Vite proxy, a browser that gives up on a
/// silent connection.
const EVENTS_HEARTBEAT: Duration = Duration::from_secs(15);

async fn serve_events(State(server): State<Arc<Server>>) -> Response {
    let messages = server.hub.subscribe();
    // `ready` carries the current generation, so a viewer that listed before subscribing can tell whether it missed
    // a change in between.
    let generation = server.current().map_or(0, |current| current.generation);
    let ready = Event::default()
        .retry(Duration::from_secs(2))
        .event("ready")
        .data(format!("{{\"generation\":{generation}}}"));
    // An open stream is a request in progress for the idle stop, for as long as the stream lives.
    let active = Activity::begin(&server);
    let frames = stream::unfold((messages, active), |(mut messages, active)| async move {
        match messages.recv().await {
            Ok(frame) => Some((
                Ok::<_, Infallible>(Event::default().event(frame.event).data(&*frame.data)),
                (messages, active),
            )),
            // A viewer that fell behind is dropped; its EventSource reconnects and lists the runs again.
            Err(broadcast::error::RecvError::Lagged(_) | broadcast::error::RecvError::Closed) => None,
        }
    });
    let events: std::pin::Pin<Box<dyn Stream<Item = Result<Event, Infallible>> + Send>> = Box::pin(
        stream::once(async move { Ok(ready) })
            .chain(frames)
            .take_until(server.shutdown.clone().cancelled_owned()),
    );
    let mut response = Sse::new(events)
        .keep_alive(KeepAlive::new().interval(EVENTS_HEARTBEAT).text("ping"))
        .into_response();
    let headers = response.headers_mut();
    set(headers, header::CACHE_CONTROL, "no-cache");
    set(headers, header::HeaderName::from_static("x-accel-buffering"), "no");
    response
}

// --- the planner ---------------------------------------------------------------------------------------------

/// `POST /__air/plan` as JSON: text typed or pasted. A dropped file comes the other way, [PLAN_FILE_CONTENT_TYPE].
#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
struct PlanRequest {
    text: String,
}

/// How the viewer sends a dropped file: its bytes as the body, its name in the [PLAN_FILE_NAME_PARAMETER] query
/// parameter. The browser then streams the `File` as it is, where base64 inside JSON made the page read the whole
/// file and encode it on its main thread before a byte left, only for a wrong zip of several hundred megabytes to
/// be refused here.
const PLAN_FILE_CONTENT_TYPE: &str = "application/octet-stream";
const PLAN_FILE_NAME_PARAMETER: &str = "fileName";

/// Bounds a planner request. A dropped bundle zip is the largest input the planner reads.
const MAX_PLAN_BODY: usize = 256 << 20;

async fn serve_plan(
    State(server): State<Arc<Server>>,
    Query(query): Query<HashMap<String, String>>,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    let input = match read_plan_request(&query, &headers, &body) {
        Ok(input) => input,
        Err(refused) => return refusal_response(StatusCode::BAD_REQUEST, &refused),
    };
    let Some(repo_root) = server.settings.repo_root.clone() else {
        let refused = Refusal::new(
            code::NO_CHECKOUT,
            Exit::Broken,
            "the planner needs the checkout, and this server was started outside one",
        );
        return refusal_response(StatusCode::UNPROCESSABLE_ENTITY, &refused);
    };
    let answer = server
        .blocking(move |server| {
            let mut result = (server.settings.resolve)(&repo_root, input)?;
            let current = server.snapshot_now(Duration::from_secs(1));
            result.join_existing(current.found.bundles.iter().map(|bundle| &**bundle));
            anyhow::Ok(result)
        })
        .await;
    match answer {
        Ok(result) => json_response(StatusCode::OK, &result),
        Err(error) => {
            let refused = Refusal::new(code::PLAN_FAILED, Exit::Broken, format!("{error:#}"));
            refusal_response(StatusCode::UNPROCESSABLE_ENTITY, &refused)
        }
    }
}

/// A refusal of the planner route: `{error, code}`, the message and the code. The site reads `error`.
fn refusal_response(status: StatusCode, refused: &Refusal) -> Response {
    json_response(status, &serde_json::json!({ "error": refused.message, "code": refused.code }))
}

/// Reads either shape of a planner request, and answers what is wrong with it when it cannot be read.
fn read_plan_request(query: &HashMap<String, String>, headers: &HeaderMap, body: &[u8]) -> Result<Input, Refusal> {
    let unreadable = |message: String| Refusal::new("plan_request_unreadable", Exit::Usage, message);
    let media_type = headers
        .get(header::CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.split(';').next())
        .map(|value| value.trim().to_ascii_lowercase())
        .unwrap_or_default();
    if media_type == PLAN_FILE_CONTENT_TYPE {
        let name = query.get(PLAN_FILE_NAME_PARAMETER).filter(|name| !name.is_empty()).ok_or_else(|| {
            unreadable(format!(
                "a file sent as {PLAN_FILE_CONTENT_TYPE} needs its name in ?{PLAN_FILE_NAME_PARAMETER}="
            ))
        })?;
        return Ok(Input {
            file_name: name.clone(),
            content: body.to_vec(),
            ..Input::default()
        });
    }
    let decoded: PlanRequest = serde_json::from_slice(body).map_err(|error| unreadable(format!("the request is not {{text}}: {error}")))?;
    Ok(Input {
        text: decoded.text,
        ..Input::default()
    })
}

// --- the site ------------------------------------------------------------------------------------------------

async fn serve_site_root(State(server): State<Arc<Server>>, request: Request) -> Response {
    serve_site_file(server, String::new(), request).await
}

async fn serve_site(State(server): State<Arc<Server>>, Path(file): Path<String>, request: Request) -> Response {
    serve_site_file(server, file, request).await
}

/// Serves the built docs site, which holds the viewer. A path that names no file is a route of the single-page app
/// and gets `index.html`, unless it looks like an asset: a missing script must be a 404, not a page of HTML the
/// browser then fails to run.
async fn serve_site_file(server: Arc<Server>, file: String, request: Request) -> Response {
    let Some(site) = server.settings.site_dir.clone() else {
        return placeholder(&server).await;
    };
    let accepts_html = request
        .headers()
        .get(header::ACCEPT)
        .and_then(|value| value.to_str().ok())
        .is_some_and(|value| value.contains("text/html"));
    let chosen = tokio::task::spawn_blocking(move || choose_site_file(&site, &file, accepts_html)).await;
    let (name, path) = match chosen {
        Ok(SiteFile::Serve(name, path)) => (name, path),
        Ok(SiteFile::Outside) => return text_error(StatusCode::FORBIDDEN, "outside the site"),
        Ok(SiteFile::Missing) => return text_error(StatusCode::NOT_FOUND, "404 page not found"),
        Ok(SiteFile::Unbuilt) | Err(_) => return placeholder(&server).await,
    };
    let mut response = serve_file(request, &path, &content_type(&name), true).await;
    // Vite names every asset by its content, so an asset never changes under its name; the pages that name them
    // do, and are revalidated on every load.
    let cache = if name.starts_with("assets/") {
        "public, max-age=31536000, immutable"
    } else {
        "no-cache"
    };
    set(response.headers_mut(), header::CACHE_CONTROL, cache);
    response
}

enum SiteFile {
    Serve(String, PathBuf),
    Outside,
    Missing,
    Unbuilt,
}

fn choose_site_file(site: &FsPath, file: &str, accepts_html: bool) -> SiteFile {
    let Ok(real_site) = fscopy::resolve_links(site) else {
        return SiteFile::Unbuilt;
    };
    if !real_site.join("index.html").is_file() {
        return SiteFile::Unbuilt;
    }
    let mut name = file.trim_end_matches('/').to_owned();
    if name.is_empty() {
        name = "index.html".to_owned();
    }
    if clean_bundle_path(&name).is_none() {
        return SiteFile::Outside;
    }
    let inside = |name: &str| {
        let path = name.split('/').fold(real_site.clone(), |path, segment| path.join(segment));
        // A link out of the site is no file of it.
        fscopy::resolve_links(&path).ok().filter(|real| real.starts_with(&real_site))
    };
    let mut found = inside(&name);
    if found.as_ref().is_some_and(|path| path.is_dir()) {
        name = format!("{name}/index.html");
        found = inside(&name);
    }
    match found.filter(|path| path.is_file()) {
        Some(path) => SiteFile::Serve(name, path),
        None if FsPath::new(&name).extension().is_some() && !accepts_html => SiteFile::Missing,
        None => SiteFile::Serve("index.html".to_owned(), real_site.join("index.html")),
    }
}

/// The page where the site would be when it has not been built. It says whether the server builds the site now, or
/// why its build failed.
async fn placeholder(server: &Arc<Server>) -> Response {
    let current = server.blocking(|server| server.snapshot_now(Duration::from_secs(1))).await;
    let mut roots = String::new();
    for state in &current.found.roots {
        let mut note = if state.exists {
            format!("{} bundles", state.bundles)
        } else {
            "absent".to_owned()
        };
        if let Some(error) = &state.error {
            note.push_str(": ");
            note.push_str(error);
        }
        roots.push_str(&format!(
            "<li><code>{}</code> ({}): {}</li>\n",
            escape_html(&state.path.to_string_lossy()),
            escape_html(state.kind.as_str()),
            escape_html(&note)
        ));
    }
    let site = server
        .settings
        .site_dir
        .as_ref()
        .map_or_else(|| "out/air-site".to_owned(), |site| site.to_string_lossy().into_owned());
    let (running, problem, output) = {
        let build = lock(&server.site_build);
        (
            build.running,
            build.problem.clone(),
            build.output.iter().cloned().collect::<Vec<_>>(),
        )
    };
    let mut state = String::new();
    let mut refresh = "";
    if running {
        refresh = r#"<meta http-equiv="refresh" content="5">"#;
        state.push_str(
            "<h1>The trace viewer is building</h1>\n<p>This server builds the AIR docs site, which holds the viewer. \
             This page loads again every 5 seconds, and shows the viewer when the build is done.</p>\n",
        );
    } else if let Some(problem) = problem {
        state.push_str(&format!(
            "<h1>The trace viewer did not build</h1>\n<p>The build of the AIR docs site failed: <code>{}</code></p>\n\
             <p>Fix the cause, then build it by hand and reload this page:</p>\n<pre><code>cd plugins/air/docs \
             &amp;&amp; pnpm build</code></pre>\n",
            escape_html(&problem)
        ));
    } else {
        state.push_str(
            "<h1>The trace viewer is not built</h1>\n<p>The viewer is part of the AIR docs site. Build it once, then \
             reload this page:</p>\n<pre><code>cd plugins/air/docs &amp;&amp; pnpm build</code></pre>\n",
        );
    }
    if !output.is_empty() {
        state.push_str(&format!(
            "<p>The last lines of the build:</p>\n<pre class=\"build-output\"><code>{}</code></pre>\n",
            escape_html(&output.join("\n"))
        ));
    }
    let page = format!(
        "<!doctype html>\n<html lang=\"en\">\n<meta charset=\"utf-8\">\n{refresh}\n<title>AIR traces</title>\n\
         <style>body{{font:15px/1.5 system-ui,sans-serif;max-width:46rem;margin:3rem auto;padding:0 1rem}}\
         code{{font-size:90%}}pre{{overflow-x:auto}}</style>\n{state}<p>The site is <code>{}</code>, which this \
         server serves at <a href=\"/air/\">/air/</a>.</p>\n<p>The API is up meanwhile: <a \
         href=\"{RUNS_ROUTE}\">{RUNS_ROUTE}</a> lists the runs found under these roots:</p>\n<ul>\n{roots}</ul>\n\
         </html>\n",
        escape_html(&site)
    );
    (
        [
            (header::CONTENT_TYPE, "text/html; charset=utf-8"),
            (header::CACHE_CONTROL, "no-store"),
        ],
        Body::from(page),
    )
        .into_response()
}

fn escape_html(text: &str) -> String {
    let mut escaped = String::with_capacity(text.len());
    for character in text.chars() {
        match character {
            '&' => escaped.push_str("&amp;"),
            '<' => escaped.push_str("&lt;"),
            '>' => escaped.push_str("&gt;"),
            '"' => escaped.push_str("&#34;"),
            '\'' => escaped.push_str("&#39;"),
            other => escaped.push(other),
        }
    }
    escaped
}
