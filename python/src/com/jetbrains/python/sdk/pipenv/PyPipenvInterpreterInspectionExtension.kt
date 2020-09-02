// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInterpreterInspectionExtension
import com.jetbrains.python.inspections.PyInterpreterInspectionQuickFixData
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pipenv.UsePipEnvQuickFix.Companion.isApplicable


class PyPipenvInterpreterInspectionExtension : PyInterpreterInspectionExtension {
  override fun createMissingSdkFix(module: Module, file: PyFile): PyInterpreterInspectionQuickFixData? = when {
    isApplicable(module) -> PyInterpreterInspectionQuickFixData(
      UsePipEnvQuickFix(null, module), PyPsiBundle.message("python.sdk.no.interpreter.configured.owner", "project"))
    else -> null
  }

  override fun createEnvironmentAssociationFix(module: Module, sdk: Sdk, isPyCharm: Boolean, associatedModulePath: String?): PyInterpreterInspectionQuickFixData? {
    if (sdk.isPipEnv) {
      val message = when {
        associatedModulePath != null -> when {
          isPyCharm -> "Pipenv interpreter is associated with another project: '$associatedModulePath'"
          else -> "Pipenv interpreter is associated with another module: '$associatedModulePath'"
        }
        else -> when {
          isPyCharm -> "Pipenv interpreter is not associated with any project"
          else -> "Pipenv interpreter is not associated with any module"
        }
      }
      return PyInterpreterInspectionQuickFixData(UsePipEnvQuickFix(sdk, module), message)
    }
    return null
  }

  override fun createInstallPackagesQuickFix(module: Module): LocalQuickFix? {
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return null
    return if (sdk.isPipEnv) PipEnvInstallQuickFix() else null
  }
}