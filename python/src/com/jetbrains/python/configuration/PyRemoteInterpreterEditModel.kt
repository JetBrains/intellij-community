// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.onFailure
import com.jetbrains.python.sdk.renameSdk
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * State and apply flow for [PyRemoteInterpreterEditDialog].
 *
 * Owns the single editable field the dialog contributes on top of the target configurable —
 * [name]. The target configurable is a separate `Configurable`; the dialog wires it in unchanged.
 *
 * The set of registered SDK names is captured once, at construction time, so the DSL validation
 * runs a set lookup on every keystroke instead of walking [ProjectJdkTable.getAllJdks].
 */
@ApiStatus.Internal
class PyRemoteInterpreterEditModel(
  private val project: Project,
  private val sdk: Sdk,
) {

  val initialName: String = sdk.name

  private val existingSdkNames: Set<String> =
    ProjectJdkTable.getInstance().allJdks.mapTo(HashSet()) { it.name }

  var name: String = initialName

  /**
   * `null` when [candidate] is a valid new name, or a localized error message otherwise. Used both
   * by the DSL `validationOnInput` (keystroke feedback) and `validationOnApply` (submit guard).
   */
  fun validateName(candidate: String): @Nls String? {
    val trimmed = candidate.trim()
    if (trimmed.isEmpty()) return PyBundle.message("rename.python.interpreter.dialog.provide.name.error.text")
    if (trimmed == initialName) return null
    if (trimmed in existingSdkNames) return PyBundle.message("rename.python.interpreter.name.already.exists.error.text")
    return null
  }

  /**
   * Persists the rename when [name] changed. Runs a write action on the EDT.
   *
   * Throws [ConfigurationException] when `renameSdk` fails — usually the target name got taken
   * between validation and OK by a parallel Settings tab or a JDK-table listener elsewhere in the
   * app. The caller ([PyRemoteInterpreterEditDialog.doOKAction]) catches the exception, keeps the
   * dialog open, and surfaces the error message so the user sees why OK didn't close and can pick
   * a different name.
   */
  @Throws(ConfigurationException::class)
  fun apply(): Boolean {
    val newName = name.trim()
    if (newName == initialName) return false
    var errorMessage: @Nls String? = null
    ApplicationManager.getApplication().runWriteAction {
      project.renameSdk(initialName, newName).onFailure { failure ->
        errorMessage = failure.message
        return@runWriteAction
      }
      if (sdk.name == initialName) {
        sdk.sdkModificator.let {
          it.name = newName
          it.commitChanges()
        }
      }
    }
    errorMessage?.let { throw ConfigurationException(it) }
    return true
  }
}
