// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsActions
import com.intellij.psi.PsiFile
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcome
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import com.intellij.pycharm.community.ide.impl.miscProject.TemplateFileName
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import javax.swing.Icon

object MiscScriptFileType : MiscFileType {
  override val title: @NlsActions.ActionText String = PyCharmCommunityCustomizationBundle.message("misc.script.text")
  override val icon: Icon = PythonPsiApiIcons.Python_32x32
  override val fileName: TemplateFileName = TemplateFileName.parse("script.py")

  override suspend fun fillFile(file: PsiFile, sdk: Sdk) {
    writeAction {
      PyWelcome.writeText(file)
    }
  }
}