use pretty_assertions::assert_eq;

use super::*;

fn labels(labels: &[&str]) -> Vec<String> {
    labels.iter().map(|label| (*label).to_owned()).collect()
}

// `cquery` prints the files in an order of its own, so the answer follows the labels and not the lines.
#[test]
fn each_line_goes_to_the_label_of_its_repository() {
    let stdout = "external/community++http_file+air_docker_buildx_darwin_arm64/file/docker-buildx\n\
                  external/community++http_archive+air_tart/tart.app/Contents/MacOS/tart\n\
                  \n\
                  external/community++http_archive+air_docker_darwin_arm64/docker/docker\n";
    let asked = labels(&[
        "@community//tools/vm:air_tart",
        "@community//tools/vm:air_docker_darwin_arm64",
        "@community//tools/vm:air_docker_buildx_darwin_arm64",
    ]);
    assert_eq!(
        files_by_label(&asked, stdout).unwrap(),
        [
            "external/community++http_archive+air_tart/tart.app/Contents/MacOS/tart",
            "external/community++http_archive+air_docker_darwin_arm64/docker/docker",
            "external/community++http_file+air_docker_buildx_darwin_arm64/file/docker-buildx",
        ]
    );
}

// The match is the whole last segment of the repository: `air_docker_darwin_arm64` is not the repository of
// `air_docker_buildx_darwin_arm64`, and the older `~` separator counts.
#[test]
fn a_repository_matches_its_whole_last_segment() {
    let stdout = "external/community~~http_archive~air_lima_darwin_arm64/bin/limactl\n";
    assert_eq!(
        files_by_label(&labels(&["@community//tools/vm:air_lima_darwin_arm64"]), stdout).unwrap(),
        ["external/community~~http_archive~air_lima_darwin_arm64/bin/limactl"]
    );
    let buildx = "external/community++http_file+air_docker_buildx_darwin_arm64/file/docker-buildx\n";
    let refusal = files_by_label(&labels(&["@community//tools/vm:air_docker_darwin_arm64"]), buildx).unwrap_err();
    assert_eq!(refusal.code, "bazel_external_file_unresolved");
    assert_eq!(
        refusal.message,
        "bazel cquery did not report an output file for @community//tools/vm:air_docker_darwin_arm64"
    );
}

#[test]
fn two_lines_of_one_repository_refuse() {
    let stdout = "external/community++http_archive+air_tart/a\nexternal/community++http_archive+air_tart/b\n";
    let refusal = files_by_label(&labels(&["@community//tools/vm:air_tart"]), stdout).unwrap_err();
    assert_eq!(refusal.code, "bazel_external_file_unresolved");
    assert!(refusal.message.contains("more than one output file"), "{}", refusal.message);
}

const CONTAINER: &str = "@community//tools/vm:air_container_darwin_arm64";
const BUILDX: &str = "@community//tools/vm:air_docker_buildx_darwin_arm64";

fn paths(paths: &[&str]) -> Vec<PathBuf> {
    paths.iter().map(PathBuf::from).collect()
}

// The lane build names the pinned tools beside its own outputs. Each label takes the one built file of its repository,
// whether the path runs through the execution root or through the output base.
#[test]
fn the_files_of_a_build_answer_each_label_by_its_repository() {
    let built = paths(&[
        "/base/execroot/_main/bazel-out/darwin_arm64-fastbuild/bin/plugins/air/tests/integration/ui/ui_daemon.runtime.json",
        "/base/execroot/_main/bazel-out/k8-opt/bin/external/community+/tools/vm/vm-guest-agent-linux-arm64",
        "/base/external/community++http_file+air_docker_buildx_darwin_arm64/file/docker-buildx",
        "/base/execroot/_main/external/community++http_archive+air_container_darwin_arm64/bin/container",
    ]);
    assert_eq!(
        built_files_by_label(&labels(&[CONTAINER, BUILDX]), &built),
        Some(paths(&[
            "/base/execroot/_main/external/community++http_archive+air_container_darwin_arm64/bin/container",
            "/base/external/community++http_file+air_docker_buildx_darwin_arm64/file/docker-buildx",
        ]))
    );
}

// The build answers all labels or none: a label with no file, or with two files in its repository, leaves the
// whole answer to the query.
#[test]
fn a_build_that_does_not_name_one_file_per_label_answers_nothing() {
    let container = "/base/external/community++http_archive+air_container_darwin_arm64/bin/container";
    assert_eq!(built_files_by_label(&labels(&[CONTAINER, BUILDX]), &paths(&[container])), None);
    let two = paths(&[
        container,
        "/base/external/community++http_archive+air_container_darwin_arm64/bin/helper",
    ]);
    assert_eq!(built_files_by_label(&labels(&[CONTAINER]), &two), None);
    assert_eq!(built_files_by_label(&labels(&[CONTAINER]), &[]), None);
}

/// A Bazel whose last build named `built`, and which refuses every query.
struct BuiltBazel {
    built: Vec<PathBuf>,
}

#[async_trait::async_trait]
impl BazelHost for BuiltBazel {
    async fn query(&self, _ctx: &Ctx, command: &str, _args: &[String]) -> Result<String, Refusal> {
        panic!("Bazel was asked `{command}` although the build named every file")
    }

    async fn execution_root(&self, _ctx: &Ctx) -> Result<PathBuf, Refusal> {
        panic!("Bazel was asked for its execution root although the build named every file")
    }

    fn built_files(&self) -> Vec<PathBuf> {
        self.built.clone()
    }
}

#[tokio::test]
async fn a_build_that_named_every_file_spares_the_query() {
    let container = "/base/external/community++http_archive+air_container_darwin_arm64/bin/container";
    let bazel = BuiltBazel {
        built: paths(&[container]),
    };
    let resolved = external_files(&Ctx::background(), &bazel, &labels(&[CONTAINER])).await.unwrap();
    assert_eq!(resolved, paths(&[container]));
}
