load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "terminal",
    srcs = ["src"],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/terminal/lib:jediterm-pty-2.2.1"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/plugins/terminal/lib:jediterm-pty-2.2.1",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:pty4j",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:purejavacomm",
        "//tools/idea/platform/remote-servers/api:remote-servers-api[module]",
        "//tools/idea/platform/remote-servers/impl:remote-servers-impl[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)
