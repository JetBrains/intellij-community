#[cfg(unix)]
use std::time::SystemTime;

use pretty_assertions::assert_eq;

use super::*;

/// Sets the time a file was last changed.
#[cfg(unix)]
fn set_modified(path: &Path, time: SystemTime) {
    fs::File::options().write(true).open(path).unwrap().set_modified(time).unwrap();
}

/// The UI primitive of the genui catalog in the fixture checkout.
#[cfg(unix)]
const CATALOG_UI_FILE: &str = "plugins/air/frontend/genui/jcef/web-bundle/src/catalog/ui/button.tsx";

/// A checkout with the docs project, the kernel and the genui catalog, whose files are an hour old.
#[cfg(unix)]
fn checkout() -> tempfile::TempDir {
    let checkout = tempfile::tempdir().unwrap();
    let files = [
        "plugins/air/docs/package.json",
        "plugins/air/docs/vite.viewer.config.ts",
        "plugins/air/docs/src/main.ts",
        "community/tools/docs-site/package.json",
        "community/tools/docs-site/pnpm-lock.yaml",
        "community/tools/docs-site/src/main.ts",
        CATALOG_UI_FILE,
    ];
    for file in files {
        let path = checkout.path().join(file);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, "{}").unwrap();
        set_modified(&path, SystemTime::now() - Duration::from_hours(1));
    }
    checkout
}

/// A fake node, and a fake pnpm that appends its arguments to `calls`, prints its verb, and runs `build` for
/// `run build:viewer`.
#[cfg(unix)]
fn tools(dir: &Path, build: &str) -> SiteTools {
    use std::os::unix::fs::PermissionsExt;
    let script = |name: &str, text: String| {
        let path = dir.join(name);
        fs::write(&path, text).unwrap();
        fs::set_permissions(&path, fs::Permissions::from_mode(0o755)).unwrap();
        path
    };
    let calls = dir.join("calls");
    SiteTools::Pinned {
        node: script("node", "#!/bin/sh\nexit 0\n".to_owned()),
        pnpm: script(
            "pnpm",
            format!(
                "#!/bin/sh\necho \"$*\" >> '{}'\necho \"pnpm $3 $4\"\nif [ \"$3 $4\" = 'run build:viewer' ]; then\n{build}\nfi\n",
                calls.display()
            ),
        ),
    }
}

/// What `build:viewer` writes: the page and one asset in `<checkout>/out/air-trace-viewer`. `$2` is the project.
#[cfg(unix)]
const BUILDS_THE_VIEWER: &str = "out=\"$2/../../../out/air-trace-viewer\"; mkdir -p \"$out/assets\"; \
                                 echo '<!doctype html>' > \"$out/index.html\"; echo 'export {}' > \"$out/assets/index.js\"";

/// The viewer of a build in the checkout with the tools, and the stderr of the step.
#[cfg(unix)]
fn build(checkout: &Path, tools: SiteTools, timeout: Duration) -> (Result<PathBuf, String>, String) {
    let source = ViewerSource::Build {
        checkout: checkout.to_path_buf(),
        tools: Some(tools),
        timeout,
    };
    let mut stderr = Vec::new();
    let dir = viewer_dir(&source, &mut stderr).unwrap();
    (dir, String::from_utf8(stderr).unwrap())
}

/// Without `--viewer`, the step installs the kernel and the docs project, builds the viewer, and says the time it took.
/// A viewer newer than its sources is not built again. A source outside `src` builds it again, such as a UI primitive
/// of the genui catalog or the Vite configuration. Both installs then run again over their `node_modules`.
#[cfg(unix)]
#[test]
fn a_missing_viewer_is_built_in_the_checkout_and_kept_while_it_is_current() {
    let checkout = checkout();
    let fakes = tempfile::tempdir().unwrap();
    let tools = tools(fakes.path(), BUILDS_THE_VIEWER);
    let (dir, stderr) = build(checkout.path(), tools.clone(), Duration::from_mins(1));
    let built = checkout.path().join("out/air-trace-viewer");
    assert_eq!(dir, Ok(built.clone()));
    let project = checkout.path().join("plugins/air/docs");
    let kernel = checkout.path().join("community/tools/docs-site");
    assert_eq!(
        fs::read_to_string(fakes.path().join("calls")).unwrap(),
        format!(
            "--dir {kernel} install --frozen-lockfile\n--dir {project} install --frozen-lockfile\n--dir {project} run build:viewer\n",
            kernel = kernel.display(),
            project = project.display()
        )
    );
    let lines: Vec<&str> = stderr.lines().collect();
    assert_eq!(
        lines[0],
        format!(
            "air-trace teamcity-publish: the viewer is not built; building it with `pnpm run build:viewer` in {}",
            project.display()
        )
    );
    assert!(
        lines.contains(&"pnpm run build:viewer"),
        "the build's output goes to stderr: {stderr}"
    );
    let last = lines.last().unwrap();
    assert!(
        last.starts_with("air-trace teamcity-publish: built the viewer in ") && last.ends_with(&format!(" s {}", tools_text(&tools))),
        "{last}"
    );
    assert!(built.join(VIEWER_PAGE).is_file());

    let (again, stderr) = build(checkout.path(), tools.clone(), Duration::from_mins(1));
    assert_eq!(again, Ok(built.clone()));
    assert_eq!(
        stderr,
        format!(
            "air-trace teamcity-publish: the viewer in {} is newer than its sources; no build\n",
            built.display()
        )
    );
    assert_eq!(fs::read_to_string(fakes.path().join("calls")).unwrap().lines().count(), 3);

    for dir in [&project, &kernel] {
        fs::create_dir_all(dir.join("node_modules")).unwrap();
    }
    let changed = [
        (CATALOG_UI_FILE, 6),
        ("plugins/air/docs/vite.viewer.config.ts", 9),
        ("community/tools/docs-site/pnpm-lock.yaml", 12),
    ];
    for (source, calls) in changed {
        set_modified(&checkout.path().join(source), SystemTime::now() + Duration::from_mins(1));
        let (rebuilt, stderr) = build(checkout.path(), tools.clone(), Duration::from_mins(1));
        assert_eq!(rebuilt, Ok(built.clone()));
        assert!(
            stderr.starts_with("air-trace teamcity-publish: the viewer is older than its sources; building it"),
            "{source}: {stderr}"
        );
        assert_eq!(
            fs::read_to_string(fakes.path().join("calls")).unwrap().lines().count(),
            calls,
            "{source}"
        );
        set_modified(&checkout.path().join(source), SystemTime::now() - Duration::from_hours(1));
    }
}

/// A build that fails, or that does not end within its time, is the reason there is no viewer, with the time it took.
#[cfg(unix)]
#[test]
fn a_viewer_build_that_fails_or_does_not_end_is_the_reason_for_no_viewer() {
    let checkout = checkout();
    let fakes = tempfile::tempdir().unwrap();
    let project = checkout.path().join("plugins/air/docs");
    let (failed, _) = build(checkout.path(), tools(fakes.path(), "exit 3"), Duration::from_mins(1));
    let failed = failed.unwrap_err();
    assert!(failed.starts_with("the viewer build failed after "), "{failed}");
    assert!(
        failed.ends_with(&format!(" s: pnpm --dir {} run build:viewer: exit status: 3", project.display())),
        "{failed}"
    );

    // The build starts a child of its own and waits for it, as Vite does under pnpm.
    let child = fakes.path().join("child.pid");
    let start = Instant::now();
    let hangs = format!("sleep 30 & echo $! > '{}'; wait", child.display());
    let (hung, _) = build(checkout.path(), tools(fakes.path(), &hangs), Duration::from_millis(500));
    let hung = hung.unwrap_err();
    assert!(hung.ends_with(" s: it did not end within 500ms"), "{hung}");
    assert!(start.elapsed() < Duration::from_secs(20), "the build stops at its time limit");
    let pid: i32 = fs::read_to_string(&child).unwrap().trim().parse().unwrap();
    let alive = || nix::sys::signal::kill(nix::unistd::Pid::from_raw(pid), None).is_ok();
    let deadline = Instant::now() + Duration::from_secs(5);
    while alive() && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(50));
    }
    assert!(!alive(), "the child of the build, {pid}, outlived the build");
}

/// Without `--viewer` and without a checkout, or in a checkout without the docs project, there is nothing to build.
#[test]
fn without_a_checkout_or_its_docs_project_there_is_no_viewer() {
    let mut stderr = Vec::new();
    assert_eq!(
        viewer_dir(&ViewerSource::Missing(NO_CHECKOUT.to_owned()), &mut stderr).unwrap(),
        Err("no --viewer DIR, and no checkout to build one in".to_owned())
    );
    let empty = tempfile::tempdir().unwrap();
    let source = ViewerSource::Build {
        checkout: empty.path().to_path_buf(),
        tools: None,
        timeout: VIEWER_BUILD_TIMEOUT,
    };
    assert_eq!(
        viewer_dir(&source, &mut stderr).unwrap(),
        Err(format!(
            "no --viewer DIR, and {} holds no docs project to build one from",
            empty.path().display()
        ))
    );
    assert_eq!(stderr, b"");
}

/// `--viewer` wins. Else the tools that the flags or the variables name build the viewer in the checkout, and `none`
/// builds none. Without them, a local step asks Bazel, as `serve` does, and a step on TeamCity builds no viewer,
/// because there it starts no Bazel. One tool alone, or no checkout, builds no viewer either.
#[test]
fn the_viewer_comes_from_the_flag_the_named_tools_or_bazel_but_never_bazel_on_team_city() {
    let checkout = || Some(PathBuf::from("/checkout"));
    let tools = |node: Option<&str>, pnpm: Option<&str>, on_team_city| Tools {
        node: node.map(PathBuf::from),
        pnpm: pnpm.map(PathBuf::from),
        on_team_city,
    };
    let build = |tools| ViewerSource::Build {
        checkout: PathBuf::from("/checkout"),
        tools,
        timeout: VIEWER_BUILD_TIMEOUT,
    };
    let named = SiteTools::Pinned {
        node: PathBuf::from("/tools/node"),
        pnpm: PathBuf::from("/tools/pnpm"),
    };
    let missing = |source: ViewerSource| match source {
        ViewerSource::Missing(reason) => reason,
        other => panic!("a viewer: {other:?}"),
    };

    assert_eq!(
        viewer_source(Some(PathBuf::from("/viewer")), checkout(), tools(Some("none"), Some("none"), true)),
        ViewerSource::Built(PathBuf::from("/viewer"))
    );
    for on_team_city in [false, true] {
        assert_eq!(
            viewer_source(None, checkout(), tools(Some("/tools/node"), Some("/tools/pnpm"), on_team_city)),
            build(Some(named.clone()))
        );
        let none = missing(viewer_source(None, checkout(), tools(Some("none"), Some("none"), on_team_city)));
        assert_eq!(
            none,
            "AIR_TRACE_NODE or AIR_TRACE_PNPM is none, so no node and no pnpm build the viewer"
        );
        let half = missing(viewer_source(None, checkout(), tools(Some("/tools/node"), None, on_team_city)));
        assert!(half.starts_with("only one of AIR_TRACE_NODE and AIR_TRACE_PNPM is set"), "{half}");
    }
    assert_eq!(
        viewer_source(None, checkout(), tools(None, None, false)),
        build(None),
        "a local step asks Bazel"
    );
    let on_team_city = missing(viewer_source(None, checkout(), tools(None, None, true)));
    assert!(
        on_team_city.starts_with("on TeamCity the step starts no Bazel to find node and pnpm"),
        "{on_team_city}"
    );
    assert_eq!(missing(viewer_source(None, None, tools(None, None, false))), NO_CHECKOUT);
}
