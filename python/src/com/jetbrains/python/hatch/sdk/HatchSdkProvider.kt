// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolder
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.jetbrains.python.sdk.PyInterpreterInspectionQuickFixData
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import org.jdom.Element
import javax.swing.Icon

class HatchSdkProvider : PySdkProvider {
  override fun getSdkAdditionalText(sdk: Sdk): String? = null

  override fun getSdkIcon(sdk: Sdk): Icon? = if (sdk.isHatch) PythonHatchIcons.Logo else null

  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? = HatchSdkAdditionalData.createIfHatch(element)

  override fun createEnvironmentAssociationFix(
    module: Module, sdk: Sdk, isPyCharm: Boolean, associatedModulePath: @NlsSafe String?,
  ): PyInterpreterInspectionQuickFixData? = null

  override fun createInstallPackagesQuickFix(module: Module): LocalQuickFix? = null

  override fun createNewEnvironmentPanel(project: Project?, module: Module?, existingSdks: List<Sdk>, newProjectPath: String?, context: UserDataHolder): PyAddNewEnvPanel? = null
}