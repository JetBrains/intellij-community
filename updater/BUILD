load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "updater",
    srcs = ["src"],
    iml_files = ["updater.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/xml/relaxng",
        "//tools/idea/json",
        "//tools/idea/images",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/lib:javaxdelta-2.0.1",
    ],
)

java_library(
    name = "updater_lib",
    srcs = glob(["src/**/*.java"]),
    tags = ["managed"],
    deps = [
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/lib:javaxdelta-2.0.1",
    ],
)

java_binary(
    name = "updater_binary",
    main_class = "com.intellij.updater.Runner",
    tags = ["managed"],
    runtime_deps = [":updater_lib"],
)
