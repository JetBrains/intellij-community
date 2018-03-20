java_binary(
    name = "BazelAntLauncher",
    srcs = ["src/main/java/com/android/tools/BazelAntLauncher.java"],
    data = ["//tools/idea/lib/ant/lib:all-libs"],
    main_class = "com.android.tools.BazelAntLauncher",
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/lib/ant/lib:ant-launcher"],
)

filegroup(
    name = "ant_prebuilts",
    srcs = glob(["build-scripts-deps/*.jar"]),
    visibility = ["//visibility:public"],
)
