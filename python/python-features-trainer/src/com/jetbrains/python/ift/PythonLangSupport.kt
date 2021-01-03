// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.statistics.modules
import training.lang.AbstractLangSupport
import training.learn.LearnBundle
import training.learn.exceptons.NoSdkException
import java.nio.file.Path

class PythonLangSupport : AbstractLangSupport() {
  override val defaultProjectName = "PyCharmLearningProject"

  override val primaryLanguage = "Python"

  override val defaultProductName: String = "PyCharm"

  private val sourcesDirectoryName: String = "src"

  override val filename: String = "Learning.py"

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

  private fun isNoOlderThan27(sdk: Sdk): Boolean {
    val languageLevel = if (sdk is PyDetectedSdk) {
      sdk.guessedLanguageLevel
    }
    else {
      PythonSdkFlavor.getFlavor(sdk)?.getLanguageLevel(sdk)
    }
    return languageLevel?.isAtLeast(LanguageLevel.PYTHON27) ?: false
  }

  override fun applyProjectSdk(sdk: Sdk, project: Project) {
    setReadyToUseSdk(project, project.modules.first(), sdk)
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
