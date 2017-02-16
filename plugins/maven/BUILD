load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "maven_and_others",
    # do not sort: must match IML order
    srcs = [
        "maven2-server-impl/src",
        "src/main/java",
    ],
    javacopts = ["-extra_checks:off"],
    resources = ["src/main/resources"],
    tags = ["managed"],
    test_srcs = [
        "maven2-server-impl/test",
        "src/test/java",
    ],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/maven/maven-server-api",
        "//tools/idea/java/openapi",
        "//tools/idea:platform-impl_and_others",
        "//tools/idea/java/compiler/openapi:compiler-openapi",
        "//tools/idea:compiler-impl_and_others",
        "//tools/idea/java/execution/openapi:execution-openapi",
        "//tools/idea/platform/forms_rt",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/external-system-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/maven/maven-server-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/plugins/maven/maven2-server-impl/lib:plexus-utils-1.5.5",
        "//tools/idea/plugins/maven/maven2-server-impl/lib:maven-dependency-tree-1.2",
        "//tools/idea/plugins/maven/maven2-server-impl/lib:archetype-common-2.0-alpha-4-SNAPSHOT",
        "//tools/idea/plugins/maven/maven2-server-impl/lib:mercury-artifact-1.0-alpha-6",
        "//tools/idea/plugins/maven/maven2-server-impl/lib/maven2/lib:maven-2.2.1-uber",
        "//tools/idea/plugins/maven/maven2-server-impl/lib/maven2/boot:classworlds-1.1",
        "//tools/idea/plugins/maven/maven2-server-impl/lib:nexus-indexer-1.2.3",
        "//tools/idea/plugins/maven/maven2-server-impl/lib:activation-1.1",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools/idea/plugins/maven/maven2-server-impl/lib:commons-beanutils",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/plugins/IntelliLang:IntelliLang-xml[module]",
        "//tools/idea/plugins/properties[module]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/platform/forms_rt[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/.idea/libraries:XmlBeans",
        "//tools/idea/plugins/maven/lib:wadl-core",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/plugins/groovy:jetgroovy[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/plugins/maven/jps-plugin:maven-jps-plugin[module]",
        "//tools/idea/plugins/maven/artifact-resolver-m2:maven-artifact-resolver-m2[module]",
        "//tools/idea/plugins/maven/artifact-resolver-m3:maven-artifact-resolver-m3[module]",
        "//tools/idea/plugins/maven/artifact-resolver-m31:maven-artifact-resolver-m31[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/plugins/maven/lib:plexus-archiver-2.4.4",
        "//tools/idea/platform/external-system-api[module]",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/platform/util:util-tests[module, test]",
    ],
)

iml_module(
    name = "aether-dependency-resolver",
    srcs = ["aether-dependency-resolver/src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/plugins/maven/lib:aether-1.1.0-all",
        "//tools/idea/plugins/maven/lib:maven-aether-provider-3.3.9-all",
    ],
)

iml_module(
    name = "maven3-server-impl",
    srcs = ["maven3-server-impl/src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/maven/maven-server-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/boot/plexus-classworlds-2.5.2",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/guava-18.0",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/cdi-api-1.0",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/jsoup-1.7.2",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/commons-io-2.2",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/javax.inject-1",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/jsr250-api-1.0",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/aopalliance-1.0",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/commons-cli-1.2",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/slf4j-api-1.7.5",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/wagon-file-2.10",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/commons-lang-2.6",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/guice-4.0-no_aop",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-core-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/commons-lang3-3.4",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-model-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/plexus-cipher-1.7",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-compat-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/slf4j-simple-1.7.5",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/plexus-utils-3.0.22",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-artifact-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-embedder-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-settings-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-plugin-api-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/wagon-http-2.10-shaded",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/wagon-http-shared-2.10",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/wagon-provider-api-2.10",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-model-builder-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/plexus-interpolation-1.21",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/plexus-sec-dispatcher-1.3",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/aether-api-1.0.2.v20150114",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/aether-spi-1.0.2.v20150114",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/aether-impl-1.0.2.v20150114",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/aether-util-1.0.2.v20150114",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-aether-provider-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-builder-support-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-settings-builder-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/org.eclipse.sisu.inject-0.3.2",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/org.eclipse.sisu.plexus-0.3.2",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/maven-repository-metadata-3.3.9",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/plexus-component-annotations-1.6",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/aether-connector-basic-1.0.2.v20150114",
        "//tools/idea/plugins/maven:maven3-server-impl/lib/maven3/lib/aether-transport-wagon-1.0.2.v20150114",
        "//tools/idea/plugins/maven/maven3-server-common/lib:nexus-indexer-3.0.4",
        "//tools/idea/plugins/maven/maven3-server-common/lib:maven-dependency-tree-1.2",
        "//tools/idea/plugins/maven:maven_and_others[module]",
        "//tools/idea/plugins/maven/maven3-server-common/lib:archetype-common-2.2",
        "//tools/idea/plugins/maven/maven3-server-common/lib:archetype-catalog-2.2",
        "//tools/idea/plugins/maven/maven3-server-common/lib:nexus-indexer-artifact-1.0.1",
        "//tools/idea/plugins/maven/maven3-server-common[module]",
    ],
)

java_import(
    name = "maven3-server-impl/lib/maven3/boot/plexus-classworlds-2.5.2",
    jars = ["maven3-server-impl/lib/maven3/boot/plexus-classworlds-2.5.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/guava-18.0",
    jars = ["maven3-server-impl/lib/maven3/lib/guava-18.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/cdi-api-1.0",
    jars = ["maven3-server-impl/lib/maven3/lib/cdi-api-1.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/jsoup-1.7.2",
    jars = ["maven3-server-impl/lib/maven3/lib/jsoup-1.7.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/commons-io-2.2",
    jars = ["maven3-server-impl/lib/maven3/lib/commons-io-2.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/javax.inject-1",
    jars = ["maven3-server-impl/lib/maven3/lib/javax.inject-1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/jsr250-api-1.0",
    jars = ["maven3-server-impl/lib/maven3/lib/jsr250-api-1.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/aopalliance-1.0",
    jars = ["maven3-server-impl/lib/maven3/lib/aopalliance-1.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/commons-cli-1.2",
    jars = ["maven3-server-impl/lib/maven3/lib/commons-cli-1.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/slf4j-api-1.7.5",
    jars = ["maven3-server-impl/lib/maven3/lib/slf4j-api-1.7.5.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/wagon-file-2.10",
    jars = ["maven3-server-impl/lib/maven3/lib/wagon-file-2.10.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/commons-lang-2.6",
    jars = ["maven3-server-impl/lib/maven3/lib/commons-lang-2.6.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/guice-4.0-no_aop",
    jars = ["maven3-server-impl/lib/maven3/lib/guice-4.0-no_aop.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-core-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-core-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/commons-lang3-3.4",
    jars = ["maven3-server-impl/lib/maven3/lib/commons-lang3-3.4.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-model-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-model-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/plexus-cipher-1.7",
    jars = ["maven3-server-impl/lib/maven3/lib/plexus-cipher-1.7.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-compat-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-compat-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/slf4j-simple-1.7.5",
    jars = ["maven3-server-impl/lib/maven3/lib/slf4j-simple-1.7.5.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/plexus-utils-3.0.22",
    jars = ["maven3-server-impl/lib/maven3/lib/plexus-utils-3.0.22.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-artifact-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-artifact-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-embedder-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-embedder-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-settings-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-settings-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-plugin-api-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-plugin-api-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/wagon-http-2.10-shaded",
    jars = ["maven3-server-impl/lib/maven3/lib/wagon-http-2.10-shaded.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/wagon-http-shared-2.10",
    jars = ["maven3-server-impl/lib/maven3/lib/wagon-http-shared-2.10.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/wagon-provider-api-2.10",
    jars = ["maven3-server-impl/lib/maven3/lib/wagon-provider-api-2.10.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-model-builder-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-model-builder-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/plexus-interpolation-1.21",
    jars = ["maven3-server-impl/lib/maven3/lib/plexus-interpolation-1.21.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/plexus-sec-dispatcher-1.3",
    jars = ["maven3-server-impl/lib/maven3/lib/plexus-sec-dispatcher-1.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/aether-api-1.0.2.v20150114",
    jars = ["maven3-server-impl/lib/maven3/lib/aether-api-1.0.2.v20150114.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/aether-spi-1.0.2.v20150114",
    jars = ["maven3-server-impl/lib/maven3/lib/aether-spi-1.0.2.v20150114.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/aether-impl-1.0.2.v20150114",
    jars = ["maven3-server-impl/lib/maven3/lib/aether-impl-1.0.2.v20150114.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/aether-util-1.0.2.v20150114",
    jars = ["maven3-server-impl/lib/maven3/lib/aether-util-1.0.2.v20150114.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-aether-provider-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-aether-provider-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-builder-support-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-builder-support-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-settings-builder-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-settings-builder-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/org.eclipse.sisu.inject-0.3.2",
    jars = ["maven3-server-impl/lib/maven3/lib/org.eclipse.sisu.inject-0.3.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/org.eclipse.sisu.plexus-0.3.2",
    jars = ["maven3-server-impl/lib/maven3/lib/org.eclipse.sisu.plexus-0.3.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/maven-repository-metadata-3.3.9",
    jars = ["maven3-server-impl/lib/maven3/lib/maven-repository-metadata-3.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/plexus-component-annotations-1.6",
    jars = ["maven3-server-impl/lib/maven3/lib/plexus-component-annotations-1.6.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/aether-connector-basic-1.0.2.v20150114",
    jars = ["maven3-server-impl/lib/maven3/lib/aether-connector-basic-1.0.2.v20150114.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "maven3-server-impl/lib/maven3/lib/aether-transport-wagon-1.0.2.v20150114",
    jars = ["maven3-server-impl/lib/maven3/lib/aether-transport-wagon-1.0.2.v20150114.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)
