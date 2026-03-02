// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.python.community.impl.pipenv.PIPENV_ICON
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PyInterpreterInspectionQuickFixData
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.pipenv.quickFixes.PipEnvAssociationQuickFix
import org.jdom.Element
import javax.swing.Icon

internal class PyPipEnvSdkProvider : PySdkProvider {

  override fun getSdkAdditionalText(sdk: Sdk): String? = if (sdk.isPipEnv) sdk.versionString else null

  override fun getSdkIcon(sdk: Sdk): Icon? = if (sdk.isPipEnv) PIPENV_ICON else null

  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return PyPipEnvSdkAdditionalData.load(element)
  }

  override fun createEnvironmentAssociationFix(
      sdk: Sdk,
    isPyCharm: Boolean,
    associatedModulePath: String?,
  ): PyInterpreterInspectionQuickFixData? {
    if (sdk.isPipEnv) {
      val message = when {
        associatedModulePath != null -> when {
          isPyCharm -> PyBundle.message("python.sdk.pipenv.associated.with.another.project", associatedModulePath)
          else -> PyBundle.message("python.sdk.pipenv.associated.with.another.module", associatedModulePath)
        }
        else -> when {
          isPyCharm -> PyBundle.message("python.sdk.pipenv.not.associated.with.any.project")
          else -> PyBundle.message("python.sdk.pipenv.not.associated.with.any.module")
        }
      }
      return PyInterpreterInspectionQuickFixData(PipEnvAssociationQuickFix(), message)
    }
    return null
  }

}