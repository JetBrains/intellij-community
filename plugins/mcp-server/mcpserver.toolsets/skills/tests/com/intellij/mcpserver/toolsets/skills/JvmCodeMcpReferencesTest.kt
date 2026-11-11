// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.toolsets.skills

import com.intellij.mcpserver.skills.BundledSkill
import com.intellij.mcpserver.testFramework.skills.BundledSkillFiles
import com.intellij.mcpserver.testFramework.skills.BundledSkillMetadataUpdater
import com.intellij.mcpserver.testFramework.skills.McpReferencesTestBase
import com.intellij.mcpserver.toolsets.general.ImportsToolset
import com.intellij.mcpserver.toolsets.general.RefactoringToolset
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class JvmCodeMcpReferencesTest : McpReferencesTestBase() {
  override val bundledSkillsRoot: Path =
    Path.of(PathManager.getCommunityHomePath(), "plugins/mcp-server/mcpserver.toolsets/skills/resources/bundledSkills")

  override val registeredSkills: List<BundledSkill> = JVM_CODE_REGISTERED_SKILLS

  override val generatedReferenceSkillIds: Set<String> = setOf(
    JvmCodeBundledSkillsProvider.JVM_IMPORTS_SKILL_ID,
    JvmCodeBundledSkillsProvider.REFACTOR_JVM_SKILL_ID,
  )

  override fun availableTools() = buildList {
    addAll(toolsetTools(ImportsToolset()))
    addAll(toolsetTools(RefactoringToolset()))
    addAll(super.availableTools())
  }

  @Test
  fun everySkillRequiresIdeTools() {
    assertEquals(emptyList<String>(), JVM_CODE_REGISTERED_SKILLS.filter { it.requiredTools.isEmpty() }.map(BundledSkill::skillId))
  }

  /** The evaluation pipeline sees a changed skill only through the hash and the version in its `metadata.json`. */
  @Test
  fun metadataRecordsTheSkillContent() {
    val skillDirectories = BundledSkillFiles.skillDirectories(bundledSkillsRoot)
    BundledSkillMetadataUpdater.regenerateIfRequested(skillDirectories)
    val instructions = BundledSkillMetadataUpdater.regenerateInstructions("intellij.mcpserver.toolsets.skills.tests", JvmCodeMcpReferencesTest::class)
    skillDirectories.forEach { BundledSkillMetadataUpdater.assertUpToDate(it, instructions) }
  }
}

fun main() {
  JvmCodeMcpReferencesTest().regenerateMcpReferences()
}
