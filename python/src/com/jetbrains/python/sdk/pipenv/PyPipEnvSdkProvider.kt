// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.sdk.PyInterpreterInspectionQuickFixData
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.pipenv.quickFixes.PipEnvAssociationQuickFix
import org.jdom.Element
import javax.swing.Icon

class PyPipEnvSdkProvider : PySdkProvider {

  override fun getSdkAdditionalText(sdk: Sdk): String? = if (sdk.isPipEnv) sdk.versionString else null

  override fun getSdkIcon(sdk: Sdk): Icon? = if (sdk.isPipEnv) PIPENV_ICON else null

  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return PyPipEnvSdkAdditionalData.load(element)
  }

  override fun createEnvironmentAssociationFix(
    module: Module,
    sdk: Sdk,
    isPyCharm: Boolean,
    associatedModulePath: String?,
  ): PyInterpreterInspectionQuickFixData? {
    if (sdk.isPipEnv) {
      val message = when {
        associatedModulePath != null -> when {
          isPyCharm -> PyPsiBundle.message("INSP.interpreter.pipenv.interpreter.associated.with.another.project", associatedModulePath)
          else -> PyPsiBundle.message("INSP.interpreter.pipenv.interpreter.associated.with.another.module", associatedModulePath)
        }
        else -> when {
          isPyCharm -> PyPsiBundle.message("INSP.interpreter.pipenv.interpreter.not.associated.with.any.project")
          else -> PyPsiBundle.message("INSP.interpreter.pipenv.interpreter.not.associated.with.any.module")
        }
      }
      return PyInterpreterInspectionQuickFixData(PipEnvAssociationQuickFix(), message)
    }
    return null
  }

}