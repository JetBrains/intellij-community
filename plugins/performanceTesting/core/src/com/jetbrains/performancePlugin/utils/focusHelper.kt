// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils

import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TypingTarget
import com.intellij.util.ui.UIUtil
import com.jetbrains.performancePlugin.commands.takeScreenshotOfAllWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.KeyboardFocusManager

class FocusHelper

suspend fun findTypingTarget(project: Project): TypingTarget? = withContext<TypingTarget?>(Dispatchers.EDT) {
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    var each = focusOwner
    while (each != null) {
      if (each is TypingTarget) {
        return@withContext each
      }
      each = each.parent
    }

    val editorTracker = EditorTracker.getInstance(project)
    val message = "There is no focus in editor (focusOwner=${
      focusOwner?.let {
        UIUtil.uiParents(it, false).joinToString(separator = "\n  ->\n")
      }
    },\neditorTracker=$editorTracker)"

    takeScreenshotOfAllWindows("no-focus-in-editor")

    if (focusOwner == null) {
      val activeEditors = editorTracker.activeEditors
      activeEditors.firstOrNull()?.let {
        it.contentComponent.requestFocusInWindow()
        logger<FocusHelper>().warn(message)
        return@withContext null
      }
    }
    throw IllegalStateException(message)
  }