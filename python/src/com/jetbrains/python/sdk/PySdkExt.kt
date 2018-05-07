/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk

import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * @author vlan
 */

fun findBaseSdks(existingSdks: List<Sdk>): List<Sdk> {
  val existing = existingSdks.filter { it.isSystemWide }
  val detected = detectSystemWideSdks(existingSdks)
  return existing + detected
}

fun detectSystemWideSdks(existingSdks: List<Sdk>): List<PyDetectedSdk> {
  val existingPaths = existingSdks.map { it.homePath }.toSet()
  return PythonSdkFlavor.getApplicableFlavors(false)
    .asSequence()
    .flatMap { it.suggestHomePaths().asSequence() }
    .filter { it !in existingPaths }
    .map { PyDetectedSdk(it) }
    .sortedWith(compareBy<PyDetectedSdk>({ it.guessedLanguageLevel },
                                         { it.homePath }).reversed())
    .toList()
}

fun detectVirtualEnvs(module: Module?, existingSdks: List<Sdk>): List<PyDetectedSdk> =
  filterSuggestedPaths(VirtualEnvSdkFlavor.INSTANCE.suggestHomePaths(), existingSdks, module)

fun detectCondaEnvs(module: Module?, existingSdks: List<Sdk>): List<PyDetectedSdk> =
  filterSuggestedPaths(CondaEnvSdkFlavor.INSTANCE.suggestHomePaths(), existingSdks, module)

fun createSdkByGenerateTask(generateSdkHomePath: Task.WithResult<String, ExecutionException>,
                            existingSdks: List<Sdk>,
                            baseSdk: Sdk?,
                            associatedProjectPath: String?,
                            suggestedSdkName: String?): Sdk? {
  val homeFile = try {
    val homePath = ProgressManager.getInstance().run(generateSdkHomePath)
    StandardFileSystems.local().refreshAndFindFileByPath(homePath) ?:
    throw ExecutionException("Directory $homePath not found")
  }
  catch (e: ExecutionException) {
    val description = PyPackageManagementService.toErrorDescription(listOf(e), baseSdk) ?: return null
    PackagesNotificationPanel.showError("Failed to Create Interpreter", description)
    return null
  }
  val suggestedName = suggestedSdkName ?: suggestAssociatedSdkName(homeFile.path, associatedProjectPath)
  return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeFile,
                                       PythonSdkType.getInstance(),
                                       false, null, suggestedName) ?: return null
}

fun Sdk.associateWithModule(module: Module?, isNewProject: Boolean) {
  getOrCreateAdditionalData().apply {
    when {
      isNewProject -> associateWithNewProject()
      module != null -> associateWithModule(module)
    }
  }
}

fun Sdk.isAssociatedWithModule(module: Module?): Boolean {
  val basePath = module?.basePath
  val associatedPath = associatedModulePath
  if (basePath != null && associatedPath == basePath) return true
  if (isAssociatedWithAnotherModule(module)) return false
  return isLocatedInsideModule(module) || containsModuleName(module)
}

fun Sdk.isAssociatedWithAnotherModule(module: Module?): Boolean {
  val basePath = module?.basePath ?: return false
  val associatedPath = associatedModulePath ?: return false
  return basePath != associatedPath
}

val Sdk.associatedModulePath: String?
  // TODO: Support .project associations
  get() = associatedPathFromAdditionalData /*?: associatedPathFromDotProject*/

fun Sdk.adminPermissionsNeeded(): Boolean {
  val homePath = homePath ?: return false
  return !Files.isWritable(Paths.get(homePath))
}

fun PyDetectedSdk.setup(existingSdks: List<Sdk>): Sdk? {
  val homeDir = homeDirectory ?: return null
  return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeDir, PythonSdkType.getInstance(), false, null, null)
}

fun PyDetectedSdk.setupAssociated(existingSdks: List<Sdk>, associatedModulePath: String?): Sdk? {
  val homeDir = homeDirectory ?: return null
  val suggestedName = homePath?.let { suggestAssociatedSdkName(it, associatedModulePath) }
  return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeDir, PythonSdkType.getInstance(), false, null, suggestedName)
}

var Module.pythonSdk: Sdk?
  get() = PythonSdkType.findPythonSdk(this)
  set(value) = ModuleRootModificationUtil.setModuleSdk(this, value)

var Project.pythonSdk: Sdk?
  get() {
    val sdk = ProjectRootManager.getInstance(this).projectSdk
    return when (sdk?.sdkType) {
      is PythonSdkType -> sdk
      else -> null
    }
  }
  set(value) {
    ApplicationManager.getApplication().runWriteAction {
      ProjectRootManager.getInstance(this).projectSdk = value
    }
  }

val Module.baseDir: VirtualFile?
  get() = rootManager.contentRoots.firstOrNull()

val Module.basePath: String?
  get() = baseDir?.path


private fun suggestAssociatedSdkName(sdkHome: String, associatedPath: String?): String? {
  val baseSdkName = PythonSdkType.suggestBaseSdkName(sdkHome) ?: return null
  val venvRoot = PythonSdkType.getVirtualEnvRoot(sdkHome)?.path
  val condaRoot = CondaEnvSdkFlavor.getCondaEnvRoot(sdkHome)?.path
  val associatedName = when {
    venvRoot != null && (associatedPath == null || !FileUtil.isAncestor(associatedPath, venvRoot, true)) ->
      PathUtil.getFileName(venvRoot)
    condaRoot != null && (associatedPath == null || !FileUtil.isAncestor(associatedPath, condaRoot, true)) ->
      PathUtil.getFileName(condaRoot)
    else ->
      associatedPath?.let { PathUtil.getFileName(associatedPath) } ?: return null
  }
  return "$baseSdkName ($associatedName)"
}

val File.isNotEmptyDirectory: Boolean
  get() = exists() && isDirectory && list()?.isEmpty()?.not() ?: false

val Sdk.isSystemWide: Boolean
  get() = !PythonSdkType.isRemote(this) && !PythonSdkType.isVirtualEnv(
    this) && !PythonSdkType.isCondaVirtualEnv(this)

@Suppress("unused")
private val Sdk.associatedPathFromDotProject: String?
  get() {
    val binaryPath = homePath ?: return null
    val virtualEnvRoot = PythonSdkType.getVirtualEnvRoot(binaryPath) ?: return null
    val projectFile = File(virtualEnvRoot, ".project")
    return try {
      projectFile.readText().trim()
    }
    catch (e: IOException) {
      null
    }
  }

private val Sdk.associatedPathFromAdditionalData: String?
  get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath

private fun Sdk.isLocatedInsideModule(module: Module?): Boolean {
  val homePath = homePath ?: return false
  val basePath = module?.basePath ?: return false
  return FileUtil.isAncestor(basePath, homePath, true)
}

private val PyDetectedSdk.guessedLanguageLevel: LanguageLevel?
  get() {
    val path = homePath ?: return null
    val result = Regex(""".*python(\d\.\d)""").find(path) ?: return null
    val versionString = result.groupValues.getOrNull(1) ?: return null
    return LanguageLevel.fromPythonVersion(versionString)
  }

private fun Sdk.containsModuleName(module: Module?): Boolean {
  val path = homePath ?: return false
  val name = module?.name ?: return false
  return path.contains(name, true)
}

fun Sdk.getOrCreateAdditionalData(): PythonSdkAdditionalData {
  val existingData = sdkAdditionalData as? PythonSdkAdditionalData
  if (existingData != null) return existingData
  val newData = PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(homePath))
  val modificator = sdkModificator
  modificator.sdkAdditionalData = newData
  ApplicationManager.getApplication().runWriteAction { modificator.commitChanges() }
  return newData
}

private fun filterSuggestedPaths(suggestedPaths: MutableCollection<String>,
                                 existingSdks: List<Sdk>,
                                 module: Module?): List<PyDetectedSdk> {
  val existingPaths = existingSdks.map { it.homePath }.toSet()
  return suggestedPaths
    .asSequence()
    .filterNot { it in existingPaths }
    .distinct()
    .map { PyDetectedSdk(it) }
    .sortedWith(compareBy<PyDetectedSdk>({ it.isAssociatedWithModule(module) },
                                         { it.homePath }).reversed())
    .toList()
}
