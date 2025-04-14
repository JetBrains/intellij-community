// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.projectRoots.impl.jdkDownloader.RuntimeChooserPaths
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import org.jetbrains.annotations.NonNls
import kotlin.io.path.Path

/**
 * Command installs new JBR (don't forget to reset!!!)
 * Usage: %installCustomJBR <full_path_to_new_JBR>
 */
class InstallCustomJBR(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "installCustomJBR"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val jbrPath = extractCommandArgument(PREFIX)
    RuntimeChooserPaths().installCustomJdk("customJBR"){ Path(jbrPath) }
  }
}