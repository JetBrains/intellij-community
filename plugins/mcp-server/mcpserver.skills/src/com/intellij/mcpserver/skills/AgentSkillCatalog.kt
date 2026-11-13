// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.skills

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The skills that a skill manager, such as Air, has installed and enabled.
 *
 * A surface outside the skill manager, for example the Rider AI agent setup widget, reads the skills here.
 * Then it shows the same skills as the Skills list of the skill manager.
 *
 * The skill manager registers the implementation as an application service. Without a skill manager, [getInstance] returns `null`.
 */
@ApiStatus.Experimental
interface AgentSkillCatalog {
  /**
   * The enabled skills of the working directory of [context], one for each row of the Skills list.
   *
   * The skills come from the roots and the settings of that working directory, also when [SkillWorkspaceContext.project] is `null`.
   * Reads the disk on `Dispatchers.IO`. A skill that is installed in several roots is in the list once.
   */
  suspend fun enabledSkills(context: SkillWorkspaceContext): List<EnabledAgentSkill>

  companion object {
    /** The catalog of the loaded skill manager, or `null` when no skill manager is loaded. */
    fun getInstance(): AgentSkillCatalog? = ApplicationManager.getApplication().serviceOrNull()
  }
}

/** One enabled skill of an [AgentSkillCatalog]. */
@ApiStatus.Experimental
class EnabledAgentSkill(
  /** The directory with the `SKILL.md` of an enabled copy. The Skills list can show another, disabled copy of the same skill. */
  val directory: Path,
  /** The `owner/repository` of each GitHub registry that installed an enabled copy of the skill. Empty for a skill from another source. */
  val registrySources: Set<String>,
)
