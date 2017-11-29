java_binary(
    name = "BazelAntLauncher",
    srcs = ["src/main/java/com/android/tools/BazelAntLauncher.java"],
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
        "ij2017.3",  # TODO: Disabled on all targets due to IJ2017.3 merge
        "managed",
        "no_linux",
        "no_mac",
        "no_test_mac",  # b/69173266 Time out frequently when run on mac.
        "no_windows",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        "//tools/base/annotations",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)
