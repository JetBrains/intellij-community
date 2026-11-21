// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.variablesview.usertyperenderers

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeBackendOnlyActionBase
import com.jetbrains.python.PyBundle
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.variablesview.usertyperenderers.codeinsight.PyTypeNameResolver
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import java.awt.event.MouseEvent


class ConfigureTypeRenderersAction : XDebuggerTreeBackendOnlyActionBase() {
  init {
    templatePresentation.text = PyBundle.message("action.PyDebugger.CustomizeDataView.text")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = DebuggerUIUtil.getSession(e)?.debugProcess is PyDebugProcess &&
                                         getSelectedValues(e.dataContext).size == 1
  }

  override fun perform(value: XValue, nodeName: @NlsSafe String, e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().defaultProject
    val debugValue = value as? PyDebugValue ?: return
    val typeRendererId = debugValue.typeRendererId
    if (typeRendererId == null) {
      showSettingsWithNewRenderer(project, debugValue)
      return
    }
    val rendererIndexToSelect = getRendererIndexWithId(typeRendererId)
    if (rendererIndexToSelect != null) {
      showSettingsWithSelectedRenderer(project, rendererIndexToSelect)
    }
    else {
      showSettingsWithNewRenderer(project, debugValue)
    }
  }

}

class ConfigureTypeRenderersHyperLink(
  private val myTypeRendererId: String?,
  private val myProject: Project?,
  private val debugValue: PyDebugValue? = null,
) : XDebuggerTreeNodeHyperlink(
  "  " + PyBundle.message("form.debugger.variables.view.user.type.renderers.configure.renderer")) {

  private val TEXT_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.Foreground.PRESSED)

  override fun onClick(event: MouseEvent) {
    val project = myProject ?: ProjectManager.getInstance().defaultProject
    if (myTypeRendererId != null) {
      getRendererIndexWithId(myTypeRendererId)?.let {
        showSettingsWithSelectedRenderer(project, it)
      }
    }
    else {
      if (debugValue != null) {
        showSettingsWithNewRenderer(project, debugValue)
      }
    }
  }

  override fun alwaysOnScreen() = true

  override fun getTextAttributes() = TEXT_ATTRIBUTES
}

private fun showSettingsWithNewRenderer(project: Project, debugValue: PyDebugValue) {
  ShowSettingsUtil.getInstance().showSettingsDialog(project, PyUserTypeRenderersConfigurable::class.java) {
    val newRenderer = PyUserNodeRenderer(true, it.getCurrentlyVisibleNames())
    val qualifiedType = debugValue.qualifiedType
    newRenderer.name = debugValue.type + " " + PyBundle.message("form.debugger.variables.view.user.type.renderers.renderer")

    if (qualifiedType == null) {
      it.setNewRendererToAdd(newRenderer)
      return@showSettingsDialog
    }

    val pyClass = PyTypeNameResolver(project).resolve(qualifiedType)
    if (pyClass != null) {
      newRenderer.toType = qualifiedType
    }
    else {
      val classes = PyClassNameIndex.find(debugValue.type, project, GlobalSearchScope.projectScope(project))
      var resolveFound = false
      if (classes.isNotEmpty()) {
        val canonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(classes.first(), null)
        canonicalImportPath?.let { path ->
          newRenderer.toType = path.toString() + "." + debugValue.type
          resolveFound = true
        }
      }
      if (!resolveFound) {
        // Show type even if we failed to resolve it
        newRenderer.toType = debugValue.qualifiedType ?: ""
        newRenderer.typeCanonicalImportPath = debugValue.qualifiedType ?: ""
        newRenderer.typeQualifiedName = debugValue.qualifiedType ?: ""
      }
    }

    it.setNewRendererToAdd(newRenderer)
  }
}

private fun showSettingsWithSelectedRenderer(project: Project, rendererIndexToSelect: Int) {
  ShowSettingsUtil.getInstance().showSettingsDialog(project, PyUserTypeRenderersConfigurable::class.java) {
    it.setRendererIndexToSelect(rendererIndexToSelect)
  }
}