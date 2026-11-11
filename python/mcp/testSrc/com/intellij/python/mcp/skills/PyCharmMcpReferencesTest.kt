// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.mcp.skills

import com.intellij.mcpserver.skills.BundledSkill
import com.intellij.mcpserver.testFramework.skills.BundledSkillFiles
import com.intellij.mcpserver.testFramework.skills.BundledSkillMetadataUpdater
import com.intellij.mcpserver.testFramework.skills.McpReferencesTestBase
import com.intellij.openapi.application.PathManager
import com.intellij.python.mcp.environment.PythonEnvironmentMcpToolset
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class PyCharmMcpReferencesTest : McpReferencesTestBase() {
  override val bundledSkillsRoot: Path = pyCharmBundledSkillsRoot()

  override val registeredSkills: List<BundledSkill> = PYCHARM_REGISTERED_SKILLS

  /**
   * Only `pycharm-code-analysis` ships a generated tree. `python-tools` documents its tools in `SKILL.md` and ships no
   * `reference/` tree. The `jupyter` tree is written by hand, with call examples that the generator does not produce.
   */
  override val generatedReferenceSkillIds: Set<String> = setOf(PyCharmBundledSkillsProvider.CODE_ANALYSIS_SKILL_ID)

  override fun availableTools() = buildList {
    addAll(toolsetTools(PythonEnvironmentMcpToolset()))
    addAll(super.availableTools())
  }

  /** The evaluation pipeline sees a changed skill only through the hash and the version in its `metadata.json`. */
  @Test
  fun metadataRecordsTheSkillContent() {
    val skillDirectories = BundledSkillFiles.skillDirectories(bundledSkillsRoot)
    BundledSkillMetadataUpdater.regenerateIfRequested(skillDirectories)
    val instructions = BundledSkillMetadataUpdater.regenerateInstructions("intellij.python.mcp.tests", PyCharmMcpReferencesTest::class)
    skillDirectories.forEach { BundledSkillMetadataUpdater.assertUpToDate(it, instructions) }
  }
}

/** The skill root of `intellij.python.mcp`, found from the community home, so the intellij-community checkout finds it too. */
internal fun pyCharmBundledSkillsRoot(): Path = Path.of(PathManager.getCommunityHomePath(), "python/mcp/resources/bundledSkills")

fun main() {
  PyCharmMcpReferencesTest().regenerateMcpReferences()
}
