package com.intellij.mcpserver.skills

import org.jetbrains.annotations.ApiStatus

/** An agent that can receive a bundled skill. */
@ApiStatus.Experimental
enum class AgentSkillTarget {
  CLAUDE, CODEX, JUNIE, OTHER;

  companion object {
    /** All targets. */
    val ALL: Set<AgentSkillTarget> = entries.toSet()
  }
}
