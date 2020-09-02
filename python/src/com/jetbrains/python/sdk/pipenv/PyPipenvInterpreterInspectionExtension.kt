// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInterpreterInspectionExtension
import com.jetbrains.python.inspections.PyInterpreterInspectionQuickFixData
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pipenv.UsePipEnvQuickFix.Companion.isApplicable


class PyPipenvInterpreterInspectionExtension : PyInterpreterInspectionExtension {
  override fun createMissingSdkFix(module: Module, file: PyFile): PyInterpreterInspectionQuickFixData? = when {
    isApplicable(module) -> PyInterpreterInspectionQuickFixData(
      UsePipEnvQuickFix(null, module), PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.project"))
    else -> null
  }

  override fun createEnvironmentAssociationFix(module: Module, sdk: Sdk, isPyCharm: Boolean, associatedModulePath: @NlsSafe String?): PyInterpreterInspectionQuickFixData? {
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
      return PyInterpreterInspectionQuickFixData(UsePipEnvQuickFix(sdk, module), message)
    }
    return null
  }

  override fun createInstallPackagesQuickFix(module: Module): LocalQuickFix? {
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return null
    return if (sdk.isPipEnv) PipEnvInstallQuickFix() else null
  }
}