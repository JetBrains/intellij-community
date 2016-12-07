load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "settings-repository",
    # do not sort: must match IML order
    srcs = [
        "resources",
        "src",
    ],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:jgit"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:jgit",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jackson",
        "//tools/idea/.idea/libraries:Slf4j",
    ],
)
