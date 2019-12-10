load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.updater",
    srcs = ["src"],
    iml_files = ["intellij.platform.updater.iml"],
    test_class = "com.android.studio.updater.UpdaterTestSuite",
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "no_test_mac",  # b/127274100
        "no_test_windows",  # b/123974530
    ],
    visibility = ["//visibility:public"],
    runtime_deps = ["//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/7.2.1:jar"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//prebuilts/tools/common/m2/repository/com/nothome/javaxdelta/2.0.1:jar",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)

java_binary(
    name = "updater",
    main_class = "com.intellij.updater.Runner",
    # This uses the jar rather than the iml_module to avoid packaging jdk langtools
    # TODO(b/124044861): switch to using the below java_binary with deploy_env once we are on a bazel that supports it.
    runtime_deps = [
        ":intellij.platform.updater.jar",
        "//prebuilts/tools/common/m2/repository/com/nothome/javaxdelta/2.0.1:jar",
        "//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/7.2.1:jar",
        "//tools/analytics-library/shared:analytics-shared.jar",
        "//tools/analytics-library/tracker:analytics-tracker.jar",
        "//tools/base/annotations:studio.android.sdktools.android-annotations.jar",
        "//tools/base/common:studio.android.sdktools.common.jar",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
    ],
)

coverage_java_test(
    name = "integration_test",
    data = [":updater_deploy.jar"],
    tags = ["no_test_windows"],  # b/77288863
    test_class = "com.android.studio.updater.StudioPatchUpdaterIntegrationTest",
    runtime_deps = [":intellij.platform.updater_testlib"],
)
