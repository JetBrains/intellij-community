// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.idea.maven.execution.MavenRunner

/**
 * The command sets delegatedBuild (Maven if true and Idea if false)
 * Syntax: %setMavenDelegatedBuild [delegatedBuild]
 */
class SetMavenDelegatedBuildCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "setMavenDelegatedBuild"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val delegatedBuild = extractCommandArgument(prefix).toBoolean()
    MavenRunner.getInstance(context.project).state.isDelegateBuildToMaven = delegatedBuild
  }

  override fun getName(): String {
    return NAME
  }
}