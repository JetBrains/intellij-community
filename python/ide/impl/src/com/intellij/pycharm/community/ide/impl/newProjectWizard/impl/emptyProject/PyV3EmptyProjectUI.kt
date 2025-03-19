// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.impl.emptyProject

import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.newProjectWizard.PyV3ProjectTypeSpecificUI
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.PyBundle

internal object PyV3EmptyProjectUI : PyV3ProjectTypeSpecificUI<PyV3EmptyProjectSettings> {
  override fun configureUpperPanel(settings: PyV3EmptyProjectSettings, checkBoxRow: Row, belowCheckBoxes: Panel) {
   checkBoxRow.checkBox(PyBundle.message("new.project.welcome")).bindSelected(settings::generateWelcomeScript)
  }
}