// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.parser.icons.PythonParserIcons
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("DEPRECATION_ERROR")
@Deprecated(
  "Custom Python SDKs support was removed from python plugin for IDEA because of UI/UX unification with PyCharm",
  level = DeprecationLevel.ERROR
)
abstract class PyAddSdkPanel : JPanel(), PyAddSdkView {
  override val component: Component
    get() = this

  abstract override val panelName: String
  override val icon: Icon = PythonParserIcons.PythonFile
  open val sdk: Sdk? = null
  open val nameExtensionComponent: JComponent? = null
  open var newProjectPath: String? = null

  @RequiresEdt
  override fun getOrCreateSdk(): Sdk? = sdk

  override fun validateAll(): List<ValidationInfo> = emptyList()

  open fun addChangeListener(listener: Runnable) {}
}

/**
 * Obtains a list of sdk on a pool using [sdkObtainer], then fills [sdkComboBox] and calls [onAdded] on the EDT.
 */
@Deprecated(
  message ="this ComboBox was designed only for plain venv, not tool-specific pythons and doesn't supported anymore",
  replaceWith = ReplaceWith("PythonInterpreterComboBox")
)
fun addInterpretersAsync(
  sdkComboBox: PySdkPathChoosingComboBox,
  sdkObtainer: () -> List<Sdk>,
  onAdded: (List<Sdk>) -> Unit,
) {
  addInterpretersToComboAsync(sdkComboBox, sdkObtainer, onAdded)
}