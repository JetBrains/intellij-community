// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.GeneratorPeerImpl
import com.jetbrains.python.newProject.PyNewProjectSettings
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep
import com.jetbrains.python.newProject.steps.PythonBaseProjectGenerator
import com.jetbrains.python.newProject.steps.PythonGenerateProjectCallback
import com.jetbrains.python.newProject.welcome.PyWelcomeSettings
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.statistics.modules
import training.lang.AbstractLangSupport
import training.learn.LearnBundle
import training.learn.exceptons.NoSdkException
import training.learn.lesson.kimpl.LessonUtil
import training.project.ProjectUtils
import java.nio.file.Path

class PythonLangSupport : AbstractLangSupport() {
  override val defaultProjectName = "PyCharmLearningProject"

  override val primaryLanguage = "Python"

  override val defaultProductName: String = "PyCharm"

  private val sourcesDirectoryName: String = "src"

  override fun installAndOpenLearningProject(projectPath: Path,
                                             projectToClose: Project?,
                                             postInitCallback: (learnProject: Project) -> Unit) {
    if (LessonUtil.productName != "PyCharm") {
      super.installAndOpenLearningProject(projectPath, projectToClose, postInitCallback)
    }
    else {
      val projectDirectory = projectPath.toAbsolutePath().toString()
      // use Python New Project Wizard logic to create project with default settings
      val callback = PythonGenerateProjectCallback<PyNewProjectSettings>()
      val step = ProjectSpecificSettingsStep(PythonBaseProjectGenerator(), callback)
      step.createPanel()
      step.setLocation(projectDirectory)

      val settings = PyWelcomeSettings.instance
      val oldValue = settings.createWelcomeScriptForEmptyProject
      settings.createWelcomeScriptForEmptyProject = false

      invokeLater {
        callback.consume(step, GeneratorPeerImpl())
        val project = ProjectManager.getInstance().openProjects.find { it.name == defaultProjectName }
                      ?: throw error("Cannot find open project with name: $defaultProjectName")
        ProjectUtils.copyLearningProjectFiles(projectPath, this)
        ProjectUtils.createVersionFile(projectPath)
        postInitCallback(project)
        settings.createWelcomeScriptForEmptyProject = oldValue
      }
      Disposer.dispose(step)
    }
  }

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

  override fun getSdkForProject(project: Project): Sdk? {
    if (LessonUtil.productName == "PyCharm") {
      return null // sdk already configured in this case
    }
    val sdkList = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<Sdk>, Exception>(
      { findAllPythonSdks(Path.of(project.basePath)) },
      LearnBundle.message("learn.project.initializing.python.sdk.finding.progress.title"),
      false,
      project
    )
    var preferredSdk = filterSystemWideSdks(sdkList)
      .filter { !PythonSdkUtil.isInvalid(it) && !PythonSdkUtil.isVirtualEnv(it) }
      .sortedWith(PreferredSdkComparator.INSTANCE)
      .firstOrNull()
    if (preferredSdk == null) {
      throw NoSdkException()
    }
    else if (preferredSdk is PyDetectedSdk) {
      preferredSdk = SdkConfigurationUtil.createAndAddSDK(preferredSdk.homePath!!, PythonSdkType.getInstance())
    }
    return preferredSdk
  }

  override fun applyProjectSdk(sdk: Sdk, project: Project) {
    val rootManager = ProjectRootManagerEx.getInstanceEx(project)
    rootManager.addProjectJdkListener {
      if (rootManager.projectSdk?.sdkType !is PythonSdkType) {
        setReadyToUseSdk(project, project.modules.first(), sdk)
      }
    }
  }

  override fun checkSdk(sdk: Sdk?, project: Project) {
  }

  override fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean {
    return file.name != projectSandboxRelativePath
  }

  override val projectSandboxRelativePath = "src/sandbox.py"
}
