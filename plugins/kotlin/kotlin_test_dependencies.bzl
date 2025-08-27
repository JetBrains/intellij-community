load("@bazel_skylib//lib:modules.bzl", "modules")
load("@bazel_features//:features.bzl", "bazel_features")

_files = []
def download_file(name, url, sha256):
    _files.append(struct(name = name, url = url, sha256 = sha256))

kotlinCompilerCliVersion = "2.3.0-dev-5105"
kotlincKotlinJpsPluginTestsVersion = "2.2.0"

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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/{0}/compose-compiler-plugin-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "654b60b7db6dff704cf3926b46da3e6fd7ad60a0802a26fa73c6b52b2b4010fe",
)

download_file(
    name = "js-ir-runtime-for-ide.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/js-ir-runtime-for-ide/{0}/js-ir-runtime-for-ide-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "2418eb1ebd6998c2879730cf0d41409269eb0a40df4e5744a912893079cec139",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-annotations-jvm/{0}/kotlin-annotations-jvm-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "6054230599c840c69af3657c292b768f2b585c25fba91d26cd5ad8c992757b03",
)

download_file(
    name = "kotlin-compiler-testdata-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler-testdata-for-ide/{0}/kotlin-compiler-testdata-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "6896f810e0bf879e07769e3f1e326ba90960c54052eae8c4ac0ad6ccddbe41d2",
)

download_file(
    name = "kotlin-compiler.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler/{0}/kotlin-compiler-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "ef2906d059e896e475930a9b38d0c31de06d864784c0e789006d3f929aa752e4",
)

download_file(
    name = "kotlin-daemon.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-daemon/{0}/kotlin-daemon-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "8b07742eaa0c4a09b0657eb84ec3262d4a6db6113a3a1913f7a7fc1f895b29d7",
)

download_file(
    name = "kotlin-dist-for-ide-increment-compilation-2.2.0.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/2.2.0/kotlin-dist-for-ide-2.2.0.jar",
    sha256 = "efe04515b3c45083ad8119249bef00d540a8ba9f10b4e0fa93833e10f47f2f9f",
)

download_file(
    name = "kotlin-dist-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/{0}/kotlin-dist-for-ide-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "efe04515b3c45083ad8119249bef00d540a8ba9f10b4e0fa93833e10f47f2f9f",
)

download_file(
    name = "kotlin-dom-api-compat.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dom-api-compat/{0}/kotlin-dom-api-compat-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "cf25a6a568780958e6e61cdf4760371812969f843ed30ed5d0f9a74476ea9bbf",
)

download_file(
    name = "kotlin-jps-plugin-classpath.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-classpath/{0}/kotlin-jps-plugin-classpath-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "61002bb21cc3c512bd7c9d532f3932e33e019e58d554bc4d3c208ebec5c11284",
)

download_file(
    name = "kotlin-jps-plugin-testdata-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/{0}/kotlin-jps-plugin-testdata-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "06bd1edb1a7ad1313a61b73dfcf94c8eab7a63c58efd26afc2d6937a11c4222c",
)

download_file(
    name = "kotlin-reflect.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "bf3681549c498cd85a80cebcde93c6c4965104678b2161a7e7ddc8662cc6c91d",
)

download_file(
    name = "kotlin-reflect-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}-sources.jar".format(kotlinCompilerCliVersion),
    sha256 = "5612d284b6d76fa2be9329b953d68bdbce7d231de770fcf77abd48c04e0c8573",
)

download_file(
    name = "kotlin-script-runtime.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-script-runtime/{0}/kotlin-script-runtime-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "d973a05cc07b93d35b4b95d41918a4153dd7d03750bd24d65813530ece33c4a8",
)

download_file(
    name = "kotlin-scripting-common.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-common/{0}/kotlin-scripting-common-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "9c93558d588248124a032fcfeaecb97dfad605dec88d9195d2e4b0e2ce09e1a2",
)

download_file(
    name = "kotlin-scripting-compiler.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler/{0}/kotlin-scripting-compiler-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "9fa8280e291cbc3ede3aee98675c0a741bc3e3f7d4645ea851f30d248cd8aa63",
)

download_file(
    name = "kotlin-scripting-compiler-impl.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/{0}/kotlin-scripting-compiler-impl-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "d9dbd82902e46e20d03a794c34dcadea0d9dd70399e9c9fa3c93518e880cdb0b",
)

download_file(
    name = "kotlin-scripting-jvm.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-jvm/{0}/kotlin-scripting-jvm-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "8b0f70b8e1e8f4c03875a7113c9a8c9c850fd95f366dc3d29df87780ccf67e9e",
)

download_file(
    name = "kotlin-stdlib.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "ff7fa72879c3cac0f9f7abace90ac897b7ae6cb7c24f513db3b98fdd6cc4962f",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-all.jar".format(kotlinCompilerCliVersion),
    sha256 = "9f4e93210a1455a56221638dcb0d36a25b7a0d7be12c4432d7d0693de09879c1",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-common-sources.jar".format(kotlinCompilerCliVersion),
    sha256 = "1394a35ec10951ced6a4bc1b9458216eaab130791b67172ec893023f7f18914e",
)

download_file(
    name = "kotlin-stdlib-jdk7.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "2c9083c2e8efd1cc37a064854dd7dc705dd62b207a2761568e05f94b183769e9",
)

download_file(
    name = "kotlin-stdlib-jdk7-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}-sources.jar".format(kotlinCompilerCliVersion),
    sha256 = "2534c8908432e06de73177509903d405b55f423dd4c2f747e16b92a2162611e6",
)

download_file(
    name = "kotlin-stdlib-jdk8.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "99462074820986e80c57d844d214ac1f77f250cf787cbf5ab0caa1943bd366d4",
)

download_file(
    name = "kotlin-stdlib-jdk8-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}-sources.jar".format(kotlinCompilerCliVersion),
    sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
)

download_file(
    name = "kotlin-stdlib-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-js/{0}/kotlin-stdlib-js-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "46b2947a0b5e0478240609cfc6e86e606abe3d68cee5f02b577cafb6f417531d",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-sources.jar".format(kotlinCompilerCliVersion),
    sha256 = "47cbf62633d18166a17a378df480ee878551374dfd25e02a959ff01287b2592d",
)

download_file(
    name = "kotlin-stdlib-wasm-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/{0}/kotlin-stdlib-wasm-js-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "af9519b7a2937e28ec76f3fdf74ef8cfeb1d039d655d387e46e8b90e6edc951e",
)

download_file(
    name = "kotlin-stdlib-wasm-wasi.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/{0}/kotlin-stdlib-wasm-wasi-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "1114ffa430dfaf6361fb7f80c0e9dfdba4e072869851d84e39a81f25ba77a107",
)

download_file(
    name = "kotlin-test.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test/{0}/kotlin-test-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "f3762e2479f5757d211d4b036271d3b430eb5c1221d080f9979ab8236f24d84f",
)

download_file(
    name = "kotlin-test-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-js/{0}/kotlin-test-js-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "0ae1a6e940835f4204e5d8c33906695ca84a36223670cae4a4e9ff715860580b",
)

download_file(
    name = "kotlin-test-junit.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-junit/{0}/kotlin-test-junit-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "f21ef543920d03dab126114f8ec09e3196520293d381fd552316e4e43b1e3fdd",
)

download_file(
    name = "parcelize-compiler-plugin-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/{0}/parcelize-compiler-plugin-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "742d24ff741d88ddf0b8d8198212d209188f8dd3e917ffe754156c96b09092c5",
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