load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.tools.updateServerMock",
    srcs = ["src/main/java"],
    iml_files = ["intellij.tools.updateServerMock.iml"],
    resources = ["src/main/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/update-server-mock/lib:log4j-api-2.6.2",
        "//tools/idea/update-server-mock/lib:log4j-core-2.6.2",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
    ],
)
