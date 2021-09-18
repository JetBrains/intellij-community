package com.jetbrains.python.sdk.poetry

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.sdk.PyInterpreterInspectionQuickFixData
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import org.jdom.Element
import javax.swing.Icon

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PoetrySdkProvider : PySdkProvider {
  override fun createEnvironmentAssociationFix(module: Module,
                                               sdk: Sdk,
                                               isPyCharm: Boolean,
                                               associatedModulePath: String?): PyInterpreterInspectionQuickFixData? {
    if (sdk.isPoetry) {
      val message = when {
        associatedModulePath != null -> when {
          isPyCharm -> "Poetry interpreter is associated with another project: $associatedModulePath"
          else -> "Poetry interpreter is associated with another module: $associatedModulePath"
        }
        else -> when {
          isPyCharm -> "Poetry interpreter is not associated with any project"
          else -> "Poetry interpreter is not associated with any module"
        }
      }
      return PyInterpreterInspectionQuickFixData(UsePoetryQuickFix(sdk, module), message)
    }
    return null
  }

  override fun createInstallPackagesQuickFix(module: Module): LocalQuickFix? {
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return null
    return if (sdk.isPoetry) PoetryInstallQuickFix() else null
  }

  override fun createNewEnvironmentPanel(project: Project?,
                                         module: Module?,
                                         existingSdks: List<Sdk>,
                                         newProjectPath: String?,
                                         context: UserDataHolder): PyAddNewEnvPanel {
    return PyAddNewPoetryPanel(null, null, existingSdks, newProjectPath, context)
  }

  override fun getSdkAdditionalText(sdk: Sdk): String? = if (sdk.isPoetry) sdk.versionString else null

  override fun getSdkIcon(sdk: Sdk): Icon? {
    return if (sdk.isPoetry) POETRY_ICON else null
  }

  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return PyPoetrySdkAdditionalData.load(element)
  }

  override fun tryCreatePackageManagementServiceForSdk(project: Project, sdk: Sdk): PyPackageManagementService? {
    return if (sdk.isPoetry) PyPoetryPackageManagementService(project, sdk) else null
  }
}