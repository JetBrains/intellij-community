exports_files(["kotlin_known_problems.txt"])

java_binary(
    name = "BazelAntLauncher",
    srcs = ["src/main/java/com/android/tools/BazelAntLauncher.java"],
    data = ["//tools/idea/lib/ant/lib:all-libs"],
    main_class = "com.android.tools.BazelAntLauncher",
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/lib/ant/lib:ant-launcher"],
)

# A test for logging sizes of Android Studio binaries created
# from the //tools/idea:android-studio rule, which references
# the BazelAntLauncher rule.
java_test(
    name = "AndroidStudioBundleTest",
    srcs = ["src/test/java/com/android/tools/AndroidStudioBundleTest.java"],
    data = [
        "//tools/idea:android-studio.mac.zip",
        "//tools/idea:android-studio.tar.gz",
        "//tools/idea:android-studio.win.zip",
        "//tools/idea:android-studio.win32.zip",
    ],
    jvm_flags = ["-Dtest.suite.jar=AndroidStudioBundleTest.jar"],
    tags = [
        "manual",  # b/144578907
        "no_windows",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        "//tools/base/annotations",
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "KotlinBinaryCompatibilityTest",
    srcs = ["src/test/java/com/android/tools/KotlinBinaryCompatibilityTest.java"],
    data = [
        "kotlin_known_problems.txt",
        "//prebuilts/tools/common/intellij-plugin-verifier:verifier-cli-1.191-all.jar",
        "//tools/idea:android-studio.tar.gz",
    ],
    tags = [
        "manual",  # b/144578907
        "no_test_mac",  # TODO: we run out of file handles on Mac, double-check whether 'sysctl kern.maxfiles' is sane
        "no_windows",
    ],
    deps = [
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/idea/.idea/libraries:commons-compress",
    ],
)
