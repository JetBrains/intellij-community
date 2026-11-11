"""Maven artifacts used by the stream debugger tests."""

load("@community//build:test_deps_extension.bzl", "test_deps_repository")

_t = test_deps_repository("stream_debugger_test_deps")
download_file = _t.download_file

download_file(
    name = "org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar",
    sha256 = "2037be378980d3ba9333e97955f3b2cde392aa124d04ca73ce2eee6657199297",
)

download_file(
    name = "org/jetbrains/annotations/26.0.2/annotations-26.0.2.pom",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.pom",
    sha256 = "7ebeceade8dada7cb17eff3e014922c39022d2a378e3e2d2461f28f016ffc7f7",
)

download_file(
    name = "org/jetbrains/mockjdk/mockjdk-base-java/21.0.0/mockjdk-base-java-21.0.0.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/mockjdk/mockjdk-base-java/21.0.0/mockjdk-base-java-21.0.0.jar",
    sha256 = "32e2469e2c2fb8b494e2cd1c94584c3b76dc3bdc1ad4fcc010537eb446aea292",
)

stream_debugger_test_deps_repository = _t.make_repository_rule()
all_test_dep_targets = _t.all_targets()

MAVEN_REPOSITORY_RUNFILES_PATH = Label("@stream_debugger_test_deps//:files").repo_name + "/"
