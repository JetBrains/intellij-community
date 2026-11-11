// Copyright 2000-2026 JetBrains s.r.o.
package com.intellij.mcpserver

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
interface McpServerConsentUi {
  val asksOnHeadlessHost: Boolean get() = false
  suspend fun askConsent(project: Project?): Boolean?
  suspend fun saveDecision()
}
