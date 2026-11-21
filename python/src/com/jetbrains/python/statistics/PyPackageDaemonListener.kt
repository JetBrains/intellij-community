// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiManager
import com.intellij.python.pyproject.model.evolution.findEvoPyProject
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.ast.PyAstFile
import com.jetbrains.python.ast.PyAstImportStatementBase
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
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

@Service(Service.Level.PROJECT)
@VisibleForTesting
internal class PackageDaemonTaskExecutor(private val project: Project, private val cs: CoroutineScope) {
  fun execute(vFile: VirtualFile): Job {
    return cs.launch {
      // Wait for the Python project structure, so a file opened before it is ready is still counted.
      val evoPyProject = project.findEvoPyProject(vFile, mainForOrphans = false) ?: return@launch
      constrainedReadAction(ReadConstraint.inSmartMode(project)) readAction@{
        val fileIndex = ProjectFileIndex.getInstance(project)
        if (!fileIndex.isInProject(vFile) || fileIndex.isInLibrary(vFile)) {
          return@readAction emptyList()
        }

        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@readAction emptyList()
        val viewProvider = psiFile.viewProvider
        val pyPsiFile = if (viewProvider is MultiplePsiFilesPerDocumentFileViewProvider) {
          viewProvider.allFiles.firstOrNull { it is PyAstFile }
        } else psiFile
        if (pyPsiFile !is PyAstFile) return@readAction emptyList()
        val interpreter = evoPyProject.interpreter
        val interpreterType = interpreter?.interpreterType ?: InterpreterType.REGULAR
        val interpreterTarget = interpreter?.executionType ?: InterpreterTarget.LOCAL
        val packages2Versions = if (interpreter == null) emptyMap()
        else {
          PythonPackageManager.forPythonInterpreter(interpreter).listInstalledPackagesSnapshot().associate { it.name to it.version }
        }

        pyPsiFile.children.filterIsInstance<PyAstImportStatementBase>().mapNotNull { import ->
          // all imports from the same statement should start with the same module
          import.fullyQualifiedObjectNames.firstOrNull()?.let { firstModule ->
            val packageName = PyPsiPackageUtil.moduleToPackageName(firstModule.split('.').first())
            PackageUsage(
              name = packageName,
              version = packages2Versions[packageName] ?: "0.0",
              interpreterTypeValue = interpreterType.value,
              targetTypeValue = interpreterTarget.value,
              hasSdk = interpreter != null,
              fileTypeName = psiFile.fileType.name
            )
          }
        }
      }.let { usages ->
        PyPackageUsageStatistics.getInstance(project).increaseUsages(usages)
      }
    }
  }
}

internal class PyPackageDaemonListener(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
  init {
    if (!isEnabled) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    for (fileEditor in fileEditors) {
      val vFile = fileEditor.file
      if (vFile.isUpToDate) continue
      project.service<PackageDaemonTaskExecutor>().execute(vFile)
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