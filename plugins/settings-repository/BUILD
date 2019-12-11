load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.settingsRepository",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["intellij.settingsRepository.iml"],
    package_prefixes = {"src": "org.jetbrains.settingsRepository"},
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/settings-repository/lib:jgit"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/plugins/settings-repository/lib:jgit",
        "//tools/idea/platform/vcs-impl:intellij.platform.vcs.impl[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl[module]",
        "//tools/idea/.idea/libraries:miglayout-swing",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:jackson",
        "//tools/idea/.idea/libraries:jackson-databind",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.settingsRepository.tests",
    iml_files = ["intellij.settingsRepository.tests.iml"],
    package_prefixes = {"testSrc": "org.jetbrains.settingsRepository.test"},
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # fails after IDEA 181.2784.17 merge
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/vcs-impl:intellij.platform.vcs.impl[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl[module]",
        "//tools/idea/platform/util:intellij.platform.util.tests[module, test]",
        "//tools/idea/plugins/settings-repository:intellij.settingsRepository[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore[module, test]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
    ],
)
