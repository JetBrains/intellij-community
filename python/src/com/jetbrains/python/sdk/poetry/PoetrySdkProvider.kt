package com.jetbrains.python.sdk.poetry

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import com.jetbrains.python.sdk.poetry.quickFixes.PoetryInstallQuickFix
import com.jetbrains.python.sdk.poetry.quickFixes.PoetryAssociationQuickFix
import com.jetbrains.python.sdk.poetry.ui.PyAddNewPoetryPanel
import org.jdom.Element
import javax.swing.Icon

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PoetrySdkProvider : PySdkProvider {
  override fun createEnvironmentAssociationFix(
    module: Module,
    sdk: Sdk,
    isPyCharm: Boolean,
    associatedModulePath: String?,
  ): PyInterpreterInspectionQuickFixData? {
    if (sdk.isPoetry) {
      val projectUnit = if (isPyCharm) "project" else "module"
      val message = when {
        associatedModulePath != null ->
          PyBundle.message("python.sdk.inspection.message.poetry.interpreter.associated.with.another.project", projectUnit, associatedModulePath)
        else -> PyBundle.message("python.sdk.inspection.message.poetry.interpreter.not.associated.with.any.project", projectUnit)
      }
      return PyInterpreterInspectionQuickFixData(PoetryAssociationQuickFix(), message)
    }
    return null
  }

  override fun createInstallPackagesQuickFix(module: Module): LocalQuickFix? {
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return null
    return if (sdk.isPoetry) PoetryInstallQuickFix() else null
  }

  override fun createNewEnvironmentPanel(
    project: Project?,
    module: Module?,
    existingSdks: List<Sdk>,
    newProjectPath: String?,
    context: UserDataHolder,
  ): PyAddNewEnvPanel {
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

internal fun validateSdks(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder): List<Sdk> {
  val moduleFile = module?.baseDir
  val sdks = findBaseSdks(existingSdks, module, context).takeIf { it.isNotEmpty() }
             ?: detectSystemWideSdks(module, existingSdks, context)

  return if (moduleFile != null) {
    PoetryPyProjectTomlPythonVersionsService.instance.validateSdkVersions(moduleFile, sdks)
  }
  else {
    sdks
  }.filter { it.sdkSeemsValid && !it.isPoetry }
}