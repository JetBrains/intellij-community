// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.toolsets.skills

import com.intellij.mcpserver.skills.BundledSkill
import com.intellij.mcpserver.skills.BundledSkillsProvider
import com.intellij.mcpserver.skills.SkillWorkspaceContext
import com.intellij.util.PlatformUtils

internal class JvmCodeBundledSkillsProvider : BundledSkillsProvider {
  override suspend fun isProviderAvailable(context: SkillWorkspaceContext): Boolean = PlatformUtils.isIntelliJ()

  override fun registeredSkills(): List<BundledSkill> = JVM_CODE_REGISTERED_SKILLS

  companion object {
    const val REFACTOR_JVM_SKILL_ID: String = "refactor-jvm-jb"
    const val JVM_IMPORTS_SKILL_ID: String = "jvm-imports"
  }
}

internal val JVM_IMPORTS_SKILL: BundledSkill = BundledSkill(
  skillId = JvmCodeBundledSkillsProvider.JVM_IMPORTS_SKILL_ID,
  tools = setOf("add_missing_imports"),
  requiredTools = setOf("add_missing_imports"),
  enabledRegistryKey = "llm.agents.contrib.idea.jvm.imports.skill.enabled",
)

internal val REFACTOR_JVM_SKILL: BundledSkill = BundledSkill(
  skillId = JvmCodeBundledSkillsProvider.REFACTOR_JVM_SKILL_ID,
  tools = setOf(
    "rename_refactoring",
  ),
  requiredTools = setOf("rename_refactoring"),
  enabledRegistryKey = "llm.agents.contrib.idea.refactor.jvm.skill.enabled",
)

internal val JVM_CODE_REGISTERED_SKILLS: List<BundledSkill> = listOf(JVM_IMPORTS_SKILL, REFACTOR_JVM_SKILL)
