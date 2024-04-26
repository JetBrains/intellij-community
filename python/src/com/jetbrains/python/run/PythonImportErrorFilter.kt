// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.codeInsight.hints.presentation.*
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.impl.InlayProvider
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.actions.RenameElementAction
import com.jetbrains.python.extensions.toPsi
import com.jetbrains.python.PyBundle
import java.awt.Point
import java.awt.event.MouseEvent

class PythonImportErrorFilter(val project: Project) : Filter {
  private var shouldHandle = false

  companion object {
    private const val TRACEBACK_STRING = "Traceback (most recent call last):\n"
    private const val ERROR_IN_SITECUSTOMIZE_STRING = "Error in sitecustomize; set PYTHONVERBOSE for traceback:\n"
    private const val FATAL_ERROR_STRING = "Fatal Python error:"
    private const val TRIM_PATTERN = "("

    private val ERRORS_STRINGS = setOf("ImportError:", "AttributeError:", "ModuleNotFoundError:", "NameError:")
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    return if (shouldHandle(line)) computeLine(line, entireLength) else null
  }

  private fun shouldHandle(input: String): Boolean {
    if (!shouldHandle) {
        shouldHandle = TRACEBACK_STRING == input || ERROR_IN_SITECUSTOMIZE_STRING == input || input.indexOf(FATAL_ERROR_STRING) != -1
    }
    return shouldHandle
  }

  private fun computeLine(line: String, entireLength: Int): Filter.Result? {
    for (error in ERRORS_STRINGS) {
      if (line.indexOf(error) != -1) {
        getModuleName(line)?.let {
          return createFilterResult(it, entireLength)
        }
      }
    }
    return null
  }

  private fun getModuleName(line: String): String? {
    val index = line.indexOf(TRIM_PATTERN)
    val key = (if (index != -1) line.substring(0, index) else line).trim().removeSuffix("\n")
    if (IMPORT_ERRORS_FILTER_MAP.containsKey(key)) {
      return IMPORT_ERRORS_FILTER_MAP[key]
    }
    return null
  }

  private fun createFilterResult(name: String, entireLength: Int): Filter.Result? {
    return getFileByName(name)?.let { createInlayProvider(it, entireLength) }
  }

  private fun createInlayProvider(virtualFile: VirtualFile, entireLength: Int): Filter.Result {
    //ResultItem(0, 0, null) is necessary that CompositeFilter doesn't rewrite result into a regular Filter.Result instance
    return Filter.Result(listOf(CreateImportExceptionResult(0, entireLength - 1, virtualFile),
                                ResultItem(0, 0, null)))
  }

  private fun getFileByName(name: String): VirtualFile? {
    val virtualFiles = FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
    return virtualFiles.firstOrNull()
  }

  private fun createRenameAction(virtualFile: VirtualFile): AnAction {
    return object : DumbAwareAction(PyBundle.message("run.debug.shadowing.name.import.error.rename", virtualFile.name)) {
      init {
        shortcutSet = ActionUtil.getShortcutSet(IdeActions.ACTION_RENAME)
      }

      override fun actionPerformed(e: AnActionEvent) {
        val action = RenameElementAction()
        val targetPsiFile = virtualFile.toPsi(project)?.containingFile
        val simpleContext = SimpleDataContext.builder()
          .add(CommonDataKeys.PSI_FILE, targetPsiFile)
          .add(CommonDataKeys.PSI_ELEMENT, targetPsiFile)
          .add(CommonDataKeys.PROJECT, project)
          .build()
        ActionUtil.invokeAction(action, simpleContext, ActionPlaces.UNKNOWN, null, null)
      }
    }
  }

  private fun createOpenFileAction(virtualFile: VirtualFile): AnAction {
    return object : DumbAwareAction(PyBundle.message("run.debug.shadowing.name.import.error.open.file", virtualFile.name)) {
      override fun actionPerformed(e: AnActionEvent) {
        OpenFileAction.openFile(virtualFile, project)
      }
    }
  }

  private inner class CreateImportExceptionResult(
    highlightStartOffset: Int,
    highlightEndOffset: Int,
    private val virtualFile: VirtualFile,
  ) : Filter.Result(highlightStartOffset, highlightEndOffset, null), InlayProvider {
    override fun createInlayRenderer(editor: Editor): EditorCustomElementRenderer {
      val factory = PresentationFactory(editor)
      val inlayText = PyBundle.message("run.debug.shadowing.name.import.error.title", virtualFile.name)
      val presentation = factory.referenceOnHover(factory.roundWithBackground(factory.withReferenceAttributes(factory.text(inlayText)))
      ) { event: MouseEvent?, _: Point? ->
        event ?: return@referenceOnHover
        val actions = listOf(createOpenFileAction(virtualFile), createRenameAction(virtualFile))
        JBPopupMenu.showByEvent(event, "InlayMenu", DefaultActionGroup(actions))
      }

      return PresentationRenderer(presentation)
    }
  }
}