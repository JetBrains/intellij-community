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
