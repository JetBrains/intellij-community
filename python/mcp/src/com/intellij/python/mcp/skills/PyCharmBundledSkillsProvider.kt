// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.mcp.skills

import com.intellij.mcpserver.skills.BundledSkill
import com.intellij.mcpserver.skills.BundledSkillsProvider
import com.intellij.mcpserver.skills.SkillWorkspaceContext
import com.intellij.python.mcp.environment.GET_PYTHON_ENVIRONMENT_TOOL
import com.intellij.python.mcp.environment.PYTHON_ENVIRONMENT_TOOLS
import com.intellij.util.PlatformUtils

internal class PyCharmBundledSkillsProvider : BundledSkillsProvider {

  override suspend fun isProviderAvailable(context: SkillWorkspaceContext): Boolean {
    return PlatformUtils.isPyCharm()
  }

  override fun registeredSkills(): List<BundledSkill> = PYCHARM_REGISTERED_SKILLS

  companion object {
    const val PYTHON_TOOLS_SKILL_ID: String = "python-tools"
    const val CODE_ANALYSIS_SKILL_ID: String = "pycharm-code-analysis"
  }
}

internal val PYTHON_TOOLS_SKILL: BundledSkill = BundledSkill(
  skillId = PyCharmBundledSkillsProvider.PYTHON_TOOLS_SKILL_ID,
  tools = PYTHON_ENVIRONMENT_TOOLS,
  requiredTools = setOf(GET_PYTHON_ENVIRONMENT_TOOL),
  enabledRegistryKey = "llm.agents.contrib.pycharm.python.tools.skill.enabled",
)

internal val PYCHARM_CODE_ANALYSIS_SKILL: BundledSkill = BundledSkill(
  skillId = PyCharmBundledSkillsProvider.CODE_ANALYSIS_SKILL_ID,
  tools = setOf("lint_files"),
  requiredTools = setOf("lint_files"),
  enabledRegistryKey = "llm.agents.contrib.pycharm.code.analysis.skill.enabled",
)

internal val PYCHARM_REGISTERED_SKILLS: List<BundledSkill> = listOf(PYTHON_TOOLS_SKILL, PYCHARM_CODE_ANALYSIS_SKILL)
