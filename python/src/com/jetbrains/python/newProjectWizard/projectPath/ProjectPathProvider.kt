// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.projectPath

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.CHECK_NO_WHITESPACES
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.builder.trimmedTextValidation
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.newProjectWizard.deniedCharsValidation
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathProvider.Companion.bindProjectName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JTextField
import kotlin.reflect.KMutableProperty0

/**
 * API to access project path field.
 * To be used with [bindProjectName]
 */
interface ProjectPathProvider {
  /**
   * Calls [code] as long as project path field is visible on each project path change.
   * this code accepts project path name
   */
  @RequiresEdt
  fun onProjectFileNameChanged(code: suspend (projectFileName: @NlsSafe String) -> Unit)

  /**
   * [ProjectPathFlows] made out of project path
   */
  val projectPathFlows: ProjectPathFlows

  companion object {

    /**
     * Binds [com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificSettings] property to the certain cell and updates it each time project path is changes
     * [deniedChars] are added to validation and replaced with `_`
     */
    @JvmOverloads
    @RequiresEdt
    fun Cell<JTextField>.bindProjectName(projectPathProvider: ProjectPathProvider, property: KMutableProperty0<@NlsSafe String>, deniedChars: Regex? = null) = apply {
      trimmedTextValidation(CHECK_NON_EMPTY, CHECK_NO_WHITESPACES)
      deniedChars?.let {
        trimmedTextValidation(deniedCharsValidation(it))
      }
      bindText(property)
      projectPathProvider.onProjectFileNameChanged {
        withContext(Dispatchers.EDT) {
          text(deniedChars?.replace(it, "_") ?: it)
        }
      }
    }
  }
}