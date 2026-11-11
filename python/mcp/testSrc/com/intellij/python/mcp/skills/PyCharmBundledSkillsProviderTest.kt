// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.mcp.skills

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.readText

@TestApplication
class PyCharmBundledSkillsProviderTest {
  @Test
  fun codeAnalysisSkillRegistersRequiredTools() {
    val registeredSkill = PyCharmBundledSkillsProvider().registeredSkills()
      .single { it.skillId == PyCharmBundledSkillsProvider.CODE_ANALYSIS_SKILL_ID }

    assertEquals(PYCHARM_CODE_ANALYSIS_SKILL, registeredSkill)
    assertEquals(setOf("lint_files"), registeredSkill.tools)
  }

  @Test
  fun codeAnalysisSkillRequiresFinalPostStagingLint() {
    val skillContent = bundledSkillContent("pycharm-code-analysis")

    assertAll(
      { assertTrue(skillContent.contains("## Final Post-Staging Commit Gate")) },
      { assertTrue(skillContent.contains("even if the same files were analyzed earlier")) },
      { assertTrue(skillContent.contains("git diff --cached --name-only --diff-filter=ACMRT -z")) },
      { assertTrue(skillContent.contains("earlier `lint_files` calls do not replace")) },
    )
  }

  @Test
  fun airSkillsPreferTheSessionPrivateIdeMcpServer() {
    assertAll(
      { assertTrue(bundledSkillContent("pycharm-code-analysis").contains(AIR_IDE_MCP_SERVER_NAME)) },
      { assertTrue(bundledSkillContent("python-tools").contains(AIR_IDE_MCP_SERVER_NAME)) },
    )
  }

  private fun bundledSkillContent(skillName: String): String =
    pyCharmBundledSkillsRoot().resolve("$skillName/SKILL.md").readText()

  private companion object {
    const val AIR_IDE_MCP_SERVER_NAME = "jetbrains_air_ide"
  }
}
