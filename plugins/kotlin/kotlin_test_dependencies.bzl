load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")
load("@bazel_skylib//lib:modules.bzl", "modules")
load("@bazel_features//:features.bzl", "bazel_features")

# do not forget to call `bazel mod tidy` in community directory
# to automatically update repositories list in community/MODULE.bazel
def _kotlin_test_deps_impl(ctx):
    http_file(
        name = "kotlin_test_deps_annotations",
        url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar",
        sha256 = "2037be378980d3ba9333e97955f3b2cde392aa124d04ca73ce2eee6657199297",
        downloaded_file_path = "annotations.jar",
    )

    http_file(
        name = "kotlin_test_deps_compose-compiler-plugin-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/2.3.0-dev-781/compose-compiler-plugin-for-ide-2.3.0-dev-781.jar",
        sha256 = "dc2e64460fcdb3c174a860aeb2e738ba5aff9bb6eb642b21630d9b052b4b28d5",
        downloaded_file_path = "compose-compiler-plugin-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_js-ir-runtime-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/js-ir-runtime-for-ide/2.1.21/js-ir-runtime-for-ide-2.1.21.klib",
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
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-annotations-jvm/2.3.0-dev-781/kotlin-annotations-jvm-2.3.0-dev-781.jar",
        sha256 = "6054230599c840c69af3657c292b768f2b585c25fba91d26cd5ad8c992757b03",
        downloaded_file_path = "kotlin-annotations-jvm.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-compiler-testdata-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler-testdata-for-ide/2.3.0-dev-781/kotlin-compiler-testdata-for-ide-2.3.0-dev-781.jar",
        sha256 = "fc5fcc92dd6bcbf1d49cc5dc1442cb68c716926f42303f128b0649bab8de409a",
        downloaded_file_path = "kotlin-compiler-testdata-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-compiler",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler/2.3.0-dev-781/kotlin-compiler-2.3.0-dev-781.jar",
        sha256 = "0601d64884f868eaac69b7e0a5aad365ffa8c043d3c8907a883f480b672e1539",
        downloaded_file_path = "kotlin-compiler.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-daemon",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-daemon/2.3.0-dev-781/kotlin-daemon-2.3.0-dev-781.jar",
        sha256 = "7ce0367dd82e5d1233eda5a547a77c478d9e09cd2b2551a664162166f5281678",
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
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/2.1.21/kotlin-dist-for-ide-2.1.21.jar",
        sha256 = "afc456c6ff50abb192624f4424324d7c9a1c927fcc03896b93b08ad1f0800a46",
        downloaded_file_path = "kotlin-dist-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-dom-api-compat",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dom-api-compat/2.3.0-dev-781/kotlin-dom-api-compat-2.3.0-dev-781.klib",
        sha256 = "bd9b8cd1024f6d6072af12092659e9b3dfaeeb48d3a29e1b9a49951d9d8e3e47",
        downloaded_file_path = "kotlin-dom-api-compat.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-jps-plugin-classpath",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-classpath/2.1.21/kotlin-jps-plugin-classpath-2.1.21.jar",
        sha256 = "c05e38ca6de3cfdb77b315c1488e0e860082670642592b897d93e41ce0ffb0ac",
        downloaded_file_path = "kotlin-jps-plugin-classpath.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-jps-plugin-testdata-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/2.3.0-dev-781/kotlin-jps-plugin-testdata-for-ide-2.3.0-dev-781.jar",
        sha256 = "06bd1edb1a7ad1313a61b73dfcf94c8eab7a63c58efd26afc2d6937a11c4222c",
        downloaded_file_path = "kotlin-jps-plugin-testdata-for-ide.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-reflect",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/2.3.0-dev-781/kotlin-reflect-2.3.0-dev-781.jar",
        sha256 = "0ca0be3aea64a8136060bbc469cc9bd6a48511372a26beab57a289f8159f8cb2",
        downloaded_file_path = "kotlin-reflect.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-reflect-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/2.3.0-dev-781/kotlin-reflect-2.3.0-dev-781-sources.jar",
        sha256 = "056b624975f90874c6f8ed4562ae0219ca85f836537c4fc1201f1cacc04daf12",
        downloaded_file_path = "kotlin-reflect-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-script-runtime",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-script-runtime/2.3.0-dev-781/kotlin-script-runtime-2.3.0-dev-781.jar",
        sha256 = "1d4d5e1f591f460dfd8260caf3e6e4eb79933c5a36ade25a9657f98c06cb7c66",
        downloaded_file_path = "kotlin-script-runtime.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-common",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-common/2.3.0-dev-781/kotlin-scripting-common-2.3.0-dev-781.jar",
        sha256 = "107eb2fffc7c83b2a1fe7cf3a452f40cc7730f4577415f101be5d414d7c27a80",
        downloaded_file_path = "kotlin-scripting-common.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-compiler",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler/2.3.0-dev-781/kotlin-scripting-compiler-2.3.0-dev-781.jar",
        sha256 = "6f37b793161ae0df9305ddd20815f4769ac1a52a12d111f1e4684d2c587d4f12",
        downloaded_file_path = "kotlin-scripting-compiler.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-compiler-impl",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/2.3.0-dev-781/kotlin-scripting-compiler-impl-2.3.0-dev-781.jar",
        sha256 = "ce03594fefc4af4acfdab5c0b151796fc751aff12ae5cee93e3b97b0485270b1",
        downloaded_file_path = "kotlin-scripting-compiler-impl.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-scripting-jvm",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-jvm/2.3.0-dev-781/kotlin-scripting-jvm-2.3.0-dev-781.jar",
        sha256 = "0e1045a1d9a8e8c3d703a433ef4ecc0f54d0684c75be3d1f7631468bdbe64565",
        downloaded_file_path = "kotlin-scripting-jvm.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.3.0-dev-781/kotlin-stdlib-2.3.0-dev-781.jar",
        sha256 = "40a10b116c6af6810c300ccf435696ce22c2f2c0760f70d01dfc60788ded5f78",
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
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.3.0-dev-781/kotlin-stdlib-2.3.0-dev-781-all.jar",
        sha256 = "0e8ae650fed90cf3bcb73b2053763c171280b390a8bc2102f5d012cc288e0ec3",
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
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.3.0-dev-781/kotlin-stdlib-2.3.0-dev-781-common-sources.jar",
        sha256 = "61818d31f351109e1d8a5733c88dd34053c6252c4de184e288740be319c2e77b",
        downloaded_file_path = "kotlin-stdlib-common-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk7",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/2.3.0-dev-781/kotlin-stdlib-jdk7-2.3.0-dev-781.jar",
        sha256 = "1303809895af87415d9f9b1cccd9022911927fd8d1a19efd153c888eff30f291",
        downloaded_file_path = "kotlin-stdlib-jdk7.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk7-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/2.3.0-dev-781/kotlin-stdlib-jdk7-2.3.0-dev-781-sources.jar",
        sha256 = "2534c8908432e06de73177509903d405b55f423dd4c2f747e16b92a2162611e6",
        downloaded_file_path = "kotlin-stdlib-jdk7-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk8",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/2.3.0-dev-781/kotlin-stdlib-jdk8-2.3.0-dev-781.jar",
        sha256 = "ff39a11b97d9cdaebae5b50e569d0df591c673906bbdcbe725d1486bd15e54bb",
        downloaded_file_path = "kotlin-stdlib-jdk8.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-jdk8-sources",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/2.3.0-dev-781/kotlin-stdlib-jdk8-2.3.0-dev-781-sources.jar",
        sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
        downloaded_file_path = "kotlin-stdlib-jdk8-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-js",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-js/2.3.0-dev-781/kotlin-stdlib-js-2.3.0-dev-781.klib",
        sha256 = "34e5d2e7f9ab1225f0b299665c80350040d6735e26409010be60fb21d3cb1b3e",
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
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.3.0-dev-781/kotlin-stdlib-2.3.0-dev-781-sources.jar",
        sha256 = "3fc53b291f145d2f90b314b2e4be841169f6698f2f76c287d27eec993fc07238",
        downloaded_file_path = "kotlin-stdlib-sources.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-wasm-js",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/2.3.0-dev-781/kotlin-stdlib-wasm-js-2.3.0-dev-781.klib",
        sha256 = "3910d6d1d4edfad59235692395f74f5c94f48ae23948223fe3588292c65c6ed8",
        downloaded_file_path = "kotlin-stdlib-wasm-js.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-stdlib-wasm-wasi",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/2.3.0-dev-781/kotlin-stdlib-wasm-wasi-2.3.0-dev-781.klib",
        sha256 = "5ff25e82ebe53598d38a4a599e88620e8e6ec2ae6f7762d14071327faf17b7cb",
        downloaded_file_path = "kotlin-stdlib-wasm-wasi.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test/2.3.0-dev-781/kotlin-test-2.3.0-dev-781.jar",
        sha256 = "526d6d0063885dd2a6610a3bdb3ed31d9142a798f96f707dbb88f4a4af04232e",
        downloaded_file_path = "kotlin-test.jar",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test-js",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-js/2.3.0-dev-781/kotlin-test-js-2.3.0-dev-781.klib",
        sha256 = "27e24a255044e762e6eefee2c65655be71ff7f7bc91d3d80591a72c4efda8516",
        downloaded_file_path = "kotlin-test-js.klib",
    )

    http_file(
        name = "kotlin_test_deps_kotlin-test-junit",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-junit/2.3.0-dev-781/kotlin-test-junit-2.3.0-dev-781.jar",
        sha256 = "a530874740f82695d7bdc1a92ba1c03a943551612acc4a93af209dbd8f48fdb5",
        downloaded_file_path = "kotlin-test-junit.jar",
    )

    http_file(
        name = "kotlin_test_deps_parcelize-compiler-plugin-for-ide",
        url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/2.3.0-dev-781/parcelize-compiler-plugin-for-ide-2.3.0-dev-781.jar",
        sha256 = "9a3f11b5fc887b0f4d6866b8acc3fbb4bfc68c2070d86ec41368cb7981115ba3",
        downloaded_file_path = "parcelize-compiler-plugin-for-ide.jar",
    )

    # https://bazel.build/external/extension#specify_reproducibility
    return modules.use_all_repos(ctx, reproducible=True)

kotlin_test_deps = module_extension(_kotlin_test_deps_impl)

