load("@bazel_features//:features.bzl", "bazel_features")
load("@bazel_skylib//lib:modules.bzl", "modules")

_files = []

def download_file(name, url, sha256):
    _files.append(struct(name = name, url = url, sha256 = sha256))

kotlincRepositoryUrl = "https://cache-redirector.jetbrains.com/maven/ultimate-public"
jpsPluginRepositoryUrl = "https://cache-redirector.jetbrains.com/maven/ultimate-public"

kotlinCompilerCliVersion = "2.5.20-dev-5671"
kotlincKotlinJpsPluginTestsVersion = "2.4.21"

# The two pinned Build Tools API implementations of the Kotlin JPS builder tests; see KotlinJpsBuilderTestArtifacts
# and https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/docs/JpsBuilderTests.md.
# The versions do not follow the Kotlin compiler of the IDE, and BazelKotlinDependencyFacade.update changes none of
# them: update them by hand, together with the sha256 values of the entries below. The `-bootstrap` and `-stable`
# entries take their URLs from these constants.
kotlinBuildToolsImplBootstrapVersion = "2.5.20-dev-5671"
kotlinBuildToolsImplStableVersion = "2.5.20-dev-1532"

download_file(
    name = "kotlinx-serialization-core-1.7.1.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-serialization-core/1.7.1/kotlinx-serialization-core-1.7.1.jar",
    sha256 = "b1f0e71a3a10e6f6697603e35909c1db99abb1e95dd3ad11a29d62c9e28cffad",
)

download_file(
    name = "kotlinx-serialization-core-jvm-1.7.1.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.7.1/kotlinx-serialization-core-jvm-1.7.1.jar",
    sha256 = "73ddb8dc2c3033f1ebccfe56377ca1321b78afd2c1c65bfbf62195f1c7a09345",
)

download_file(
    name = "kotlinx-serialization-json-jvm-1.6.1.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.6.1/kotlinx-serialization-json-jvm-1.6.1.jar",
    sha256 = "8d2718bb042e830b12b7fb10af26d0fba43de1f1f9ffe0a6b131d4d251aac2cc",
)

download_file(
    name = "kotlinx-serialization-json-jvm-1.7.1.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.7.1/kotlinx-serialization-json-jvm-1.7.1.jar",
    sha256 = "ab6f1b6e8c70899d8c41f2be5c391d357840f1429b151f4dfb07271029083316",
)

download_file(
    name = "kotlinx-serialization-core-1.3.3.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-serialization-core/1.3.3/kotlinx-serialization-core-1.3.3.jar",
    sha256 = "f84746221055327cd88bf210c801e49abc17c912ceb8209cac224ac4304b7fa1",
)

download_file(
    name = "kotlinx-serialization-core-jvm-1.3.3.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.3.3/kotlinx-serialization-core-jvm-1.3.3.jar",
    sha256 = "7ef62d1a0114052608d0f541c17b25f1faac17a270d5e26217ac4329ea164752",
)

download_file(
    name = "kotlin-script-runtime-1.6.21.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlin/kotlin-script-runtime/1.6.21/kotlin-script-runtime-1.6.21.jar",
    sha256 = "606c34a7e6e8e439e9208765e7d75b1dbcf80f38353f3e29bb27456d7b371171",
)

download_file(
    name = "kotlinx-serialization-json-1.7.3.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-serialization-json/1.7.3/kotlinx-serialization-json-1.7.3.jar",
    sha256 = "aa93fa3c96392cb139593134430dc2d51367f59d553e5e3747ebd8007b263f1b",
)

download_file(
    name = "kotlinx-serialization-core-jvm-1.7.3.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.7.3/kotlinx-serialization-core-jvm-1.7.3.jar",
    sha256 = "f0adde45864144475385cf4aa7e0b7feb27f61fcf9472665ed98cc971b06b1eb",
)

download_file(
    name = "kotlinx-coroutines-core-1.6.4.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.6.4/kotlinx-coroutines-core-1.6.4.jar",
    sha256 = "778851e73851b502e8366434bc9ec58371431890fb12b89e7edbf1732962c030",
)

download_file(
    name = "kotlinx-coroutines-core-jvm-1.6.4.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.6.4/kotlinx-coroutines-core-jvm-1.6.4.jar",
    sha256 = "c24c8bb27bb320c4a93871501a7e5e0c61607638907b197aef675513d4c820be",
)

download_file(
    name = "kotlinx-coroutines-core-1.7.3.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.7.3/kotlinx-coroutines-core-1.7.3.jar",
    sha256 = "f9522095aedcc2a6ab32c7484061ea698352c71be1390adb403b59aa48a38fdc",
)

download_file(
    name = "kotlinx-coroutines-core-1.7.3-sources.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.7.3/kotlinx-coroutines-core-1.7.3-sources.jar",
    sha256 = "86ce259182afe7dd82cfe97da50a736a6194a91cfe19b8336799890bbd0e81b1",
)

download_file(
    name = "kotlinx-coroutines-core-jvm-1.7.3.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/kotlinx-coroutines-core-jvm-1.7.3.jar",
    sha256 = "1ab3acc38f3e7355c4f9d1ec62107a46fa73c899f3070d055e5d4373dfe67e12",
)

download_file(
    name = "kotlinx-coroutines-core-jvm-1.7.3-sources.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/kotlinx-coroutines-core-jvm-1.7.3-sources.jar",
    sha256 = "efabad4b6a46957325d956487a226234f26c2f519cddfcb7480c61c79e3ad95b",
)

download_file(
    name = "kotlinx-coroutines-core-1.4.2.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.4.2/kotlinx-coroutines-core-1.4.2.jar",
    sha256 = "4cd24a06b2a253110d8afd250e9eec6c6faafea6463d740824743d637e761f12",
)

download_file(
    name = "kotlinx-coroutines-debug-1.3.8.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-debug/1.3.8/kotlinx-coroutines-debug-1.3.8.jar",
    sha256 = "9390452e85f3e2e41304af56882de73ab5deb8c9cfe0addd05e18af17922b342",
)

download_file(
    name = "kotlinx-coroutines-core-1.3.8.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.3.8/kotlinx-coroutines-core-1.3.8.jar",
    sha256 = "f8c8b7485d4a575e38e5e94945539d1d4eccd3228a199e1a9aa094e8c26174ee",
)

download_file(
    name = "kotlinx-coroutines-core-jvm-1.8.0-intellij-11.jar",
    url = "https://cache-redirector.jetbrains.com/maven/ultimate-public/com/intellij/platform/kotlinx-coroutines-core-jvm/1.8.0-intellij-11/kotlinx-coroutines-core-jvm-1.8.0-intellij-11.jar",
    sha256 = "e7acf96587bc3148db64b5e0adc988fa743f9820a99eb118acb905935ead1bc6",
)

download_file(
    name = "kotlinx-coroutines-core-jvm-1.8.0-intellij-11-sources.jar",
    url = "https://cache-redirector.jetbrains.com/maven/ultimate-public/com/intellij/platform/kotlinx-coroutines-core-jvm/1.8.0-intellij-11/kotlinx-coroutines-core-jvm-1.8.0-intellij-11-sources.jar",
    sha256 = "37659637be7bd80e57b7fc652c5dc71739f4a46041c8be61cbf8205a3d5c86ea",
)

download_file(
    name = "kotlinx-coroutines-core-1.10.1.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.10.1/kotlinx-coroutines-core-1.10.1.jar",
    sha256 = "fae4771dd987cfadabae129dd7f625af40d9e4f14abb7ffc72e42dccb97b7010",
)

download_file(
    name = "kotlinx-coroutines-core-jvm-1.10.1.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.1/kotlinx-coroutines-core-jvm-1.10.1.jar",
    sha256 = "069c5988633230e074ec0d39321ec3cdaa4547c49e90ba936c63d8fc91c8c00d",
)

download_file(
    name = "kotlin-parcelize-runtime-1.8.20.jar",
    url = "https://cache-redirector.jetbrains.com/maven/ultimate-public/org/jetbrains/kotlin/kotlin-parcelize-runtime/1.8.20/kotlin-parcelize-runtime-1.8.20.jar",
    sha256 = "b45e3e13ab8b5864bc979a4b324ce59b53bff9d7e6be57f21862aab0abaa5874",
)

download_file(
    name = "runtime-desktop-1.6.11.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/compose/runtime/runtime-desktop/1.6.11/runtime-desktop-1.6.11.jar",
    sha256 = "66c97d0d48ac8852ed2780de5a747ea94a26c29b37196e23e6225502a2a09c96",
)

download_file(
    name = "kotlinx-coroutines-core-1.8.0.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.8.0/kotlinx-coroutines-core-1.8.0.jar",
    sha256 = "20aa434b6a930ea66d2e61b00deefae09fea3d32f9640d2e0c271312880e0add",
)

download_file(
    name = "kotlinx-coroutines-core-jvm-1.8.0.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.0/kotlinx-coroutines-core-jvm-1.8.0.jar",
    sha256 = "9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5",
)

download_file(
    name = "collection-jvm-1.4.0.jar",
    url = "https://cache-redirector.jetbrains.com/maven.google.com/androidx/collection/collection-jvm/1.4.0/collection-jvm-1.4.0.jar",
    sha256 = "d5cf7b72647c7995071588fe870450ff9c8f127f253d2d4851e161b800f67ae0",
)

download_file(
    name = "kotlinx-coroutines-core-1.10.2.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.10.2/kotlinx-coroutines-core-1.10.2.jar",
    sha256 = "319b653009d49c70982f98df29cc84fc7025b092cb0571c8e7532e3ad4366dae",
)

download_file(
    name = "kotlinx-coroutines-core-jvm-1.10.2.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.2/kotlinx-coroutines-core-jvm-1.10.2.jar",
    sha256 = "5ca175b38df331fd64155b35cd8cae1251fa9ee369709b36d42e0a288ccce3fd",
)

download_file(
    name = "atomicfu-jvm-0.27.0.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlinx/atomicfu-jvm/0.27.0/atomicfu-jvm-0.27.0.jar",
    sha256 = "2b68464170070a8b085d8a7224c7c002dbd65ea14e1f8b97a9605115a252f7fb",
)

download_file(
    name = "annotations-java5-24.0.0.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/annotations-java5/24.0.0/annotations-java5-24.0.0.jar",
    sha256 = "2d033590350f9e936a787bfa407ecae221a80220762c9cf56c0066ff5e52fd10",
)

download_file(
    name = "annotations-13.0.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/annotations/13.0/annotations-13.0.jar",
    sha256 = "ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478",
)

download_file(
    name = "annotations.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar",
    sha256 = "2037be378980d3ba9333e97955f3b2cde392aa124d04ca73ce2eee6657199297",
)

download_file(
    name = "compose-compiler-plugin-for-ide.jar",
    url = "{0}/org/jetbrains/kotlin/compose-compiler-plugin-for-ide/{1}/compose-compiler-plugin-for-ide-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "ba8e35c6a4cec8626b7bbf633b7e5964e75873feca1608d7d949907ae9828ddd",
)

download_file(
    name = "js-ir-runtime-for-ide.klib",
    url = "{0}/org/jetbrains/kotlin/js-ir-runtime-for-ide/{1}/js-ir-runtime-for-ide-{1}.klib".format(jpsPluginRepositoryUrl, kotlincKotlinJpsPluginTestsVersion),
    sha256 = "caf38846e5646857ef534a01642245842bbafd02affb3b0906a1e9293f9a94c2",
)

download_file(
    name = "jsr305.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
    sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
)

download_file(
    name = "junit.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/junit/junit/3.8.2/junit-3.8.2.jar",
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
    sha256 = "fac4f75ccb17cefd347d632889b1472bf089b661e0809699362695ec72885bf7",
)

download_file(
    name = "kotlin-daemon.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-daemon/{1}/kotlin-daemon-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "f4cb574cc5ebfbe1cb30196ef084094ea11e191a4097e4b6461425d08068638a",
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
    sha256 = "297ff3379db0c030024b8f6e9597c84b01d0c887b435ea40306eed02475d916b",
)

download_file(
    name = "kotlin-dom-api-compat.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-dom-api-compat/{1}/kotlin-dom-api-compat-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "a5209daca0a60b3271b52311cdadc90f76fdf04b240d10c761e2a9b842c66d1d",
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
    sha256 = "f0f30a9ea8ef1127de920aad88acda3b23bacfee895baf812a76df62c43bb697",
)

download_file(
    name = "kotlin-reflect-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-reflect/{1}/kotlin-reflect-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "ee3c44bb6177590975fcf65e9fdf69f0c097f79f35832501a86f4d641f5040af",
)

download_file(
    name = "kotlin-script-runtime.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-script-runtime/{1}/kotlin-script-runtime-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "5f3e99aaedb298dbadf24d6664a4241c2c1648d143dfccb3a5427f209d282fb9",
)

download_file(
    name = "kotlin-scripting-common.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-common/{1}/kotlin-scripting-common-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "6fa2736164bfc3b068e2e5d8c72cad3d979e87aeb691d491f1ea2060491735ae",
)

download_file(
    name = "kotlin-scripting-compiler.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-compiler/{1}/kotlin-scripting-compiler-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "929111eeb921d6d1b4b1382931018a66ce1cb0133f2b717d863516e1d2c04a60",
)

download_file(
    name = "kotlin-scripting-compiler-impl.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-compiler-impl/{1}/kotlin-scripting-compiler-impl-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "56c5c300dbda0ab6f035990f7b9196042bf8c3e8a1170944b82ae49a87c1525f",
)

download_file(
    name = "kotlin-scripting-jvm.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-scripting-jvm/{1}/kotlin-scripting-jvm-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "0f4160e233e4df7e21e1ad87fbe07c673a37731e1b43b6dfb8193cffbc38d11f",
)

download_file(
    name = "kotlin-stdlib-2.1.21.jar",
    url = "https://cache-redirector.jetbrains.com/maven/ultimate-public/org/jetbrains/kotlin/kotlin-stdlib/2.1.21/kotlin-stdlib-2.1.21.jar",
    sha256 = "263bdc679e1f62012db7b091796279b6d71cf36f4797a98ff1ace05835f201c8",
)

download_file(
    name = "kotlin-stdlib.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "32f4e68216a442b68bfc62117295745b54c08b8841dc737757f3a8796f2d7898",
)

download_file(
    name = "kotlin-stdlib-1.7.0.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlin/kotlin-stdlib/1.7.0/kotlin-stdlib-1.7.0.jar",
    sha256 = "aa88e9625577957f3249a46cb6e166ee09b369e600f7a11d148d16b0a6d87f05",
)

download_file(
    name = "kotlin-stdlib-1.7.0-sources.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlin/kotlin-stdlib/1.7.0/kotlin-stdlib-1.7.0-sources.jar",
    sha256 = "2176274ecf922fffdd9a7eeec18f5e3a69f7ed53dadb5add3c9a706560ac9d7f",
)

download_file(
    name = "kotlin-stdlib-all.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}-all.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "39914ec57ad25c80a0afee6b08097bc867432b95b141e0247012e3d5b9fdda9b",
)

download_file(
    name = "kotlin-stdlib-common.jar",
    url = "https://cache-redirector.jetbrains.com/maven/ultimate-public/org/jetbrains/kotlin/kotlin-stdlib-common/1.9.22/kotlin-stdlib-common-1.9.22.jar",
    sha256 = "60b53a3fc0ed19ff5568ad54372f102f51109b7480417e93c8f3418ae4f73188",
)

download_file(
    name = "kotlin-stdlib-common-1.7.0-sources.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlin/kotlin-stdlib-common/1.7.0/kotlin-stdlib-common-1.7.0-sources.jar",
    sha256 = "406ecfb22a278ef80b642196d572eda4daebeed67b88474c86b39265288fba00",
)

download_file(
    name = "kotlin-stdlib-common-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}-common-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "265c222f00651e6d1066f253c46f0846d9cd7940bc98846d24664aa8771397ab",
)

download_file(
    name = "kotlin-stdlib-jdk7.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{1}/kotlin-stdlib-jdk7-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "5c7c40ef7a0b4aa08569a57b19793b2c1cc85c7d6feb3d6e82dddf391196ef27",
)

download_file(
    name = "kotlin-stdlib-jdk7-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk7/{1}/kotlin-stdlib-jdk7-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "2534c8908432e06de73177509903d405b55f423dd4c2f747e16b92a2162611e6",
)

download_file(
    name = "kotlin-stdlib-jdk8-2.1.21.jar",
    url = "https://cache-redirector.jetbrains.com/maven/ultimate-public/org/jetbrains/kotlin/kotlin-stdlib-jdk8/2.1.21/kotlin-stdlib-jdk8-2.1.21.jar",
    sha256 = "87b4f956de27401446227e474ac7a31acff0d0a8087160c54288c1e6f46a67e6",
)

download_file(
    name = "kotlin-stdlib-jdk8.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{1}/kotlin-stdlib-jdk8-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "91e58a6f796754d117258bda356c80f5567674d9a456e6e2befffde6c9341eac",
)

download_file(
    name = "kotlin-stdlib-jdk8-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-jdk8/{1}/kotlin-stdlib-jdk8-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "3cb6895054a0985bba591c165503fe4dd63a215af53263b67a071ccdc242bf6e",
)

download_file(
    name = "kotlin-stdlib-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-js/{1}/kotlin-stdlib-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "845635f7b082cd7b8daaad71ef7229f73dc0fa1213224d813f86a19de3cdc8c5",
)

download_file(
    name = "kotlin-stdlib-js-1.9.22.jar",
    url = "https://cache-redirector.jetbrains.com/maven/ultimate-public/org/jetbrains/kotlin/kotlin-stdlib-js/1.9.22/kotlin-stdlib-js-1.9.22.jar",
    sha256 = "f89136086e9cc9d01c4f629093b2447289b8ff3de11cb58b2a1c92483a3dc7f5",
)

download_file(
    name = "kotlin-stdlib-1.9.22.jar",
    url = "https://cache-redirector.jetbrains.com/maven/ultimate-public/org/jetbrains/kotlin/kotlin-stdlib/1.9.22/kotlin-stdlib-1.9.22.jar",
    sha256 = "6abe146c27864138b874ccccfe5f534e3eb923c99a1b7b5d45494ee5694f3e0a",
)

download_file(
    name = "kotlin-stdlib-project-wizard-default.jar",
    url = "https://cache-redirector.jetbrains.com/maven-central/org/jetbrains/kotlin/kotlin-stdlib/1.9.0/kotlin-stdlib-1.9.0.jar",
    sha256 = "35aeffbe2db5aa446072cee50fcee48b7fa9e2fc51ca37c0cc7d7d0bc39d952e",
)

download_file(
    name = "kotlin-stdlib-sources.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}-sources.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "3c431aa8671bdf2db7b7ffabb0d842094c96c539954bac15ad10c8b3a01b2501",
)

download_file(
    name = "kotlin-stdlib-wasm-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-wasm-js/{1}/kotlin-stdlib-wasm-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "e0786c8b8d61132fefcc7bb524d9cebf230080b18c6f53f67b64765d6d40d234",
)

download_file(
    name = "kotlin-stdlib-wasm-wasi.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib-wasm-wasi/{1}/kotlin-stdlib-wasm-wasi-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "2a03ce6a5088052c2d01719a7e99f9d3fefcd252ad5fc517b85126395aa0600f",
)

download_file(
    name = "kotlin-test.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-test/{1}/kotlin-test-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "f7f743cbbbb22c3952f506333ae890df3ccd37b5a53f3dec07a6f1862ce89bc7",
)

download_file(
    name = "kotlin-test-js.klib",
    url = "{0}/org/jetbrains/kotlin/kotlin-test-js/{1}/kotlin-test-js-{1}.klib".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "088ae75a745395d74ced3e8ebc2eb1be8c473d54efda7d30103337f3f0c76abf",
)

download_file(
    name = "kotlin-test-junit.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-test-junit/{1}/kotlin-test-junit-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "b35d78b91ffe3601153b89a397463aa57035750c145d85b257092c525878a55f",
)

download_file(
    name = "parcelize-compiler-plugin-for-ide.jar",
    url = "{0}/org/jetbrains/kotlin/parcelize-compiler-plugin-for-ide/{1}/parcelize-compiler-plugin-for-ide-{1}.jar".format(kotlincRepositoryUrl, kotlinCompilerCliVersion),
    sha256 = "56b74d771e58f6685ee743ce4ee69a547932f5050bb4400f3204cc7a80006ccc",
)

# The artifacts of the Kotlin JPS builder tests; see KotlinJpsBuilderTestArtifacts. The names carry the bucket, not
# the version: BUILD.bazel of the tests and KotlinJpsBuilderTestArtifacts address an artifact by its bucket, so a
# version bump changes only the constants above and the sha256 values of these entries.
download_file(
    name = "kotlin-jps-plugin-testdata-for-ide-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-jps-plugin-testdata-for-ide/{1}/kotlin-jps-plugin-testdata-for-ide-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "b24c371023c37d9f33bdb9af6f766563973308e9d31c04f5e115532eaf336d56",
)

download_file(
    name = "kotlin-build-tools-api-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-build-tools-api/{1}/kotlin-build-tools-api-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "f7ddacd16b07411fab643611f3fc838cc56e1feb5b0c08fa5c9cfbb62805be50",
)

download_file(
    name = "kotlin-build-tools-cri-impl-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-build-tools-cri-impl/{1}/kotlin-build-tools-cri-impl-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "cb8afd26d24c1e9319ada4457075d841d5aa8a2327daf67d6ddeae49e77c0456",
)

download_file(
    name = "kotlin-build-tools-impl-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-build-tools-impl/{1}/kotlin-build-tools-impl-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "32cb246f5dcc5e4b8c9831e44b3ede0015e28bbf7291cccdd59207f88daf9175",
)

download_file(
    name = "kotlin-compiler-embeddable-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-compiler-embeddable/{1}/kotlin-compiler-embeddable-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "e5a8605706d6fbd8d202d57dd75d6a3ac72f5e3c489c690433be04e918a9d89f",
)

download_file(
    name = "kotlin-compiler-runner-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-compiler-runner/{1}/kotlin-compiler-runner-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "13654a05f9d94a7b5d411984800fe14a310ea7492978b26eaf26a4d47378e2f4",
)

download_file(
    name = "kotlin-daemon-client-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-daemon-client/{1}/kotlin-daemon-client-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "00af4cd5fd2f254ad5e11a16d1d3aa2581dd71be96fdb4eca0fdd029a34495fd",
)

download_file(
    name = "kotlin-daemon-embeddable-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-daemon-embeddable/{1}/kotlin-daemon-embeddable-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "417f2fdecb39450efb92a76151bc65e3f0495decbfaf3efc97e2bb9dc3457403",
)

download_file(
    name = "kotlin-reflect-1.6.10.jar",
    url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10.jar",
    sha256 = "3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203",
)

download_file(
    name = "kotlin-stdlib-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "32f4e68216a442b68bfc62117295745b54c08b8841dc737757f3a8796f2d7898",
)

download_file(
    name = "kotlin-tooling-core-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-tooling-core/{1}/kotlin-tooling-core-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "4dc5ec54a924c0d66d6f918e67aa28a569275a64cdfdaab11061c9eb9bb16b67",
)

download_file(
    name = "typescript-export-standalone-embeddable-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/typescript-export-standalone-embeddable/{1}/typescript-export-standalone-embeddable-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "1eea2692e9b6d1560d07cad4f66ddb8e33fd3e4503179dff97f7877d2c4189e2",
)

download_file(
    name = "kotlin-test-bootstrap.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-test/{1}/kotlin-test-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplBootstrapVersion),
    sha256 = "f7f743cbbbb22c3952f506333ae890df3ccd37b5a53f3dec07a6f1862ce89bc7",
)

download_file(
    name = "kotlin-build-tools-api-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-build-tools-api/{1}/kotlin-build-tools-api-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "f3e5d11bd80a713b6da405132a786f9dbae5f69a2936d2cf21963f2497b7f479",
)

download_file(
    name = "kotlin-build-tools-cri-impl-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-build-tools-cri-impl/{1}/kotlin-build-tools-cri-impl-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "b94dc8699ca7e0e7cb9c9d047624ae5f98388e9dfa9e2c9b5ba2700b6f9281c2",
)

download_file(
    name = "kotlin-build-tools-impl-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-build-tools-impl/{1}/kotlin-build-tools-impl-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "d621e2037191db1f0fc459f1262903b56cacef13b635368c41bbab241e4b5b03",
)

download_file(
    name = "kotlin-compiler-embeddable-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-compiler-embeddable/{1}/kotlin-compiler-embeddable-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "a9a248aecb7feefdc0253ecd7fcfb3250e5cad16f641f40741bf4def17162972",
)

download_file(
    name = "kotlin-compiler-runner-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-compiler-runner/{1}/kotlin-compiler-runner-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "4dcfcad543a15eaed1dadb315a87b4639a4005f68d40be075835da48adb0a75d",
)

download_file(
    name = "kotlin-daemon-client-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-daemon-client/{1}/kotlin-daemon-client-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "c2044bf40725d1a0b49c10d8fcfba58b4949536b7ba5f00926851a5942600798",
)

download_file(
    name = "kotlin-daemon-embeddable-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-daemon-embeddable/{1}/kotlin-daemon-embeddable-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "04a08c2cb82a05fa878f1ea17ee3506d2895e29d3741f9dba3403d1c502c810d",
)

download_file(
    name = "kotlin-stdlib-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-stdlib/{1}/kotlin-stdlib-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "06599aec68a27855a6a9d334e16dd09fe4b27a5aaf668df780f2188bcd7c3376",
)

download_file(
    name = "kotlin-tooling-core-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-tooling-core/{1}/kotlin-tooling-core-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "4dc5ec54a924c0d66d6f918e67aa28a569275a64cdfdaab11061c9eb9bb16b67",
)

download_file(
    name = "typescript-export-standalone-embeddable-stable.jar",
    url = "{0}/org/jetbrains/kotlin/typescript-export-standalone-embeddable/{1}/typescript-export-standalone-embeddable-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "93ad5604b04cef3b3d6716eb666db123f97545c1b9e48426915709193a4cfc04",
)

download_file(
    name = "kotlin-test-stable.jar",
    url = "{0}/org/jetbrains/kotlin/kotlin-test/{1}/kotlin-test-{1}.jar".format(kotlincRepositoryUrl, kotlinBuildToolsImplStableVersion),
    sha256 = "56a3ee2c9ea2684c7f8435d49e4d1f06c5f6b56c53bbc9373ed109f1fb589af9",
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
