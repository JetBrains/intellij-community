load("@bazel_skylib//lib:modules.bzl", "modules")
load("@bazel_features//:features.bzl", "bazel_features")

_files = []
def download_file(name, url, sha256):
    _files.append(struct(name = name, url = url, sha256 = sha256))

download_file(
  name = "kotlinx-coroutines-core-1.10.2.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.10.2/kotlinx-coroutines-core-1.10.2.jar",
  sha256 = "319b653009d49c70982f98df29cc84fc7025b092cb0571c8e7532e3ad4366dae",
)

download_file(
  name = "kotlinx-coroutines-core-jvm-1.10.2.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.2/kotlinx-coroutines-core-jvm-1.10.2.jar",
  sha256 = "5ca175b38df331fd64155b35cd8cae1251fa9ee369709b36d42e0a288ccce3fd",
)

download_file(
    name = "annotations.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar",
    sha256 = "2037be378980d3ba9333e97955f3b2cde392aa124d04ca73ce2eee6657199297",
)

download_file(
    name = "compose-compiler-plugin-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/2.3.0-dev-781/compose-compiler-plugin-for-ide-2.3.0-dev-781.jar",
    sha256 = "dc2e64460fcdb3c174a860aeb2e738ba5aff9bb6eb642b21630d9b052b4b28d5",
)

download_file(
    name = "js-ir-runtime-for-ide.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/js-ir-runtime-for-ide/2.1.21/js-ir-runtime-for-ide-2.1.21.klib",
    sha256 = "ce7dfdb29f2fcd1818d6096683853a0b5150751c0181cc59c8e366f766e39369",
)

download_file(
    name = "jsr305.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
    sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
)

download_file(
    name = "junit.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/junit/junit/3.8.2/junit-3.8.2.jar",
    sha256 = "ecdcc08183708ea3f7b0ddc96f19678a0db8af1fb397791d484aed63200558b0",
)

download_file(
    name = "kotlin-annotations-jvm.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-annotations-jvm/2.3.0-dev-781/kotlin-annotations-jvm-2.3.0-dev-781.jar",
    sha256 = "6054230599c840c69af3657c292b768f2b585c25fba91d26cd5ad8c992757b03",
)

download_file(
    name = "kotlin-compiler-testdata-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler-testdata-for-ide/2.3.0-dev-781/kotlin-compiler-testdata-for-ide-2.3.0-dev-781.jar",
    sha256 = "fc5fcc92dd6bcbf1d49cc5dc1442cb68c716926f42303f128b0649bab8de409a",
)

download_file(
    name = "kotlin-compiler.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler/2.3.0-dev-781/kotlin-compiler-2.3.0-dev-781.jar",
    sha256 = "0601d64884f868eaac69b7e0a5aad365ffa8c043d3c8907a883f480b672e1539",
)

download_file(
    name = "kotlin-daemon.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-daemon/2.3.0-dev-781/kotlin-daemon-2.3.0-dev-781.jar",
    sha256 = "7ce0367dd82e5d1233eda5a547a77c478d9e09cd2b2551a664162166f5281678",
)

download_file(
    name = "kotlin-dist-for-ide-increment-compilation-2.2.0.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/2.2.0/kotlin-dist-for-ide-2.2.0.jar",
    sha256 = "efe04515b3c45083ad8119249bef00d540a8ba9f10b4e0fa93833e10f47f2f9f",
)

download_file(
    name = "kotlin-dist-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/2.1.21/kotlin-dist-for-ide-2.1.21.jar",
    sha256 = "afc456c6ff50abb192624f4424324d7c9a1c927fcc03896b93b08ad1f0800a46",
)

download_file(
    name = "kotlin-dom-api-compat.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dom-api-compat/2.3.0-dev-781/kotlin-dom-api-compat-2.3.0-dev-781.klib",
    sha256 = "bd9b8cd1024f6d6072af12092659e9b3dfaeeb48d3a29e1b9a49951d9d8e3e47",
)

download_file(
    name = "kotlin-jps-plugin-classpath.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-classpath/2.1.21/kotlin-jps-plugin-classpath-2.1.21.jar",
    sha256 = "c05e38ca6de3cfdb77b315c1488e0e860082670642592b897d93e41ce0ffb0ac",
)

download_file(
    name = "kotlin-jps-plugin-testdata-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/2.3.0-dev-781/kotlin-jps-plugin-testdata-for-ide-2.3.0-dev-781.jar",
    sha256 = "06bd1edb1a7ad1313a61b73dfcf94c8eab7a63c58efd26afc2d6937a11c4222c",
)

download_file(
    name = "kotlin-reflect.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/2.3.0-dev-781/kotlin-reflect-2.3.0-dev-781.jar",
    sha256 = "0ca0be3aea64a8136060bbc469cc9bd6a48511372a26beab57a289f8159f8cb2",
)

download_file(
    name = "kotlin-reflect-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/2.3.0-dev-781/kotlin-reflect-2.3.0-dev-781-sources.jar",
    sha256 = "056b624975f90874c6f8ed4562ae0219ca85f836537c4fc1201f1cacc04daf12",
)

download_file(
    name = "kotlin-script-runtime.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-script-runtime/2.3.0-dev-781/kotlin-script-runtime-2.3.0-dev-781.jar",
    sha256 = "1d4d5e1f591f460dfd8260caf3e6e4eb79933c5a36ade25a9657f98c06cb7c66",
)

download_file(
    name = "kotlin-scripting-common.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-common/2.3.0-dev-781/kotlin-scripting-common-2.3.0-dev-781.jar",
    sha256 = "107eb2fffc7c83b2a1fe7cf3a452f40cc7730f4577415f101be5d414d7c27a80",
)

download_file(
    name = "kotlin-scripting-compiler.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler/2.3.0-dev-781/kotlin-scripting-compiler-2.3.0-dev-781.jar",
    sha256 = "6f37b793161ae0df9305ddd20815f4769ac1a52a12d111f1e4684d2c587d4f12",
)

download_file(
    name = "kotlin-scripting-compiler-impl.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/2.3.0-dev-781/kotlin-scripting-compiler-impl-2.3.0-dev-781.jar",
    sha256 = "ce03594fefc4af4acfdab5c0b151796fc751aff12ae5cee93e3b97b0485270b1",
)

download_file(
    name = "kotlin-scripting-jvm.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-jvm/2.3.0-dev-781/kotlin-scripting-jvm-2.3.0-dev-781.jar",
    sha256 = "0e1045a1d9a8e8c3d703a433ef4ecc0f54d0684c75be3d1f7631468bdbe64565",
)

download_file(
    name = "kotlin-stdlib.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.3.0-dev-781/kotlin-stdlib-2.3.0-dev-781.jar",
    sha256 = "40a10b116c6af6810c300ccf435696ce22c2f2c0760f70d01dfc60788ded5f78",
)

download_file(
    name = "kotlin-stdlib-1.7.0.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.7.0/kotlin-stdlib-1.7.0.jar",
    sha256 = "aa88e9625577957f3249a46cb6e166ee09b369e600f7a11d148d16b0a6d87f05",
)

download_file(
    name = "kotlin-stdlib-1.7.0-sources.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.7.0/kotlin-stdlib-1.7.0-sources.jar",
    sha256 = "2176274ecf922fffdd9a7eeec18f5e3a69f7ed53dadb5add3c9a706560ac9d7f",
)

download_file(
    name = "kotlin-stdlib-all.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.3.0-dev-781/kotlin-stdlib-2.3.0-dev-781-all.jar",
    sha256 = "0e8ae650fed90cf3bcb73b2053763c171280b390a8bc2102f5d012cc288e0ec3",
)

download_file(
    name = "kotlin-stdlib-common.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-common/1.9.22/kotlin-stdlib-common-1.9.22.jar",
    sha256 = "60b53a3fc0ed19ff5568ad54372f102f51109b7480417e93c8f3418ae4f73188",
)

download_file(
    name = "kotlin-stdlib-common-1.7.0-sources.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-common/1.7.0/kotlin-stdlib-common-1.7.0-sources.jar",
    sha256 = "406ecfb22a278ef80b642196d572eda4daebeed67b88474c86b39265288fba00",
)

download_file(
    name = "kotlin-stdlib-common-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.3.0-dev-781/kotlin-stdlib-2.3.0-dev-781-common-sources.jar",
    sha256 = "61818d31f351109e1d8a5733c88dd34053c6252c4de184e288740be319c2e77b",
)

download_file(
    name = "kotlin-stdlib-jdk7.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/2.3.0-dev-781/kotlin-stdlib-jdk7-2.3.0-dev-781.jar",
    sha256 = "1303809895af87415d9f9b1cccd9022911927fd8d1a19efd153c888eff30f291",
)

download_file(
    name = "kotlin-stdlib-jdk7-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/2.3.0-dev-781/kotlin-stdlib-jdk7-2.3.0-dev-781-sources.jar",
    sha256 = "2534c8908432e06de73177509903d405b55f423dd4c2f747e16b92a2162611e6",
)

download_file(
    name = "kotlin-stdlib-jdk8.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/2.3.0-dev-781/kotlin-stdlib-jdk8-2.3.0-dev-781.jar",
    sha256 = "ff39a11b97d9cdaebae5b50e569d0df591c673906bbdcbe725d1486bd15e54bb",
)

download_file(
    name = "kotlin-stdlib-jdk8-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/2.3.0-dev-781/kotlin-stdlib-jdk8-2.3.0-dev-781-sources.jar",
    sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
)

download_file(
    name = "kotlin-stdlib-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-js/2.3.0-dev-781/kotlin-stdlib-js-2.3.0-dev-781.klib",
    sha256 = "34e5d2e7f9ab1225f0b299665c80350040d6735e26409010be60fb21d3cb1b3e",
)

download_file(
    name = "kotlin-stdlib-js-1.9.22.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-js/1.9.22/kotlin-stdlib-js-1.9.22.jar",
    sha256 = "f89136086e9cc9d01c4f629093b2447289b8ff3de11cb58b2a1c92483a3dc7f5",
)

download_file(
    name = "kotlin-stdlib-1.9.22.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/1.9.22/kotlin-stdlib-1.9.22.jar",
    sha256 = "6abe146c27864138b874ccccfe5f534e3eb923c99a1b7b5d45494ee5694f3e0a",
)

download_file(
    name = "kotlin-stdlib-project-wizard-default.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.0/kotlin-stdlib-1.9.0.jar",
    sha256 = "35aeffbe2db5aa446072cee50fcee48b7fa9e2fc51ca37c0cc7d7d0bc39d952e",
)

download_file(
    name = "kotlin-stdlib-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.3.0-dev-781/kotlin-stdlib-2.3.0-dev-781-sources.jar",
    sha256 = "3fc53b291f145d2f90b314b2e4be841169f6698f2f76c287d27eec993fc07238",
)

download_file(
    name = "kotlin-stdlib-wasm-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/2.3.0-dev-781/kotlin-stdlib-wasm-js-2.3.0-dev-781.klib",
    sha256 = "3910d6d1d4edfad59235692395f74f5c94f48ae23948223fe3588292c65c6ed8",
)

download_file(
    name = "kotlin-stdlib-wasm-wasi.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/2.3.0-dev-781/kotlin-stdlib-wasm-wasi-2.3.0-dev-781.klib",
    sha256 = "5ff25e82ebe53598d38a4a599e88620e8e6ec2ae6f7762d14071327faf17b7cb",
)

download_file(
    name = "kotlin-test.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test/2.3.0-dev-781/kotlin-test-2.3.0-dev-781.jar",
    sha256 = "526d6d0063885dd2a6610a3bdb3ed31d9142a798f96f707dbb88f4a4af04232e",
)

download_file(
    name = "kotlin-test-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-js/2.3.0-dev-781/kotlin-test-js-2.3.0-dev-781.klib",
    sha256 = "27e24a255044e762e6eefee2c65655be71ff7f7bc91d3d80591a72c4efda8516",
)

download_file(
    name = "kotlin-test-junit.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-junit/2.3.0-dev-781/kotlin-test-junit-2.3.0-dev-781.jar",
    sha256 = "a530874740f82695d7bdc1a92ba1c03a943551612acc4a93af209dbd8f48fdb5",
)

download_file(
    name = "parcelize-compiler-plugin-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/2.3.0-dev-781/parcelize-compiler-plugin-for-ide-2.3.0-dev-781.jar",
    sha256 = "9a3f11b5fc887b0f4d6866b8acc3fbb4bfc68c2070d86ec41368cb7981115ba3",
)

all_test_dep_targets = ["@kotlin_test_deps//:" + t.name for t in _files]

def _kotlin_test_deps_impl(repository_ctx):
    downloads = []
    for file in _files:
        downloads.append(repository_ctx.download(
           url = file.url,
           output = file.name,
           sha256 = file.sha256,
           # Do not download serially, it's too slow
           # https://github.com/bazelbuild/bazel/issues/19674
           block = False,
        ))
    for d in downloads:
        d.wait()

    repository_ctx.file(
        "BUILD",
        "package(default_visibility = ['//visibility:public'])\n" +
        "exports_files([" + ", ".join(["'" + f.name + "'" for f in _files]) + "])\n"
    )

kotlin_test_deps = repository_rule(implementation = _kotlin_test_deps_impl)
