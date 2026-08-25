load("@bazel_features//:features.bzl", "bazel_features")
load("@bazel_skylib//lib:modules.bzl", "modules")

_files = []

def download_file(name, url, sha256):
    _files.append(struct(name = name, url = url, sha256 = sha256))

kotlincRepositoryUrl = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies"
jpsPluginRepositoryUrl = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies"

kotlinCompilerCliVersion = "2.5.255-dev-255"
kotlincKotlinJpsPluginTestsVersion = "2.4.10"

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
    url = "{0}/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/{1}/compose-compiler-plugin-for-ide-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "9354233dfb27e76cccbc8e79b45a2edce57c346b9dcac51683c9a5b53865eabb",
)

download_file(
    name = "js-ir-runtime-for-ide.klib",
    url = "{0}/org/jetbrains/kotlin/js-ir-runtime-for-ide/{1}/js-ir-runtime-for-ide-{1}.klib".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "4daaad8d158a8323a42b195a62e3ee977b274cd25d80bf1b7617ff0455874ef9",
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
    url = "{0}/org/jetbrains/kotlin/kotlin-annotations-jvm/{1}/kotlin-annotations-jvm-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "6054230599c840c69af3657c292b768f2b585c25fba91d26cd5ad8c992757b03",
)

download_file(
    name = "kotlin-compiler.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-compiler/{1}/kotlin-compiler-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "a2efc082d24d08011090a108573bbcfba1f0c2628e71e413660eacc212d2cdc5",
)

download_file(
    name = "kotlin-daemon.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-daemon/{1}/kotlin-daemon-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "fffc7d3a22d4a07df4729176e7b20ca3381d725273c493401edefac5db64a2d8",
)

download_file(
    name = "kotlin-dist-for-ide-increment-compilation.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-dist-for-ide/{1}/kotlin-dist-for-ide-{1}.jar".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "0e80ea565f51f00e20a3135c7e266e1383d02a08fcada4267408f22fb1d9f522",
)

download_file(
    name = "kotlin-dist-for-ide.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-dist-for-ide/{1}/kotlin-dist-for-ide-{1}.jar".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "0e80ea565f51f00e20a3135c7e266e1383d02a08fcada4267408f22fb1d9f522",
)

download_file(
    name = "kotlin-dom-api-compat.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-dom-api-compat/{1}/kotlin-dom-api-compat-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "00106d26e44651268ea0c2fbf9b2e17044103d767118917b0497fbd462ac8c7a",
)

download_file(
    name = "kotlin-jps-plugin-classpath.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-jps-plugin-classpath/{1}/kotlin-jps-plugin-classpath-{1}.jar".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "cff201bbcf43942a716658192e84e2977cb3cb7efa3b10e7bbcb284e69903615",
)

download_file(
    name = "kotlin-jps-plugin-testdata-for-ide.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/{1}/kotlin-jps-plugin-testdata-for-ide-{1}.jar".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "8de8f62ebeab00a922c1fafa31635b7b7cf29f0b5f4e9896a8b94a7d51f44e90",
)

download_file(
    name = "kotlin-reflect.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-reflect/{1}/kotlin-reflect-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "2796487683e21087a3125ad34ef411d2364df02a077bb099618c22a5c9118405",
)

download_file(
    name = "kotlin-reflect-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-reflect/{1}/kotlin-reflect-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "19bfd0223fb82ab5fda044d5c1d57cefb1e61529dc733d9590451958e9f5caf7",
)

download_file(
    name = "kotlin-script-runtime.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-script-runtime/{1}/kotlin-script-runtime-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "25c3a35dbbe60981a63dce6fe9a52c0995705ee3d2778498b4d2f8bfc272b640",
)

download_file(
    name = "kotlin-scripting-common.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-common/{1}/kotlin-scripting-common-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "a93d7e42738b1dd16e3029ec8bf837e8d83b8fd2c701bc4261156e072180437d",
)

download_file(
    name = "kotlin-scripting-compiler.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-compiler/{1}/kotlin-scripting-compiler-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "55e3f39b720b0ff60bbe198ab560da06cc7761b902d8eb38840223d850a14cda",
)

download_file(
    name = "kotlin-scripting-compiler-impl.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/{1}/kotlin-scripting-compiler-impl-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "7edc2c67229b396592de1d574bf70a4f72895d0a2c4a4989d9c6878982f9d892",
)

download_file(
    name = "kotlin-scripting-jvm.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-jvm/{1}/kotlin-scripting-jvm-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "1b3c0756fd9452f8f68e779a7bfae70def2fc00caa9eb7956bdb483bf4fef40b",
)

download_file(
    name = "kotlin-stdlib-2.1.21.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.1.21/kotlin-stdlib-2.1.21.jar",
    sha256 = "263bdc679e1f62012db7b091796279b6d71cf36f4797a98ff1ace05835f201c8",
)

download_file(
    name = "kotlin-stdlib.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "c54f5d2ba5a3a1d68db2110e248801381176683780aefeb7b2bcea95d9aa8c2b",
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
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}-all.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "c2e0a0fbeec7f52dd5a1297e3283070ba81050a4fc3b1b281e0247ba6ba7c295",
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
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}-common-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "76ad19d89691238e46ca9acce869c11a0051e2318c78e04b642d29ce914499fc",
)

download_file(
    name = "kotlin-stdlib-jdk7.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{1}/kotlin-stdlib-jdk7-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "6af3aaa1e64d0a88607bc98a52f89d1eb9abbc6ae9854642fe763186a24ff606",
)

download_file(
    name = "kotlin-stdlib-jdk7-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{1}/kotlin-stdlib-jdk7-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "2534c8908432e06de73177509903d405b55f423dd4c2f747e16b92a2162611e6",
)

download_file(
    name = "kotlin-stdlib-jdk8-2.1.21.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib-jdk8/2.1.21/kotlin-stdlib-jdk8-2.1.21.jar",
    sha256 = "87b4f956de27401446227e474ac7a31acff0d0a8087160c54288c1e6f46a67e6",
)

download_file(
    name = "kotlin-stdlib-jdk8.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{1}/kotlin-stdlib-jdk8-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "3fd8856ca0a7fef3f88eda7b2f7665758b028a214cb1b0ece84d32e22e66ff0d",
)

download_file(
    name = "kotlin-stdlib-jdk8-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{1}/kotlin-stdlib-jdk8-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
)

download_file(
    name = "kotlin-stdlib-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-js/{1}/kotlin-stdlib-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "dc57e6b4cd8f5e5ab8bf29134860c9c30b2b41b1c32377b019f626818f17ffc2",
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
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "81a1791169def4464e5012d31db0292fb5b7409654b5a0cccea0acdda3d8050a",
)

download_file(
    name = "kotlin-stdlib-wasm-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/{1}/kotlin-stdlib-wasm-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "14235efd78271d84ba90799e261ee0b162f27c8ac64812b458e045a70f20c15b",
)

download_file(
    name = "kotlin-stdlib-wasm-wasi.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/{1}/kotlin-stdlib-wasm-wasi-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "453b16db8c9278f8682858742ce7abd1bfbbc54b9c20d09e2d7ac10acc66053f",
)

download_file(
    name = "kotlin-test.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-test/{1}/kotlin-test-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "85709dbfeeb40acff8f14b5df76aea12358af1ec9e73049fae3d03703441f1ba",
)

download_file(
    name = "kotlin-test-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-test-js/{1}/kotlin-test-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "9004f5d49b9c862f2e23dc1c7e9d5e749b365f72f4878aa405b63cff766df1e9",
)

download_file(
    name = "kotlin-test-junit.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-test-junit/{1}/kotlin-test-junit-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "8a5be075e5e8ee65b474aae6f7060ea85440bfb0f42f0366abee2994b1a63035",
)

download_file(
    name = "parcelize-compiler-plugin-for-ide.jar",
    url = "{0}/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/{1}/parcelize-compiler-plugin-for-ide-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "5ff3c1ec313e9fb915ad63343c8c05f62e7384b8cb6821c6b6f076f012eb22d2",
)

all_test_dep_targets = ["@kotlin_test_deps//:" + t.name for t in _files]

def _kotlin_test_deps_impl(repository_ctx):
    downloads = []
    home_dir = repository_ctx.getenv("HOME")
    m2_dir = repository_ctx.path(home_dir).get_child(".m2").get_child("repository") if home_dir else None
    is_snapshot = repository_ctx.getenv("JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT") == kotlinCompilerCliVersion
    for file in _files:
        if not file.sha256:
            fail("kotlin_test_deps requires a non-empty sha256 for " + file.name)

        # Kotlin plugin team use special workflow for simultaneous development Kotlin compiler and IDEA plugin.
        # In this scenario maven libraries with complier artifacts are replaced on locally deployed jars in the Kotlin repo folder.
        # To support test in this scenario, we need special handling urls with a custom hardcoded version.
        # See docs about this process:
        # https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/docs/cooperative-development/environment-setup.md
        if file.url.find(kotlinCompilerCliVersion) != -1 and is_snapshot:
            m2_file = m2_dir.get_child(file.url.removeprefix(kotlincRepositoryUrl + "/"))
            repository_ctx.file(
                file.name,
                content = repository_ctx.read(m2_file, watch = "no"),
                executable = False,
            )
        else:
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
        "exports_files([" + ", ".join(["'" + f.name + "'" for f in _files]) + "])\n",
    )
    return repository_ctx.repo_metadata(reproducible = not is_snapshot)

kotlin_test_deps = repository_rule(implementation = _kotlin_test_deps_impl)
