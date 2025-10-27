load("@bazel_skylib//lib:modules.bzl", "modules")
load("@bazel_features//:features.bzl", "bazel_features")

_files = []
def download_file(name, url, sha256):
    _files.append(struct(name = name, url = url, sha256 = sha256))

kotlinCompilerCliVersion = "2.3.20-dev-1838"
kotlincKotlinJpsPluginTestsVersion = "2.2.20"

download_file(
  name = "kotlinx-serialization-core-1.7.1.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core/1.7.1/kotlinx-serialization-core-1.7.1.jar",
  sha256 = "b1f0e71a3a10e6f6697603e35909c1db99abb1e95dd3ad11a29d62c9e28cffad",
)

download_file(
  name = "kotlinx-serialization-core-jvm-1.7.1.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.7.1/kotlinx-serialization-core-jvm-1.7.1.jar",
  sha256 = "73ddb8dc2c3033f1ebccfe56377ca1321b78afd2c1c65bfbf62195f1c7a09345",
)

download_file(
  name = "kotlinx-serialization-json-jvm-1.6.1.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.6.1/kotlinx-serialization-json-jvm-1.6.1.jar",
  sha256 = "8d2718bb042e830b12b7fb10af26d0fba43de1f1f9ffe0a6b131d4d251aac2cc",
)

download_file(
  name = "kotlinx-serialization-json-jvm-1.7.1.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.7.1/kotlinx-serialization-json-jvm-1.7.1.jar",
  sha256 = "ab6f1b6e8c70899d8c41f2be5c391d357840f1429b151f4dfb07271029083316",
)

download_file(
  name = "kotlinx-serialization-core-1.3.3.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core/1.3.3/kotlinx-serialization-core-1.3.3.jar",
  sha256 = "f84746221055327cd88bf210c801e49abc17c912ceb8209cac224ac4304b7fa1",
)

download_file(
  name = "kotlinx-serialization-core-jvm-1.3.3.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.3.3/kotlinx-serialization-core-jvm-1.3.3.jar",
  sha256 = "7ef62d1a0114052608d0f541c17b25f1faac17a270d5e26217ac4329ea164752",
)

download_file(
  name = "kotlin-script-runtime-1.6.21.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-script-runtime/1.6.21/kotlin-script-runtime-1.6.21.jar",
  sha256 = "606c34a7e6e8e439e9208765e7d75b1dbcf80f38353f3e29bb27456d7b371171",
)

download_file(
  name = "kotlinx-serialization-json-1.7.3.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/1.7.3/kotlinx-serialization-json-1.7.3.jar",
  sha256 = "aa93fa3c96392cb139593134430dc2d51367f59d553e5e3747ebd8007b263f1b",
)

download_file(
  name = "kotlinx-serialization-core-jvm-1.7.3.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.7.3/kotlinx-serialization-core-jvm-1.7.3.jar",
  sha256 = "f0adde45864144475385cf4aa7e0b7feb27f61fcf9472665ed98cc971b06b1eb",
)

download_file(
  name = "kotlinx-coroutines-core-1.6.4.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.6.4/kotlinx-coroutines-core-1.6.4.jar",
  sha256 = "778851e73851b502e8366434bc9ec58371431890fb12b89e7edbf1732962c030",
)

download_file(
  name = "kotlinx-coroutines-core-jvm-1.6.4.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.6.4/kotlinx-coroutines-core-jvm-1.6.4.jar",
  sha256 = "c24c8bb27bb320c4a93871501a7e5e0c61607638907b197aef675513d4c820be",
)

download_file(
  name = "kotlinx-coroutines-core-1.7.3.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.7.3/kotlinx-coroutines-core-1.7.3.jar",
  sha256 = "f9522095aedcc2a6ab32c7484061ea698352c71be1390adb403b59aa48a38fdc",
)

download_file(
  name = "kotlinx-coroutines-core-1.7.3-sources.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.7.3/kotlinx-coroutines-core-1.7.3-sources.jar",
  sha256 = "86ce259182afe7dd82cfe97da50a736a6194a91cfe19b8336799890bbd0e81b1",
)

download_file(
  name = "kotlinx-coroutines-core-jvm-1.7.3.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/kotlinx-coroutines-core-jvm-1.7.3.jar",
  sha256 = "1ab3acc38f3e7355c4f9d1ec62107a46fa73c899f3070d055e5d4373dfe67e12",
)

download_file(
  name = "kotlinx-coroutines-core-jvm-1.7.3-sources.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/kotlinx-coroutines-core-jvm-1.7.3-sources.jar",
  sha256 = "efabad4b6a46957325d956487a226234f26c2f519cddfcb7480c61c79e3ad95b",
)

download_file(
  name = "kotlinx-coroutines-core-1.4.2.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.4.2/kotlinx-coroutines-core-1.4.2.jar",
  sha256 = "4cd24a06b2a253110d8afd250e9eec6c6faafea6463d740824743d637e761f12",
)

download_file(
  name = "kotlinx-coroutines-debug-1.3.8.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-debug/1.3.8/kotlinx-coroutines-debug-1.3.8.jar",
  sha256 = "9390452e85f3e2e41304af56882de73ab5deb8c9cfe0addd05e18af17922b342",
)

download_file(
  name = "kotlinx-coroutines-core-1.3.8.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.3.8/kotlinx-coroutines-core-1.3.8.jar",
  sha256 = "f8c8b7485d4a575e38e5e94945539d1d4eccd3228a199e1a9aa094e8c26174ee",
)

download_file(
  name = "kotlinx-coroutines-core-jvm-1.8.0-intellij-11.jar",
  url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/com/intellij/platform/kotlinx-coroutines-core-jvm/1.8.0-intellij-11/kotlinx-coroutines-core-jvm-1.8.0-intellij-11.jar",
  sha256 = "e7acf96587bc3148db64b5e0adc988fa743f9820a99eb118acb905935ead1bc6",
)

download_file(
  name = "kotlinx-coroutines-core-jvm-1.8.0-intellij-11-sources.jar",
  url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/com/intellij/platform/kotlinx-coroutines-core-jvm/1.8.0-intellij-11/kotlinx-coroutines-core-jvm-1.8.0-intellij-11-sources.jar",
  sha256 = "37659637be7bd80e57b7fc652c5dc71739f4a46041c8be61cbf8205a3d5c86ea",
)

download_file(
  name = "kotlinx-coroutines-core-1.10.1.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.10.1/kotlinx-coroutines-core-1.10.1.jar",
  sha256 = "fae4771dd987cfadabae129dd7f625af40d9e4f14abb7ffc72e42dccb97b7010",
)

download_file(
  name = "kotlinx-coroutines-core-jvm-1.10.1.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.1/kotlinx-coroutines-core-jvm-1.10.1.jar",
  sha256 = "069c5988633230e074ec0d39321ec3cdaa4547c49e90ba936c63d8fc91c8c00d",
)

download_file(
  name = "kotlin-parcelize-runtime-1.8.20.jar",
  url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-parcelize-runtime/1.8.20/kotlin-parcelize-runtime-1.8.20.jar",
  sha256 = "b45e3e13ab8b5864bc979a4b324ce59b53bff9d7e6be57f21862aab0abaa5874",
)

download_file(
  name = "runtime-desktop-1.6.11.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/compose/runtime/runtime-desktop/1.6.11/runtime-desktop-1.6.11.jar",
  sha256 = "66c97d0d48ac8852ed2780de5a747ea94a26c29b37196e23e6225502a2a09c96",
)

download_file(
  name = "kotlinx-coroutines-core-1.8.0.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.8.0/kotlinx-coroutines-core-1.8.0.jar",
  sha256 = "20aa434b6a930ea66d2e61b00deefae09fea3d32f9640d2e0c271312880e0add",
)

download_file(
  name = "kotlinx-coroutines-core-jvm-1.8.0.jar",
  url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.0/kotlinx-coroutines-core-jvm-1.8.0.jar",
  sha256 = "9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5",
)

download_file(
  name = "collection-jvm-1.4.0.jar",
  url = "https://cache-redirector.jetbrains.com/maven.google.com/androidx/collection/collection-jvm/1.4.0/collection-jvm-1.4.0.jar",
  sha256 = "d5cf7b72647c7995071588fe870450ff9c8f127f253d2d4851e161b800f67ae0",
)

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
    name = "annotations-java5-24.0.0.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations-java5/24.0.0/annotations-java5-24.0.0.jar",
    sha256 = "2d033590350f9e936a787bfa407ecae221a80220762c9cf56c0066ff5e52fd10",
)

download_file(
    name = "annotations-13.0.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.jar",
    sha256 = "ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478",
)

download_file(
    name = "annotations.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar",
    sha256 = "2037be378980d3ba9333e97955f3b2cde392aa124d04ca73ce2eee6657199297",
)

download_file(
    name = "compose-compiler-plugin-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/{0}/compose-compiler-plugin-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "044bee1237d878f1a201ebd19de6b8d4b14b1bb82182ed4a39fff33041c5b49d",
)

download_file(
    name = "js-ir-runtime-for-ide.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/js-ir-runtime-for-ide/{0}/js-ir-runtime-for-ide-{0}.klib".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "0d1e7fb22f8a6d8daae1e13501b3bbe9fbcfb817b68035beb9385e4976a09f84",
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
    sha256 = "14b6f3fe0a65075d4d20c5edbff8d43bb9ec694027d4a4d7ed2b86cc43ea8e57",
)

download_file(
    name = "kotlin-compiler.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-compiler/{0}/kotlin-compiler-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "35acdbe7961bf92aee1e64ac8d34c9e10e12750764e73482381d5a94236e4505",
)

download_file(
    name = "kotlin-daemon.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-daemon/{0}/kotlin-daemon-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "fc3163cedfa7c0e3e9023228d6ba513d356beeaaf383de3fb0914169525d8603",
)

download_file(
    name = "kotlin-dist-for-ide-increment-compilation.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/{0}/kotlin-dist-for-ide-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "0070e80f9fed930c7209b15a5590f178acfc56028f2b6dd447c2c7cae1fc058d",
)

download_file(
    name = "kotlin-dist-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dist-for-ide/{0}/kotlin-dist-for-ide-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "0070e80f9fed930c7209b15a5590f178acfc56028f2b6dd447c2c7cae1fc058d",
)

download_file(
    name = "kotlin-dom-api-compat.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-dom-api-compat/{0}/kotlin-dom-api-compat-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "d3c8c7df3b28ae8837c171341be52dca02ff295c095c4a8673de2e8c65e30f5f",
)

download_file(
    name = "kotlin-jps-plugin-classpath.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-classpath/{0}/kotlin-jps-plugin-classpath-{0}.jar".format(kotlincKotlinJpsPluginTestsVersion),
    sha256 = "fa31a084adbecaaf98166e734fba1bf964e04e22671d70230ed9598cecd22124",
)

download_file(
    name = "kotlin-jps-plugin-testdata-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/{0}/kotlin-jps-plugin-testdata-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "9eadc21d40b1970c4daa0234a7e045327c540b9adf4543b93e01274e78085e72",
)

download_file(
    name = "kotlin-reflect.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "fbda8a32ca7b44454e186c3cccb2a3bf7ae05ba6c79b7cbed1e9395f632dacf0",
)

download_file(
    name = "kotlin-reflect-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-reflect/{0}/kotlin-reflect-{0}-sources.jar".format(kotlinCompilerCliVersion),
    sha256 = "9c061e320931ac63397651ad2feb30bcdd64ebee286cc546e076454d5860c6a8",
)

download_file(
    name = "kotlin-script-runtime.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-script-runtime/{0}/kotlin-script-runtime-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "5d2539db8193307430534223d87dfd62d3d3b20c55a5525e51eaac8a62ec5fbd",
)

download_file(
    name = "kotlin-scripting-common.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-common/{0}/kotlin-scripting-common-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "ea701569737a35d8746f1006a361fc2145e4621163f0604794d229b14405ac98",
)

download_file(
    name = "kotlin-scripting-compiler.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler/{0}/kotlin-scripting-compiler-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "7dbe6a4b28e49f3083cf4061f5b85ab4bb9674a4c29474cbc0cf287314070e69",
)

download_file(
    name = "kotlin-scripting-compiler-impl.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/{0}/kotlin-scripting-compiler-impl-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "4ef830e853577f3abf5dd7331730bf11bfea32e2cb404f0a6563771c94294306",
)

download_file(
    name = "kotlin-scripting-jvm.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-scripting-jvm/{0}/kotlin-scripting-jvm-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "25f67f109189527cb6127d067ccaffe0c43da667fb27e399079589d8de4e464a",
)

download_file(
    name = "kotlin-stdlib-2.1.21.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.1.21/kotlin-stdlib-2.1.21.jar",
    sha256 = "263bdc679e1f62012db7b091796279b6d71cf36f4797a98ff1ace05835f201c8",
)

download_file(
    name = "kotlin-stdlib.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/{0}/kotlin-stdlib-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "362187fef53fbee6d80a4b2eebbb535ea1cf7a96b0adb02c250907caca0b5957",
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
    sha256 = "ce474b1ea3a2d055ea3fb2388deab2a62ed3960c465c930c21adbef72f926a28",
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
    sha256 = "82f64c7215d2a15260ee03933bef5947339bd0d4bff6a6d0172a8d6e9a138bef",
)

download_file(
    name = "kotlin-stdlib-jdk7.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "bbb38a46c3961b0a55dfd3b3a2db920bb10a91c82eaa3df1327d6fd8b1338d9a",
)

download_file(
    name = "kotlin-stdlib-jdk7-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{0}/kotlin-stdlib-jdk7-{0}-sources.jar".format(kotlinCompilerCliVersion),
    sha256 = "2534c8908432e06de73177509903d405b55f423dd4c2f747e16b92a2162611e6",
)

download_file(
    name = "kotlin-stdlib-jdk8-2.1.21.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/2.1.21/kotlin-stdlib-jdk8-2.1.21.jar",
    sha256 = "87b4f956de27401446227e474ac7a31acff0d0a8087160c54288c1e6f46a67e6",
)

download_file(
    name = "kotlin-stdlib-jdk8.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "8a8dd4a5f68f79c8479a987bde122c8638a0cc604c90e5d9cd109dbf6d27762a",
)

download_file(
    name = "kotlin-stdlib-jdk8-sources.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{0}/kotlin-stdlib-jdk8-{0}-sources.jar".format(kotlinCompilerCliVersion),
    sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
)

download_file(
    name = "kotlin-stdlib-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-js/{0}/kotlin-stdlib-js-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "477c9483692853bbe30f380abc204b291b3970932d52c115f7af4a38c6079b82",
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
    sha256 = "8544bcbc349995ec8d50031bd30001256915f96c59e65d1f2e91a307eb4696ff",
)

download_file(
    name = "kotlin-stdlib-wasm-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/{0}/kotlin-stdlib-wasm-js-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "78e2af61d1c264a1ffeaa203839c1a50ecafe7f93ae2a4f95d20d977b23d8feb",
)

download_file(
    name = "kotlin-stdlib-wasm-wasi.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/{0}/kotlin-stdlib-wasm-wasi-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "80f034959643f80444838b551cf13597749800e8a095b70c6124fffc74af6d82",
)

download_file(
    name = "kotlin-test.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test/{0}/kotlin-test-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "fb355f8127a49c3b0c32d1c7e727f85529104ec21f26d45ac0a2ca18cce42d52",
)

download_file(
    name = "kotlin-test-js.klib",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-js/{0}/kotlin-test-js-{0}.klib".format(kotlinCompilerCliVersion),
    sha256 = "18efef7f18a246c9657d868be9470b8a706a1f12c08976b28896202bfbcbcc99",
)

download_file(
    name = "kotlin-test-junit.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-test-junit/{0}/kotlin-test-junit-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "91daf6b159b4c216aa7f84cb44cab4f0268777b2598fc13986b0c2d945541024",
)

download_file(
    name = "parcelize-compiler-plugin-for-ide.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/{0}/parcelize-compiler-plugin-for-ide-{0}.jar".format(kotlinCompilerCliVersion),
    sha256 = "de79a31f7b11235e8e48062eca5576d027ade61804987f74c81a5a927b147c9a",
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