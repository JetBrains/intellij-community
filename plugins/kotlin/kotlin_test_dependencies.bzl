load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")
load("@bazel_skylib//lib:modules.bzl", "modules")
load("@bazel_features//:features.bzl", "bazel_features")

# do not forget to call `bazel mod tidy` in community directory
# to automatically update repositories list in community/MODULE.bazel
def _kotlin_test_deps_impl(ctx):
    kotlinCompilerCliVersion = "2.3.0-dev-3276"
    kotlincKotlinJpsPluginTestsVersion = "2.2.0"

    http_file(
        name = "kotlin_test_deps_annotations",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar",
        sha256 = "2037be378980d3ba9333e97955f3b2cde392aa124d04ca73ce2eee6657199297",
        downloaded_file_path = "annotations.jar",
    )

    http_file(
        name = "kotlin_test_deps_compose-compiler-plugin-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/{0}/compose-compiler-plugin-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "5d0dbb0685b5f3dcd7d01e05c8a4e9bc0c41e344a364a0d9b57d86ed4f8de529",
        downloaded_file_path = "compose-compiler-plugin-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_js-ir-runtime-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/js-ir-runtime-for-ide/{0}/js-ir-runtime-for-ide-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
        sha256 = "2418eb1ebd6998c2879730cf0d41409269eb0a40df4e5744a912893079cec139",
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
        sha256 = "76ce2f00e2e0d84b2e2ef794d309fbb8040f46863f5ecc6f91b3aba5375cae6b",
        downloaded_file_path = "kotlin-compiler-testdata-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-compiler",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler/{0}/kotlin-compiler-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "e323d57efd25733355ee507a1955f9d4be2da51bae2301cea6d509dabfe3025f",
        downloaded_file_path = "kotlin-compiler.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-daemon",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-daemon/{0}/kotlin-daemon-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "c1d377b3d75687eebfa51340c13d857afec1e122ac02e99c3655529447f1a43b",
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
        sha256 = "efe04515b3c45083ad8119249bef00d540a8ba9f10b4e0fa93833e10f47f2f9f",
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
        sha256 = "61002bb21cc3c512bd7c9d532f3932e33e019e58d554bc4d3c208ebec5c11284",
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
        sha256 = "dad6f8f7ac56583024d28c70023d0d49626900b9e428a9a7d9b819015a82d8fa",
        downloaded_file_path = "kotlin-reflect.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-reflect-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}-sources.jar".format(kotlinCompilerCliVersion),
        sha256 = "833054e7c7a0271364688a23247f7f33c536a9c184dd6f6b6866089bbebc322d",
        downloaded_file_path = "kotlin-reflect-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-script-runtime",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-script-runtime/{0}/kotlin-script-runtime-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "4ad2647c5c1319fffebbfdfee7233d8cf54a322666e34029c1fb9ebc6394aa5c",
        downloaded_file_path = "kotlin-script-runtime.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-common",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-common/{0}/kotlin-scripting-common-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "9b8da74f286ce48d22a83b84a45c2c125eb6dbba92ae3829ba74934e2bfb5e39",
        downloaded_file_path = "kotlin-scripting-common.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-compiler",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler/{0}/kotlin-scripting-compiler-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "c5e19ea940d4465f4eff8f4ad4c769e5ab3af7ff89a1f1e6b27400d605bfd3c2",
        downloaded_file_path = "kotlin-scripting-compiler.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-compiler-impl",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/{0}/kotlin-scripting-compiler-impl-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "c8a94fdff062574abd46f022c177fc31fd4847fd41803df7afaca7cc1fffc33b",
        downloaded_file_path = "kotlin-scripting-compiler-impl.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-jvm",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-jvm/{0}/kotlin-scripting-jvm-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "0955af09fc2f4878fa3dd437fc5a0c3bc0583a0f53893333ef65e10b2d9d0083",
        downloaded_file_path = "kotlin-scripting-jvm.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "553a6b3a093e9aeba3e52329a0e5afa27d96b8345224e092dcb30e3da7a736b4",
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
        sha256 = "ce25c25339bf0eca88bb0c20791874498a9be7a0490fbe21eba939670611930e",
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
        sha256 = "4123936aadaea9a046b3f87743ba8f566d46497bcfcfc1bc20befe66da817623",
        downloaded_file_path = "kotlin-stdlib-common-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk7",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "790785ca13ba1e82ad9cff1cffe06b7f1fc1720c6bfbb25dc95c6076daa37e03",
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
        sha256 = "a2a2024e0d01303524f11f4b5d540054cf5a59ed39ffc089788c34731d55d352",
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
        sha256 = "722b6da6e3ef650fdbcbd9dfcbba1214ca467bfa4434c96578491cab10207faa",
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
        sha256 = "313965fc444022f54d8dbfbfd4681287f6dad4a24d967405991882a8773ef474",
        downloaded_file_path = "kotlin-stdlib-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-wasm-js",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/{0}/kotlin-stdlib-wasm-js-{0}.klib".format(kotlinCompilerCliVersion),
        sha256 = "6af68505cc92c547cb5cc430a11c107d34c087c81004e7dd6d43de71945b95bd",
        downloaded_file_path = "kotlin-stdlib-wasm-js.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-wasm-wasi",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/{0}/kotlin-stdlib-wasm-wasi-{0}.klib".format(kotlinCompilerCliVersion),
        sha256 = "67c0b9f064f70de456c6fab36624e2fbf0f07e6245a2fd6d1190411b0f78b73e",
        downloaded_file_path = "kotlin-stdlib-wasm-wasi.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test/{0}/kotlin-test-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "b260d5a6148b52ff2e0d662c63d51070d6f03b3384b6266299ff24144066ea08",
        downloaded_file_path = "kotlin-test.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test-js",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-js/{0}/kotlin-test-js-{0}.klib".format(kotlinCompilerCliVersion),
        sha256 = "3b0638a0953ed8d05fbf414c9699599bd7b5fc5c41882e60827e3d023c3dffa3",
        downloaded_file_path = "kotlin-test-js.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test-junit",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-junit/{0}/kotlin-test-junit-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "abef9e41d3f3505f6e9727f9e29a47d8c3aa06908c9b52c7946816d8e259faaf",
        downloaded_file_path = "kotlin-test-junit.jar",
    )

    http_file(
        name = "kotlin_test_deps_parcelize-compiler-plugin-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/{0}/parcelize-compiler-plugin-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
        sha256 = "2d366146f1a9069ed993708a0ea633f2cea8e903d0c89297ae07c92f11179252",
        downloaded_file_path = "parcelize-compiler-plugin-for-ide.jar",
    )

    # https://bazel.build/external/extension#specify_reproducibility
    return modules.use_all_repos(ctx, reproducible=True)

kotlin_test_deps = module_extension(_kotlin_test_deps_impl)

