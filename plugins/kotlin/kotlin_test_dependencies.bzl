load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")
load("@bazel_skylib//lib:modules.bzl", "modules")
load("@bazel_features//:features.bzl", "bazel_features")

# do not forget to call `bazel mod tidy` in community directory
# to automatically update repositories list in community/MODULE.bazel
def _kotlin_test_deps_impl(ctx):
    kotlinCompilerCliVersion = "2.3.0-dev-1719"
    kotlincKotlinJpsPluginTestsVersion = "2.1.21"

    http_file(
        name = "kotlin_test_deps_annotations",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar",
        sha256 = "2037be378980d3ba9333e97955f3b2cde392aa124d04ca73ce2eee6657199297",
        downloaded_file_path = "annotations.jar",
    )

    http_file(
        name = "kotlin_test_deps_compose-compiler-plugin-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/{0}/compose-compiler-plugin-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "c89e944356112e8b1265311e687235d8aeaaa21535155cabb9377052db73bb18",
        downloaded_file_path = "compose-compiler-plugin-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_js-ir-runtime-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/js-ir-runtime-for-ide/{0}/js-ir-runtime-for-ide-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
        sha256 = "ce7dfdb29f2fcd1818d6096683853a0b5150751c0181cc59c8e366f766e39369",
        downloaded_file_path = "js-ir-runtime-for-ide.klib",
    )

    http_file(
        name = "kotlin_test_deps_jsr305",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
        sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
        downloaded_file_path = "jsr305.jar",
    )

    http_file(
        name = "kotlin_test_deps_junit",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/junit/junit/3.8.2/junit-3.8.2.jar",
        sha256 = "ecdcc08183708ea3f7b0ddc96f19678a0db8af1fb397791d484aed63200558b0",
        downloaded_file_path = "junit.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-annotations-jvm",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-annotations-jvm/{0}/kotlin-annotations-jvm-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "6054230599c840c69af3657c292b768f2b585c25fba91d26cd5ad8c992757b03",
        downloaded_file_path = "kotlin-annotations-jvm.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-compiler-testdata-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler-testdata-for-ide/{0}/kotlin-compiler-testdata-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "007ee2b096795db7a5eb19dd20bc1ae3f9c1c414057672f2d3947e9a53cda38f",
        downloaded_file_path = "kotlin-compiler-testdata-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-compiler",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler/{0}/kotlin-compiler-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "541d349e7c080a9d8ead41f2cf2a93c0f5c5af1a82abe020184251b8a6fbf6bf",
        downloaded_file_path = "kotlin-compiler.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-daemon",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-daemon/{0}/kotlin-daemon-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "1e9cf03ec2fda391bf7aa20da56806550313e144b5c7ff65362cc028eef7b43e",
        downloaded_file_path = "kotlin-daemon.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-dist-for-ide-increment-compilation",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/2.2.0/kotlin-dist-for-ide-2.2.0.jar",
        sha256 = "efe04515b3c45083ad8119249bef00d540a8ba9f10b4e0fa93833e10f47f2f9f",
        downloaded_file_path = "kotlin-dist-for-ide-increment-compilation-2.2.0.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-dist-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/{0}/kotlin-dist-for-ide-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
        sha256 = "afc456c6ff50abb192624f4424324d7c9a1c927fcc03896b93b08ad1f0800a46",
        downloaded_file_path = "kotlin-dist-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-dom-api-compat",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dom-api-compat/{0}/kotlin-dom-api-compat-{0}.klib".format(kotlinCompilerCliVersion),
        sha256 = "f9f482fa410366d4c11c5f9d815e3538f0fea45faa395091ee608342c91005df",
        downloaded_file_path = "kotlin-dom-api-compat.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-jps-plugin-classpath",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-classpath/{0}/kotlin-jps-plugin-classpath-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
        sha256 = "c05e38ca6de3cfdb77b315c1488e0e860082670642592b897d93e41ce0ffb0ac",
        downloaded_file_path = "kotlin-jps-plugin-classpath.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-jps-plugin-testdata-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/{0}/kotlin-jps-plugin-testdata-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "06bd1edb1a7ad1313a61b73dfcf94c8eab7a63c58efd26afc2d6937a11c4222c",
        downloaded_file_path = "kotlin-jps-plugin-testdata-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-reflect",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "d6426f342f6f41e6271f117a770316c0351d39225f01a669d9f5d82f8fdfe866",
        downloaded_file_path = "kotlin-reflect.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-reflect-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}-sources.jar".format(kotlinCompilerCliVersion),
        sha256 = "6e747a354e1b53571ccbdffba29f6c1033eea5983ac051038a3854825982ee81",
        downloaded_file_path = "kotlin-reflect-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-script-runtime",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-script-runtime/{0}/kotlin-script-runtime-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "5a5f24b0a8a5062dd98a07ad373fbbdfe2acdbb6531cc7438ea52715c734217d",
        downloaded_file_path = "kotlin-script-runtime.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-common",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-common/{0}/kotlin-scripting-common-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "c7e40e552739edf8e8bcd6c3ce3d0f07ccde25cbdce88dc387e58faf35273d4c",
        downloaded_file_path = "kotlin-scripting-common.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-compiler",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler/{0}/kotlin-scripting-compiler-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "6d8f04ea0d1253ba8d662de242e222d86f3a6faecab0ab517fc2406eaebfd220",
        downloaded_file_path = "kotlin-scripting-compiler.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-compiler-impl",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/{0}/kotlin-scripting-compiler-impl-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "02fc8af7f2d4c016b52c69cca56eb3d83d40070002608e32f01b4847c6afc9c5",
        downloaded_file_path = "kotlin-scripting-compiler-impl.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-jvm",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-jvm/{0}/kotlin-scripting-jvm-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "698a42f23395bf745cbaf4d925a8c5f8f2cf7f4efebc580f3ad024e31eac80c7",
        downloaded_file_path = "kotlin-scripting-jvm.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "110dc51e861ebffb8a8d85ac316d0e7c17cb37722ed6af21e1e303ea1a0dd5f4",
        downloaded_file_path = "kotlin-stdlib.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-170",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.7.0/kotlin-stdlib-1.7.0.jar",
        sha256 = "aa88e9625577957f3249a46cb6e166ee09b369e600f7a11d148d16b0a6d87f05",
        downloaded_file_path = "kotlin-stdlib-1.7.0.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-170-sources",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.7.0/kotlin-stdlib-1.7.0-sources.jar",
        sha256 = "2176274ecf922fffdd9a7eeec18f5e3a69f7ed53dadb5add3c9a706560ac9d7f",
        downloaded_file_path = "kotlin-stdlib-1.7.0-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-all",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-all.jar".format(kotlinCompilerCliVersion),
        sha256 = "58f826dba2b9fcde83d9ead93f3bc3e3b4c04f929dce2ecdbaa8cf0a7a74b802",
        downloaded_file_path = "kotlin-stdlib-all.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-common",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-common/1.9.22/kotlin-stdlib-common-1.9.22.jar",
        sha256 = "60b53a3fc0ed19ff5568ad54372f102f51109b7480417e93c8f3418ae4f73188",
        downloaded_file_path = "kotlin-stdlib-common.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-common-170-sources",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-common/1.7.0/kotlin-stdlib-common-1.7.0-sources.jar",
        sha256 = "406ecfb22a278ef80b642196d572eda4daebeed67b88474c86b39265288fba00",
        downloaded_file_path = "kotlin-stdlib-common-1.7.0-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-common-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-common-sources.jar".format(kotlinCompilerCliVersion),
        sha256 = "fefa343138581024f783262a74ca7a832e5b6a00fd8c82b073212fc1d7e13f94",
        downloaded_file_path = "kotlin-stdlib-common-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk7",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "dd9800c71111d7709025fe7e4adfea17913eaf54e7109b896075f4fdcaeb1766",
        downloaded_file_path = "kotlin-stdlib-jdk7.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk7-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}-sources.jar".format(kotlinCompilerCliVersion),
        sha256 = "2534c8908432e06de73177509903d405b55f423dd4c2f747e16b92a2162611e6",
        downloaded_file_path = "kotlin-stdlib-jdk7-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk8",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "b58967f625f52e8924c21d727fa060ae054fcda4f7cf6c79d1087c488e07a935",
        downloaded_file_path = "kotlin-stdlib-jdk8.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk8-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}-sources.jar".format(kotlinCompilerCliVersion),
        sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
        downloaded_file_path = "kotlin-stdlib-jdk8-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-js",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-js/{0}/kotlin-stdlib-js-{0}.klib".format(kotlinCompilerCliVersion),
        sha256 = "c359914e2be06145369fb968a87369a4b63055533b8c383ac40b2896e8a7e281",
        downloaded_file_path = "kotlin-stdlib-js.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-js-legacy",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-js/1.9.22/kotlin-stdlib-js-1.9.22.jar",
        sha256 = "f89136086e9cc9d01c4f629093b2447289b8ff3de11cb58b2a1c92483a3dc7f5",
        downloaded_file_path = "kotlin-stdlib-js-1.9.22.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-legacy",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/1.9.22/kotlin-stdlib-1.9.22.jar",
        sha256 = "6abe146c27864138b874ccccfe5f534e3eb923c99a1b7b5d45494ee5694f3e0a",
        downloaded_file_path = "kotlin-stdlib-1.9.22.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-project-wizard-default",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.0/kotlin-stdlib-1.9.0.jar",
        sha256 = "35aeffbe2db5aa446072cee50fcee48b7fa9e2fc51ca37c0cc7d7d0bc39d952e",
        downloaded_file_path = "kotlin-stdlib-1.9.0.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-sources.jar".format(kotlinCompilerCliVersion),
        sha256 = "c94a0592d67fdbd91f6a8c1b46de0d27b1bd39e01e67516342118ea586722f99",
        downloaded_file_path = "kotlin-stdlib-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-wasm-js",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/{0}/kotlin-stdlib-wasm-js-{0}.klib".format(kotlinCompilerCliVersion),
        sha256 = "d94d859ab7b1951960b36a3dac2ce96ebe1071e1b04f7ef10dea4a8960e7e1c2",
        downloaded_file_path = "kotlin-stdlib-wasm-js.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-wasm-wasi",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/{0}/kotlin-stdlib-wasm-wasi-{0}.klib".format(kotlinCompilerCliVersion),
        sha256 = "6ec470575cc0891d34dc22055d9501852bf46b24f4d0bcf30fc1dadf5e063552",
        downloaded_file_path = "kotlin-stdlib-wasm-wasi.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test/{0}/kotlin-test-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "501fa3941da13654dffe1cd67719ca7b36f17aa84799efa6637b6e75e0ae03e5",
        downloaded_file_path = "kotlin-test.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test-js",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-js/{0}/kotlin-test-js-{0}.klib".format(kotlinCompilerCliVersion),
        sha256 = "549d748c1b4cb94d615b0f4815add636ac9c604d87955109d80bdea2659ed389",
        downloaded_file_path = "kotlin-test-js.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test-junit",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-junit/{0}/kotlin-test-junit-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "7fc29e04f81da672d5ffb22084ae1b462ed1bcc0c636da7eaf3e1891d9cfe7dd",
        downloaded_file_path = "kotlin-test-junit.jar",
    )

    http_file(
        name = "kotlin_test_deps_parcelize-compiler-plugin-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/{0}/parcelize-compiler-plugin-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "0cae486ce1cd891e3def37e91b9f286384c7666972b30d86e4a670bb5179f1f0",
        downloaded_file_path = "parcelize-compiler-plugin-for-ide.jar",
    )

    # https://bazel.build/external/extension#specify_reproducibility
    return modules.use_all_repos(ctx, reproducible=True)

kotlin_test_deps = module_extension(_kotlin_test_deps_impl)

