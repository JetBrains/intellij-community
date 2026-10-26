load("@bazel_features//:features.bzl", "bazel_features")
load("@bazel_skylib//lib:modules.bzl", "modules")

_files = []

def download_file(name, url, sha256):
    _files.append(struct(name = name, url = url, sha256 = sha256))

kotlincRepositoryUrl = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies"
jpsPluginRepositoryUrl = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies"

kotlinCompilerCliVersion = "2.5.20-dev-3853"
kotlincKotlinJpsPluginTestsVersion = "2.4.21"

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
    name = "atomicfu-jvm-0.27.0.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlinx/atomicfu-jvm/0.27.0/atomicfu-jvm-0.27.0.jar",
    sha256 = "2b68464170070a8b085d8a7224c7c002dbd65ea14e1f8b97a9605115a252f7fb",
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
    sha256 = "996fa8af8daee57c27b9b3702eaa8f76ccbc35e1988a6f796cf22fcdf580870c",
)

download_file(
    name = "js-ir-runtime-for-ide.klib",
    url = "{0}/org/jetbrains/kotlin/js-ir-runtime-for-ide/{1}/js-ir-runtime-for-ide-{1}.klib".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "caf38846e5646857ef534a01642245842bbafd02affb3b0906a1e9293f9a94c2",
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
    sha256 = "f9fda5b05c89fe54536847c4f57de940b3dfaeb1dbc9ea9d4aa5eaec1abda2c8",
)

download_file(
    name = "kotlin-daemon.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-daemon/{1}/kotlin-daemon-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "253de6d0fe2197793f143783d58e4aae51bdc6a8dfd4ace9c106fb70ef5bc634",
)

download_file(
    name = "kotlin-dist-for-ide-increment-compilation.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-dist-for-ide/{1}/kotlin-dist-for-ide-{1}.jar".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "dd88d026c0753472326178f01229cc6da5db5ca7ce533093e17f46ee12aed728",
)

download_file(
    name = "kotlin-dist-for-ide.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-dist-for-ide/{1}/kotlin-dist-for-ide-{1}.jar".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "dd88d026c0753472326178f01229cc6da5db5ca7ce533093e17f46ee12aed728",
)

download_file(
    name = "kotlin-dist-for-ide-analyzer.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-dist-for-ide/{1}/kotlin-dist-for-ide-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "0ca16e44151d7b5b83cc2192a0558c7517f16912b6de609340dbd62bb4b94f0e",
)

download_file(
    name = "kotlin-dom-api-compat.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-dom-api-compat/{1}/kotlin-dom-api-compat-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "e12957c7461f8f5d985876cda4e3e342053a1eacbef6931c519008bf8780c922",
)

download_file(
    name = "kotlin-jps-plugin-classpath.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-jps-plugin-classpath/{1}/kotlin-jps-plugin-classpath-{1}.jar".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "fa692aeac4f53526a1f7e1f069de0263135249f0666615350d2bf74283741920",
)

download_file(
    name = "kotlin-jps-plugin-testdata-for-ide.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/{1}/kotlin-jps-plugin-testdata-for-ide-{1}.jar".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "e18112cfd4f9a2b1315a23f08b495e687e79d1fc6050dfe452d55506d9fbf4e0",
)

download_file(
    name = "kotlin-reflect.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-reflect/{1}/kotlin-reflect-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "74db3a7f07d6562aef030b235c6b93fa7d6b3a31b7900ab60b68a571c0ad69f4",
)

download_file(
    name = "kotlin-reflect-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-reflect/{1}/kotlin-reflect-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "a3e08b1efd0c7c41f2e4e03276d17ea6672c41c0a5fb124e74e80ebd2272cfa9",
)

download_file(
    name = "kotlin-script-runtime.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-script-runtime/{1}/kotlin-script-runtime-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "7ddd8613f4abfe4253fc8fc5d69456138dfea2ffe6f99b49917c5bf9723f22e7",
)

download_file(
    name = "kotlin-scripting-common.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-common/{1}/kotlin-scripting-common-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "9e8615fd9b8966bb3b89045556f9d73778f0f384af35c9a00037da3c3d5c65d2",
)

download_file(
    name = "kotlin-scripting-compiler.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-compiler/{1}/kotlin-scripting-compiler-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "44b9df3e5e6fea42d80251f18eef039f194a1e6d7ad6c92bac18de627625ebf0",
)

download_file(
    name = "kotlin-scripting-compiler-impl.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/{1}/kotlin-scripting-compiler-impl-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "0d7f1e9282e7b39570da364739836797886edce37e62c3d8524d412792002687",
)

download_file(
    name = "kotlin-scripting-jvm.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-jvm/{1}/kotlin-scripting-jvm-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "981cde4e199ec281b4ce3e2038a32d185327f44f527ac285d05bf56e357fddce",
)

download_file(
    name = "kotlin-stdlib-2.1.21.jar",
    url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/kotlin/kotlin-stdlib/2.1.21/kotlin-stdlib-2.1.21.jar",
    sha256 = "263bdc679e1f62012db7b091796279b6d71cf36f4797a98ff1ace05835f201c8",
)

download_file(
    name = "kotlin-stdlib.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "7760552f558e0340f2e2315c9194f2ec2b04daa04e1a22a2328bb55bda218d06",
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
    sha256 = "84b98fd5dd90680dc92540959f1be7ba8c5b2879199a2df98c89b382ef26874a",
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
    sha256 = "a79b94a384042bc20fcafd9fbe9bebce05a29b2a7c5e0c3535ec64349e4ed968",
)

download_file(
    name = "kotlin-stdlib-jdk7.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{1}/kotlin-stdlib-jdk7-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "99ad14101947d85c6b63a5a13ed866af77d8da9a115461671fa598e6e267ac90",
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
    sha256 = "fb0d8f4c6a4434336a95e3d6d49f08c4bb3138f7a8939025dc29a098f7b81593",
)

download_file(
    name = "kotlin-stdlib-jdk8-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{1}/kotlin-stdlib-jdk8-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
)

download_file(
    name = "kotlin-stdlib-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-js/{1}/kotlin-stdlib-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "ac404cb757a4f73f613b80fe253a7713adaa1afe0dfbeed8ae940c4898b8e581",
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
    sha256 = "3f99bcbee8ef6471d2b93e55d655f8445dc678d6202c21b8ec15465693478497",
)

download_file(
    name = "kotlin-stdlib-wasm-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/{1}/kotlin-stdlib-wasm-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "29ac3f56156301b2238965fb2d66e65b5831bafd21a988a14942e75acbefe127",
)

download_file(
    name = "kotlin-stdlib-wasm-wasi.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/{1}/kotlin-stdlib-wasm-wasi-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "ab64aa3af16a424775c471681d04a81cd2f2b895b2cb090e881f376dc538f5f3",
)

download_file(
    name = "kotlin-test.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-test/{1}/kotlin-test-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "decb4df3a0cd9cfd90c563a9baca82ed9a09e62ceb0571dc45fc2e7f4f3e87db",
)

download_file(
    name = "kotlin-test-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-test-js/{1}/kotlin-test-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "fe00c9ba17bacab117978335082b71c52eb4453011d6f42a4c8c00342e4fb888",
)

download_file(
    name = "kotlin-test-junit.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-test-junit/{1}/kotlin-test-junit-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "b0518f2a8b9825488ff870b475d2c5e09759eff3819969ef8654d1c3de9f4c18",
)

download_file(
    name = "parcelize-compiler-plugin-for-ide.jar",
    url = "{0}/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/{1}/parcelize-compiler-plugin-for-ide-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "651fee1344cc95c55235b2fa8e9d1c0cdb6d71fe56d7ba39f48ddfdede1565c3",
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
