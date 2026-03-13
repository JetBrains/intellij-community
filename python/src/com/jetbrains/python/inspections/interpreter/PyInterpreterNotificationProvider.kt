// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.interpreter

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.util.ui.AsyncProcessIcon
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.inspections.InterpreterFixExecutor
import com.jetbrains.python.inspections.PyAsyncFileInspectionRunner
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function
import javax.swing.JComponent

internal val RELEVANT_NON_PYTHON_FILES: Map<String, (Module) -> Boolean> = mapOf(
  PY_PROJECT_TOML to { _ -> true },
  "README.md" to ::moduleContainsPythonFiles,
)

@ApiStatus.Internal
class PyInterpreterNotificationProvider : EditorNotificationProvider, DumbAware {
  private val asyncFileInspectionRunner = PyAsyncFileInspectionRunner(
    progressTitle = PyBundle.message("python.sdk.checking.existing.environments"),
    cacheLoader = createInterpreterCacheLoader(),
  )

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
    val nonPythonRelevantCheck = RELEVANT_NON_PYTHON_FILES[file.name]
    if (psiFile is PyFile && isFileIgnored(psiFile)) return null
    if (psiFile !is PyFile && nonPythonRelevantCheck == null) return null

    val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
    if (!PyModuleService.getInstance().isPythonModule(module)) return null

    PythonSdkUtil.findPythonSdk(module)?.let { return null }
    if (nonPythonRelevantCheck != null && !nonPythonRelevantCheck(module)) return null

    val interpreterFixes = asyncFileInspectionRunner.runInspection(module)?.takeIf { it.isNotEmpty() } ?: return null

    val executor: BusyGuardExecutor = project.service<InterpreterFixExecutor>()

    return Function { fileEditor ->
      object : EditorNotificationPanel(fileEditor, Status.Warning) {
        init {
          text = PyBundle.message("python.sdk.no.interpreter.configured.for.module", module.name)
          if (executor.isBusy.value) {
            val label = javax.swing.JLabel(PyBundle.message("python.sdk.interpreter.fix.already.in.progress"))
            label.foreground = com.intellij.util.ui.UIUtil.getInactiveTextColor()
            myLinksPanel.add(label)
            myLinksPanel.add(AsyncProcessIcon("interpreter fix"))
          }
          else {
            interpreterFixes.forEach { fix ->
              myLinksPanel.add(fix.createActionLink(module, project, psiFile, executor))
            }
          }
        }
      }
    }
  }
}

private fun moduleContainsPythonFiles(module: Module): Boolean = when {
  DumbService.isDumb(module.project) -> false
  else -> FileTypeIndex.containsFileOfType(PythonFileType.INSTANCE, GlobalSearchScope.moduleScope(module))
}

private fun isFileIgnored(pyFile: PyFile): Boolean =
  PyInspectionExtension.EP_NAME.extensionList.any { it.ignoreInterpreterWarnings(pyFile) }
