// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.configuration.PyProjectVirtualEnvConfiguration
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.statistics.modules
import training.dsl.LessonUtil
import training.lang.AbstractLangSupport
import training.learn.LearnBundle
import training.learn.exceptons.NoSdkException
import training.project.ProjectUtils
import training.project.ReadMeCreator
import training.util.getFeedbackLink
import java.nio.file.Path

class PythonLangSupport : AbstractLangSupport() {
  override val defaultProjectName = "PyCharmLearningProject"

  override val primaryLanguage = "Python"

  override val defaultProductName: String = "PyCharm"

  private val sourcesDirectoryName: String = "src"

  override val filename: String = "Learning.py"

  override val langCourseFeedback get() = getFeedbackLink(this, false)

  override val readMeCreator = ReadMeCreator()

  override fun applyToProjectAfterConfigure(): (Project) -> Unit = { project ->
    // mark src directory as sources root
    val module = project.modules.first()
    val sourcesPath = project.basePath!! + '/' + sourcesDirectoryName
    val sourcesRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(sourcesPath)
                      ?: error("Failed to find directory with source files: $sourcesPath")
    val rootsModel = ModuleRootManager.getInstance(module).modifiableModel
    val contentEntry = rootsModel.contentEntries.find {
      val contentEntryFile = it.file
      contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, sourcesRoot, false)
    } ?: error("Failed to find content entry for file: ${sourcesRoot.name}")

    contentEntry.addSourceFolder(sourcesRoot, false)
    runWriteAction {
      rootsModel.commit()
      project.save()
    }
  }

  override fun installAndOpenLearningProject(projectPath: Path,
                                             projectToClose: Project?,
                                             postInitCallback: (learnProject: Project) -> Unit) {
    // if we open project with isProjectCreatedFromWizard flag as true, PythonSdkConfigurator will not run and configure our sdks
    // and we will configure it individually without any race conditions
    val openProjectTask = OpenProjectTask(projectToClose = projectToClose, isProjectCreatedWithWizard = true)
    ProjectUtils.simpleInstallAndOpenLearningProject(projectPath, this, openProjectTask, postInitCallback)
  }

  override fun getSdkForProject(project: Project): Sdk? {
    if (project.pythonSdk != null) return null  // sdk already configured
    return if (useVenv) {
      createVenv(project)
    }
    else {
      findSystemWideSdk(project)
    }
  }

  private fun findSystemWideSdk(project: Project): Sdk? {
    val sdkList = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<Sdk>, Exception>(
      { findAllPythonSdks(Path.of(project.basePath)) },
      LearnBundle.message("learn.project.initializing.python.sdk.finding.progress.title"),
      false,
      project
    )
    var preferredSdk = filterSystemWideSdks(sdkList)
      .filter {
        !(!isNoOlderThan27(it) || PythonSdkUtil.isInvalid(it) || PythonSdkUtil.isVirtualEnv(it)
          || PythonSdkUtil.isCondaVirtualEnv(it) || PythonSdkUtil.isRemote(it))
      }
      .sortedWith(compareBy { sdk: Sdk -> PythonSdkUtil.isConda(sdk) }.thenComparing(PreferredSdkComparator.INSTANCE))
      .firstOrNull()
    if (preferredSdk == null) {
      throw NoSdkException()
    }
    else if (preferredSdk is PyDetectedSdk) {
      preferredSdk = SdkConfigurationUtil.createAndAddSDK(preferredSdk.homePath!!, PythonSdkType.getInstance())
    }
    return preferredSdk
  }

  private fun createVenv(project: Project): Sdk? {
    val module = project.modules.first()
    val existingSdks = getExistingSdks()
    val baseSdks = findBaseSdks(existingSdks, module, project)
    val preferredSdk = PyProjectVirtualEnvConfiguration.findPreferredVirtualEnvBaseSdk(baseSdks)
    val venvRoot = FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(project.basePath))
    val venvSdk = PyProjectVirtualEnvConfiguration.createVirtualEnvSynchronously(preferredSdk, existingSdks, venvRoot,
                                                                                 project.basePath, project, module, project)
    return venvSdk?.also {
      SdkConfigurationUtil.addSdk(it)
    }
  }

  override fun applyProjectSdk(sdk: Sdk, project: Project) {
    setReadyToUseSdk(project, project.modules.first(), sdk)
    if (!useVenv && LessonUtil.productName != defaultProductName) {
      // This is a workaround for IDEA SetupJavaProjectFromSourcesActivity that replaces our SDK with Java SDK
      val rootManager = ProjectRootManagerEx.getInstanceEx(project)
      rootManager.addProjectJdkListener {
        if (rootManager.projectSdk?.sdkType !is PythonSdkType) {
          setReadyToUseSdk(project, project.modules.first(), sdk)
        }
      }
    }
  }

  private fun getExistingSdks(): List<Sdk> {
    return PyConfigurableInterpreterList.getInstance(null).allPythonSdks
      .sortedWith(PreferredSdkComparator.INSTANCE)
  }

  private fun isNoOlderThan27(sdk: Sdk): Boolean {
    val languageLevel = if (sdk is PyDetectedSdk) {
      sdk.guessedLanguageLevel
    }
    else {
      PythonSdkFlavor.getFlavor(sdk)?.getLanguageLevel(sdk)
    }
    return languageLevel?.isAtLeast(LanguageLevel.PYTHON27) ?: false
  }

  private val useVenv: Boolean
    get() = Registry.`is`("ide.features.trainer.use.venv")

  override fun checkSdk(sdk: Sdk?, project: Project) {
  }

  override fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean {
    return file.name != projectSandboxRelativePath
  }

  override val projectSandboxRelativePath = "src/sandbox.py"
}
