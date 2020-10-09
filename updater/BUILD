load("//tools/base/bazel:utils.bzl", "select_project")

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
