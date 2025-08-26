load("@bazel_skylib//lib:modules.bzl", "modules")
load("@bazel_features//:features.bzl", "bazel_features")

_files = []
def download_file(name, url, sha256):
    _files.append(struct(name = name, url = url, sha256 = sha256))

kotlinCompilerCliVersion = "2.3.0-dev-4775"
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/{0}/compose-compiler-plugin-for-ide-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "50e8d1b53387b9b1353e6c349d168c432e115ba94efe1f9332dfe28ab31726f4",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-annotations-jvm/{0}/kotlin-annotations-jvm-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "6054230599c840c69af3657c292b768f2b585c25fba91d26cd5ad8c992757b03",
)

download_file(
    name = "kotlin-compiler-testdata-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler-testdata-for-ide/{0}/kotlin-compiler-testdata-for-ide-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "58a43df1e5892e4c98188a256cbc58daeaef5b585dd0a96b03889b3753770521",
)

download_file(
    name = "kotlin-compiler.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler/{0}/kotlin-compiler-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "cb2b7b0b82ad5b6ddc2e8c6c41c73e837a2b42e2abf64af64dda1b6173609790",
)

download_file(
    name = "kotlin-daemon.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-daemon/{0}/kotlin-daemon-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "efd0b911bd952c91a74acdf2861b74a61516b869cc9a22aa1858197a87a66c35",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dom-api-compat/{0}/kotlin-dom-api-compat-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "3ab4598b1c1b30ec9f381b2e962f8ba459072745602795961d501bb9531d4673",
)

download_file(
    name = "kotlin-jps-plugin-classpath.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-classpath/{0}/kotlin-jps-plugin-classpath-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "61002bb21cc3c512bd7c9d532f3932e33e019e58d554bc4d3c208ebec5c11284",
)

download_file(
    name = "kotlin-jps-plugin-testdata-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/{0}/kotlin-jps-plugin-testdata-for-ide-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "ac3db7b4adcf09f52ead90307b16df064486c42b5258d2419b0c9c03eb0bb2be",
)

download_file(
    name = "kotlin-reflect.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "230d91c2e410e3cfca3a4dc73d255455f62ff52aac091a33397a6e30bde91bf7",
)

download_file(
    name = "kotlin-reflect-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}-sources.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "e9719062ea18b7c10f231ee170306f95acf78c31b4f4592d32e8376ca3779f0b",
)

download_file(
    name = "kotlin-script-runtime.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-script-runtime/{0}/kotlin-script-runtime-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "4ed97fd1e0c9bb1d0948ed5e63cc91456313a59518295b92b2c1514aef556150",
)

download_file(
    name = "kotlin-scripting-common.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-common/{0}/kotlin-scripting-common-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "7c9ac8499fa91b201ea7836537c625dd52a82549e57e6b3f175cc0c79e9ab604",
)

download_file(
    name = "kotlin-scripting-compiler.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler/{0}/kotlin-scripting-compiler-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "e127f4bb3a7aac17e53df1b09e062e0878d8ac665335f79d002a6ac99882ca90",
)

download_file(
    name = "kotlin-scripting-compiler-impl.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/{0}/kotlin-scripting-compiler-impl-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "83e8ec0b53778f5c5f32199bfebda2ccd6c41b0b99390bb71b6a1c9b7c2fc4bf",
)

download_file(
    name = "kotlin-scripting-jvm.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-jvm/{0}/kotlin-scripting-jvm-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "60a8c742d61f44f2311fbdc8a86b767d171ba09d97be72d6ba450d37f7e3fdbf",
)

download_file(
    name = "kotlin-stdlib.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "65d12d85a3b865c160db9147851712a64b10dadd68b22eea22a95bf8a8670dca",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-all.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "e2300faa59c935293dabb0a183d2e6108a9b011c427076c3f7ba10d24b73ba0a",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-common-sources.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "fea963432261427542dfe929e42a3f746034e088fdff21c6d6297eef6f607166",
)

download_file(
    name = "kotlin-stdlib-jdk7.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "0d10bc0d42b8605f23629a3f31ea27c19cdbca9dcdf4f53f6d22cd6366836d18",
)

download_file(
    name = "kotlin-stdlib-jdk7-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}-sources.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "2534c8908432e06de73177509903d405b55f423dd4c2f747e16b92a2162611e6",
)

download_file(
    name = "kotlin-stdlib-jdk8.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "adc16648dbbcf35b0d10e7ec301c35d746d1c2fe460c606aba59f12b117cf9b0",
)

download_file(
    name = "kotlin-stdlib-jdk8-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}-sources.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
)

download_file(
    name = "kotlin-stdlib-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-js/{0}/kotlin-stdlib-js-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "d68bcaa6ca7d6b69ed3a263323811eadb46ecebc4d6b3cd89eb3a0ad971686b9",
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
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}-sources.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "967ad9599254e3a60d96d6c789547cc35c22d770d9c8fb1e3f15fac3b4c3b65d",
)

download_file(
    name = "kotlin-stdlib-wasm-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/{0}/kotlin-stdlib-wasm-js-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "dc8c650c07270cbdcd77a029de85d6cd0f89ca6d566fb18cc9eb09b213eee18e",
)

download_file(
    name = "kotlin-stdlib-wasm-wasi.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/{0}/kotlin-stdlib-wasm-wasi-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "af4ed3e0c363b7c9defce3ed9d2e371d2364178cfdc4b441ad4df052ebec0570",
)

download_file(
    name = "kotlin-test.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test/{0}/kotlin-test-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "8db175a3f56cf139ebdf8936f293ce5a64a16b52a0408804e0ec077e8848eb32",
)

download_file(
    name = "kotlin-test-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-js/{0}/kotlin-test-js-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "97febf96080565efc336c279446f6d33b67a141e72c543f3ad2c4dae4a7ea47c",
)

download_file(
    name = "kotlin-test-junit.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-junit/{0}/kotlin-test-junit-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "f45ed32e721d901d3cbe83ece7f41c98533b34155186368a79bfdc667e433dce",
)

download_file(
    name = "parcelize-compiler-plugin-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/{0}/parcelize-compiler-plugin-for-ide-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "9c320ab99b53b89ff0c607ece066023399ecc933643e39ca4622fe1721d78afc",
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
