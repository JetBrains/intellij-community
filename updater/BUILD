load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "select_project")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.updater",
    srcs = ["src"],
    iml_files = ["intellij.platform.updater.iml"],
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/platform/inspect:intellij.platform.inspect",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:jaxb-api",
        "//tools/idea/.idea/libraries:jaxb-runtime",
        "//tools/idea/.idea/libraries:javax.annotation-api",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/8.4.0:jar"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//prebuilts/tools/common/m2/repository/com/nothome/javaxdelta/2.0.1:jar",
    ],
)

java_binary(
    name = "updater",
    main_class = "com.intellij.updater.Runner",
    # This uses the jar rather than the iml_module to avoid packaging jdk langtools
    # TODO(b/124044861): switch to using the below java_binary with deploy_env once we are on a bazel that supports it.
    tags = select_project({
        "unb": ["manual"],
        "": [],
    }),
    visibility = ["//visibility:public"],
    runtime_deps = [
        ":intellij.platform.updater.jar",
        "//prebuilts/tools/common/m2/repository/com/nothome/javaxdelta/2.0.1:jar",
        "//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/8.4.0:jar",
        "//tools/adt/idea/studio-updater:intellij.android.updater.studio-updater.jar",  # Adds analytics to the updater
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
