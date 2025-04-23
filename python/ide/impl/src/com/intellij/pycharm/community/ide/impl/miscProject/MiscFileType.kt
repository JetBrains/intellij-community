// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsActions
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * On a welcome screen user clicks in [icon] to get a project with [fileName] template filled by [fillFile]
 */
interface MiscFileType {
  companion object {
    val EP: ExtensionPointName<MiscFileType> = ExtensionPointName.create("Pythonid.miscFileType")
  }

  val title: @NlsActions.ActionText String
  val icon: Icon
  val fileName: TemplateFileName

  /**
   * Do not change to not break the statistics.
   */
  val technicalNameForStatistics: @NonNls String

  suspend fun fillFile(file: PsiFile, sdk: Sdk)
}