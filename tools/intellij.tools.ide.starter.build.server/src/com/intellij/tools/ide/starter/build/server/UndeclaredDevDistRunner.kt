// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.includeRuntimeModuleRepositoryInIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.openapi.application.ArchivedCompilationContextUtil.BAZEL_TARGETS_JSON_FILE_PROPERTY
import com.intellij.openapi.application.PathManager
import com.intellij.platform.buildScripts.devLaunch.readCustomCommand
import com.intellij.platform.buildScripts.devLaunch.resolveAdditionalJvmArguments
import com.intellij.platform.devIdeConfig.DevIdeConfig
import java.nio.file.Path

/**
 * Fails a test under Bazel runfiles that declares no distribution, and names the declaration that the test target needs.
 *
 * Under Bazel runfiles, the legacy engine finds the module outputs through the file of [BAZEL_TARGETS_JSON_FILE_PROPERTY].
 * Without that property, it fails late: first on the property, then on missing jars. This runner fails in [startDevBuild]
 * instead, because only the [IdeInfo] names the product and the modules. [undeclaredDevDistMessage] writes the message.
 *
 * The distribution index is not in the runfiles of a test, and the runner adds no input for an error message. The runner
 * reads the index of the checkout. `JUnit5BazelRunner` makes the checkout the home of a `jps_test` without the sandbox. A
 * sandboxed test has another home, and then the message names the selection rule instead of a label.
 */
internal object UndeclaredDevDistRunner : DevBuildServerRunner {
  override fun isDevBuildSupported(): Boolean = true

  /** The runner returns no directory. */
  override val ownsInstallationDirectory: Boolean = false

  override fun readVmOptions(installationDirectory: Path): List<String> =
    com.intellij.platform.buildScripts.devLaunch.readVmOptions(installationDirectory)

  override fun readCustomCommandJvmArguments(installationDirectory: Path, command: String): List<String>? =
    readCustomCommand(installationDirectory, command)?.resolveAdditionalJvmArguments(installationDirectory)

  override fun startDevBuild(ideInfo: IdeInfo): Path {
    val (distributions, indexProblem) = readCheckoutIndex()
    error(undeclaredDevDistMessage(
      ideName = ideInfo.fullName,
      product = devDistProductKey(ideInfo.platformPrefix, ideInfo.baseIdePlatformPrefixForFrontend),
      modules = ideInfo.additionalModules,
      runtimeModuleRepository = ConfigurationStorage.includeRuntimeModuleRepositoryInIde(),
      legacyEngineReason = legacyEngineReason(ideInfo),
      distributions = distributions,
      indexProblem = indexProblem,
      hostPlatform = hostPlatformKey(System.getProperty("os.name"), System.getProperty("os.arch")),
    ))
  }

  /** Returns the distributions of the index of the checkout, or no distributions and the reason. No failure escapes. */
  private fun readCheckoutIndex(): Pair<List<DevDistCandidate>, String?> {
    val projectHome = try {
      PathManager.getHomeDir(false)
    }
    catch (e: RuntimeException) {
      return emptyList<DevDistCandidate>() to "the home of the test is not valid: ${e.message}"
    }
    if (projectHome == null) {
      return emptyList<DevDistCandidate>() to "the test has no '${PathManager.PROPERTY_HOME_PATH}'"
    }

    val index = try {
      readDevDistIndex(projectHome)
    }
    catch (e: IllegalStateException) {
      return emptyList<DevDistCandidate>() to e.message
    }
    if (index.distributions.isNotEmpty()) {
      return index.distributions to null
    }
    return emptyList<DevDistCandidate>() to index.missingFiles.joinToString { "the index file $it is missing" }.ifEmpty { "the index has no distributions" }
  }
}

/**
 * Returns whether a test under Bazel runfiles that declares no distribution gets [UndeclaredDevDistRunner].
 *
 * The legacy engine works under Bazel runfiles only with [bazelTargetsJsonFile], the value of
 * [BAZEL_TARGETS_JSON_FILE_PROPERTY]. A target that passes it, for example `//codeInsight:ultimate-codeInsight-tests_test`,
 * keeps the legacy engine (AT-5301).
 */
internal fun legacyEngineHasNoBazelInputs(underBazel: Boolean, bazelTargetsJsonFile: String?): Boolean =
  underBazel && bazelTargetsJsonFile == null

/**
 * Returns the message of [UndeclaredDevDistRunner]: the [product], the [modules], and what to declare in the test target.
 *
 * The recipe goes in this order:
 * - With a [legacyEngineReason], the message names the Bazel inputs of the legacy engine (AT-5301).
 * - Without [distributions], the message states [indexProblem] and the selection rule of [selectDevDist].
 * - With a matching distribution, the message names its label and its `_ide_config` sibling, ready to paste.
 * - Without a match, the message names the modules that the product does not bundle and two ways to declare them.
 *
 * A test target of the community repository cannot declare a label of the ultimate repository. The message says so for
 * the selected label of the ultimate index and for the inputs of the legacy engine.
 */
internal fun undeclaredDevDistMessage(
  ideName: String,
  product: String,
  modules: Collection<String>,
  runtimeModuleRepository: Boolean,
  legacyEngineReason: String?,
  distributions: List<DevDistCandidate>,
  indexProblem: String?,
  hostPlatform: String?,
): String = buildString {
  append("The $ideName test runs under Bazel runfiles and declares no dev distribution. ")
  append("The legacy engine cannot build the IDE, because the test target does not pass '-D$BAZEL_TARGETS_JSON_FILE_PROPERTY'.\n")
  append("  product: $product\n")
  append("  requested modules: ${modules.distinct().sorted()}\n")
  if (runtimeModuleRepository) {
    append("  runtime module repository: requested\n")
  }

  if (legacyEngineReason != null) {
    appendLegacyEngineInputs(legacyEngineReason)
    return@buildString
  }

  if (distributions.isEmpty()) {
    append("The distribution index is not readable, so this message names no label: ${indexProblem ?: "the index has no distributions"}.\n")
    append("Find the distribution in $DEV_DIST_INDEX_PATH of the checkout, or in community/$DEV_DIST_INDEX_PATH for a community product. ")
    append("Its product is '$product', and each requested module is one of its additional or bundled modules. ")
    append("Of these distributions, take the one with the fewest additional modules. Declare it in the test target:\n")
    appendDistributionDeclaration("<label>")
    return@buildString
  }

  val distribution = selectDevDist(distributions, product, modules, runtimeModuleRepository, hostPlatform)
  if (distribution != null) {
    append("The distribution index selects ${distribution.label}. Declare it in the test target:\n")
    appendDistributionDeclaration(distribution.label)
    if (distribution.ultimate && distribution.label.startsWith("//")) {
      append("The label is in the ultimate repository, and a test target of the community repository cannot declare it. ")
      append("Such a test target moves to the ultimate repository first.\n")
    }
    return@buildString
  }

  val ofProduct = distributions.filter { it.product == product }
  val bundled = ofProduct.firstOrNull()?.bundledModulesOn(hostPlatform) ?: emptySet()
  val additional = modules.filterNot { it in bundled }.distinct().sorted()
  if (ofProduct.isEmpty()) {
    append("The distribution index has no distribution of the product '$product'. ")
  }
  else {
    append("No distribution of the index has the product '$product'")
    if (additional.isNotEmpty()) {
      append(" and the additional modules $additional")
    }
    append(if (runtimeModuleRepository) " with a runtime module repository. " else ". ")
  }
  append("Declare a distribution in one of two ways (MRI-5738):\n")

  val flags = ArrayList<String>()
  if (additional.isNotEmpty()) {
    flags.add("'-Dadditional.modules=${additional.joinToString(",")}'")
  }
  if (runtimeModuleRepository) {
    flags.add("'-Dintellij.build.generate.runtime.module.repository=true'")
  }
  append("  - Add a `DevMainKt` run configuration of the product to `.idea/runConfigurations`")
  if (flags.isNotEmpty()) {
    append(", with ${flags.joinToString(" and ")}")
  }
  append(". Then run `bazel run //platform/buildScripts:plugin-model-tool`, and run the test again for the label.\n")

  append("  - In the package of the test, declare `intellij_dev_fragments_dist_ultimate` of `//build:intellij_dev_ultimate.bzl`")
  if (additional.isNotEmpty()) {
    append(" with these `additional_modules`")
  }
  append(". Its `platform_fragments` and `platform_fragment_names` come from `dev_dist_platform_fragments(product = \"$product\"")
  if (runtimeModuleRepository) {
    append(", runtime_module_repository = True")
  }
  append(")` of the same file. ")
  append("Declare `intellij_dev_dist_config` of `@community//build:intellij_dev.bzl` for it. ")
  append("`//tests/intellij.ide.starter.extended.uiAgent:ui-agent_test` is an example.\n")
}

private fun StringBuilder.appendDistributionDeclaration(label: String) {
  append("  data = [\n")
  append("      \"$label\",\n")
  append("      \"${label}_ide_config\",\n")
  append("  ],\n")
  append("  jvm_flags = [\"-D${DevIdeConfig.CONFIG_PATH_PROPERTY}=\$(rlocationpath ${label}_ide_config)\"],\n")
}

private fun StringBuilder.appendLegacyEngineInputs(reason: String) {
  append("The test needs the legacy engine, because $reason. The Bazel dev distribution does not support this yet. ")
  append("Give the legacy engine its Bazel inputs, as `//codeInsight:ultimate-codeInsight-tests_test` does (AT-5301):\n")
  append("  load(\"@community//build:bazel_targets_json.bzl\", \"current_bazel_targets_json\")\n")
  append("  load(\"@jps_dynamic_deps_ultimate//:targets.bzl\", \"ALL_ULTIMATE_TARGETS\")\n")
  append("  BAZEL_TARGETS_JSON = current_bazel_targets_json()\n")
  append("  data = ALL_ULTIMATE_TARGETS + [BAZEL_TARGETS_JSON],\n")
  append("  jvm_flags = [\"-D$BAZEL_TARGETS_JSON_FILE_PROPERTY=\$(rlocationpath %s)\" % BAZEL_TARGETS_JSON],\n")
  append("These inputs are in the ultimate repository, and the standalone community build cannot load '@jps_dynamic_deps_ultimate'. ")
  append("So a test target of the community repository moves to the ultimate repository first.\n")
}
