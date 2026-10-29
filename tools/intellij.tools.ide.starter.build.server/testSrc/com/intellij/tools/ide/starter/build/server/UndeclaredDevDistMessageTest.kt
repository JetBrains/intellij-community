// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UndeclaredDevDistMessageTest {
  private val distributions = listOf(
    candidate("idea", bundledModules = listOf("intellij.java.plugin")),
    candidate("idea_perf", additionalModules = listOf("intellij.performanceTesting"), bundledModules = listOf("intellij.java.plugin")),
    candidate("pycharm", product = "Python"),
  )

  @Test
  fun `matched distribution gives the declaration to paste`() {
    assertEquals(
      """
      |The IntelliJ IDEA test runs under Bazel runfiles and declares no dev distribution. The legacy engine cannot build the IDE, because the test target does not pass '-Dintellij.build.bazel.targets.json.file'.
      |  product: idea
      |  requested modules: [intellij.java.plugin, intellij.performanceTesting]
      |The distribution index selects //build:idea_perf_dist. Declare it in the test target:
      |  data = [
      |      "//build:idea_perf_dist",
      |      "//build:idea_perf_dist_ide_config",
      |  ],
      |  jvm_flags = ["-Didea.ide.config.path=${'$'}(rlocationpath //build:idea_perf_dist_ide_config)"],
      |
      """.trimMargin(),
      message(modules = listOf("intellij.performanceTesting", "intellij.java.plugin")),
    )
  }

  @Test
  fun `label of the ultimate index tells a community test target to move`() {
    val text = message(distributions = listOf(candidate("idea", ultimate = true)))
    assertTrue(text.contains("      \"//build:idea_dist_ide_config\",\n"), text)
    assertTrue(text.contains("The label is in the ultimate repository, and a test target of the community repository cannot declare it. "), text)
    assertTrue(text.contains("Such a test target moves to the ultimate repository first.\n"), text)
    assertFalse(text.contains("is_inside_ultimate"), text)
  }

  @Test
  fun `community label gets no note on the repository`() {
    // The `//build` label of a standalone community checkout and the `@community` label of the monorepo work in both halves.
    assertFalse(message().contains("ultimate repository"))
    val community = candidate("idea_community", product = "Idea", label = "@community//build:idea_community_dist")
    val text = message(product = "Idea", distributions = listOf(community))
    assertTrue(text.contains("The distribution index selects @community//build:idea_community_dist. "), text)
    assertFalse(text.contains("ultimate repository"), text)
  }

  @Test
  fun `unreadable index gives the selection rule and no label`() {
    assertEquals(
      """
      |The IntelliJ IDEA test runs under Bazel runfiles and declares no dev distribution. The legacy engine cannot build the IDE, because the test target does not pass '-Dintellij.build.bazel.targets.json.file'.
      |  product: idea
      |  requested modules: []
      |The distribution index is not readable, so this message names no label: the index file /tmp/home/build/dev-dist-distributions.json is missing.
      |Find the distribution in build/dev-dist-distributions.json of the checkout, or in community/build/dev-dist-distributions.json for a community product. Its product is 'idea', and each requested module is one of its additional or bundled modules. Of these distributions, take the one with the fewest additional modules. Declare it in the test target:
      |  data = [
      |      "<label>",
      |      "<label>_ide_config",
      |  ],
      |  jvm_flags = ["-Didea.ide.config.path=${'$'}(rlocationpath <label>_ide_config)"],
      |
      """.trimMargin(),
      message(distributions = emptyList(), indexProblem = "the index file /tmp/home/build/dev-dist-distributions.json is missing"),
    )
  }

  @Test
  fun `legacy engine reason names the AT-5301 inputs instead of a distribution`() {
    val text = message(product = "Rider", legacyEngineReason = "the Bazel dev distribution of 'Rider' has known gaps")
    assertTrue(text.contains("  product: Rider\n"), text)
    assertTrue(text.contains("The test needs the legacy engine, because the Bazel dev distribution of 'Rider' has known gaps. "), text)
    assertTrue(text.contains("  data = ALL_ULTIMATE_TARGETS + [BAZEL_TARGETS_JSON],\n"), text)
    assertTrue(text.contains("  jvm_flags = [\"-Dintellij.build.bazel.targets.json.file=${'$'}(rlocationpath %s)\" % BAZEL_TARGETS_JSON],\n"), text)
    assertFalse(text.contains("_ide_config"), text)
    assertTrue(text.contains("the standalone community build cannot load '@jps_dynamic_deps_ultimate'. "), text)
  }

  @Test
  fun `no match names the modules that the product does not bundle`() {
    val text = message(modules = listOf("intellij.java.plugin", "intellij.space", "intellij.go"))
    assertTrue(text.contains("No distribution of the index has the product 'idea' and the additional modules [intellij.go, intellij.space]. "), text)
    assertTrue(text.contains("'-Dadditional.modules=intellij.go,intellij.space'. Then run `bazel run //platform/buildScripts:plugin-model-tool`"), text)
    assertTrue(text.contains("`intellij_dev_fragments_dist_ultimate` of `//build:intellij_dev_ultimate.bzl` with these `additional_modules`. "), text)
    assertTrue(text.contains("come from `dev_dist_platform_fragments(product = \"idea\")` of the same file. "), text)
    assertTrue(text.contains("`intellij_dev_dist_config` of `@community//build:intellij_dev.bzl`"), text)
    assertFalse(text.contains("runtime.module.repository"), text)
  }

  @Test
  fun `no match with a runtime module repository names the repository flag`() {
    val text = message(runtimeModuleRepository = true)
    assertTrue(text.contains("  runtime module repository: requested\n"), text)
    assertTrue(text.contains("No distribution of the index has the product 'idea' with a runtime module repository. "), text)
    assertTrue(text.contains("`.idea/runConfigurations`, with '-Dintellij.build.generate.runtime.module.repository=true'. "), text)
    assertTrue(text.contains("`dev_dist_platform_fragments(product = \"idea\", runtime_module_repository = True)`"), text)
    assertFalse(text.contains("additional"), text)
  }

  @Test
  fun `product without a distribution is named`() {
    val text = message(product = "GoLand", modules = listOf("intellij.go"))
    assertTrue(text.contains("The distribution index has no distribution of the product 'GoLand'. "), text)
    assertTrue(text.contains("'-Dadditional.modules=intellij.go'"), text)
  }

  private fun message(
    product: String = "idea",
    modules: List<String> = emptyList(),
    runtimeModuleRepository: Boolean = false,
    legacyEngineReason: String? = null,
    distributions: List<DevDistCandidate> = this.distributions,
    indexProblem: String? = null,
  ): String = undeclaredDevDistMessage(
    ideName = "IntelliJ IDEA",
    product = product,
    modules = modules,
    runtimeModuleRepository = runtimeModuleRepository,
    legacyEngineReason = legacyEngineReason,
    distributions = distributions,
    indexProblem = indexProblem,
    hostPlatform = "linux_x64",
  )
}
