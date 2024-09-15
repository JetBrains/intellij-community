// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.CHECK_NO_WHITESPACES
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.trimmedTextValidation
import com.jetbrains.python.ui.flow.bindText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.nio.file.Path
import javax.swing.JTextField
import kotlin.io.path.name
import kotlin.reflect.KMutableProperty0

/**
 * "New project path" field value.
 * This flow is used by [PyV3ProjectTypeSpecificUI.advancedSettings] to [bindProjectName]
 */
typealias ProjectPathFlow = StateFlow<Path>


/**
 * Binds [PyV3ProjectTypeSpecificSettings] property to the certain cell and updates it each time project path is changes
 */
fun Cell<JTextField>.bindProjectName(projectPath: ProjectPathFlow, property: KMutableProperty0<@NlsSafe String>) = apply {
  property.set(getProjectNameFromPath(projectPath.value)) // set the initial value before we get any new value
  trimmedTextValidation(CHECK_NON_EMPTY, CHECK_NO_WHITESPACES)
  bindText(property)
  bindText(projectPath.map { getProjectNameFromPath(it) })
}

/**
 *  Projects like Django are named after the last part of the path
 */
private fun getProjectNameFromPath(path: Path): @NlsSafe String = path.name