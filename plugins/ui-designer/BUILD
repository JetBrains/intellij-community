load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ui-designer",
    srcs = ["src"],
    iml_files = ["ui-designer.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:external-system-impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/java-i18n"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/java/compiler/forms-compiler[module]",
        "//tools/idea/platform/forms_rt[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea:execution-impl[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/platform/icons[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/plugins/java-i18n[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:spellchecker[module]",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea:xml[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
    ],
)
