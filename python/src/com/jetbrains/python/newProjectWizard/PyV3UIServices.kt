// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.project.Project
import com.jetbrains.python.errorProcessing.ErrorSink
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

/**
 * Various services that aren't accessible in tests.
 * There is [com.jetbrains.python.newProjectWizard.impl.PyV3UIServicesProd] used in production, and you should never
 * touch it, unless you test your [PyV3ProjectBaseGenerator]. In this case, use [PyV3ProjectBaseGenerator.setUiServices] in tests.
 * There is an inheritor
 */
interface PyV3UIServices {
  /**
   * Runs [code] when [component] is visible
   */
  fun runWhenComponentDisplayed(component: JComponent, code: suspend CoroutineScope.() -> Unit)

  /**
   * Send user errors here: they will arrive to user
   */
  val errorSink: ErrorSink

  /**
   * Expand a project tree on the left side of IDE
   */
  suspend fun expandProjectTreeView(project: Project)
}