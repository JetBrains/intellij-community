// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.sdk.PythonSdkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.days


private val VIRTUAL_FILE_TIMESTAMP_KEY = Key.create<Long>("VIRTUAL_FILE_TIMESTAMP")

private val VirtualFile.isUpToDate: Boolean
  get() {
    val timestamp = getUserData(VIRTUAL_FILE_TIMESTAMP_KEY) ?: 0
    if (timestamp + 1.days.inWholeMilliseconds < System.currentTimeMillis() ) {
      putUserData(VIRTUAL_FILE_TIMESTAMP_KEY, System.currentTimeMillis())
      return false
    }
    return true
  }

@Service
internal class TaskExecutor(private val cs: CoroutineScope) {

  fun shutdownService() {
    cs.cancel() // this does not affect other services in the same plugin/container.
  }

  fun execute(vFile: VirtualFile, project: Project) {
    cs.launch {
      constrainedReadAction(ReadConstraint.inSmartMode(project)) readAction@{
        val fileIndex = ProjectFileIndex.getInstance(project)
        if (!fileIndex.isInProject(vFile) || fileIndex.isInLibrary(vFile)) {
          return@readAction emptyList()
        }

        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@readAction emptyList()
        if (psiFile !is PyFile) return@readAction emptyList()
        val module = ModuleUtil.findModuleForFile(psiFile) ?: return@readAction emptyList()
        val sdk = PythonSdkUtil.findPythonSdk(module)
        val interpreterType = sdk?.interpreterType ?: InterpreterType.REGULAR
        val interpreterTarget = sdk?.executionType ?: InterpreterTarget.LOCAL
        val packages2Versions = sdk?.let {
          val packagesFromPackageManager = PythonPackageManager.forSdk(project, sdk).installedPackages
          packagesFromPackageManager.associate { it.name to it.version }
        } ?: emptyMap()

        psiFile.children.filterIsInstance<PyImportStatementBase>().mapNotNull { import ->
          // all imports from the same statement should start with the same module
          import.fullyQualifiedObjectNames.firstOrNull()?.let { firstModule ->
            val packageName = PyPsiPackageUtil.moduleToPackageName(firstModule.split('.').first())
            PackageUsage(
              name = packageName,
              version = packages2Versions[packageName] ?: "0.0",
              interpreterTypeValue = interpreterType.value,
              targetTypeValue = interpreterTarget.value,
              hasSdk = sdk != null
            )
          }
        }
      }.let { usages ->
        PyPackageUsageStatistics.getInstance(project).increaseUsages(usages)
      }
    }
  }
}

class PyPackageDaemonListener(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
  init {
    if (!isEnabled) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    for (fileEditor in fileEditors) {
      val vFile = fileEditor.file
      if (vFile.isUpToDate) continue
      service<TaskExecutor>().execute(vFile, project)
    }
  }

  companion object {
    @set:TestOnly
    var isEnforceEnabledInTests: Boolean = false

    val isEnabled: Boolean
      get() {
        return isEnforceEnabledInTests || ApplicationManager.getApplication().run {
          !isUnitTestMode && !isHeadlessEnvironment && StatisticsUploadAssistant.isCollectAllowedOrForced()
        }
      }
  }
}