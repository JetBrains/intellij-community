// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.variablesview.usertyperenderers

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.python.PyBundle
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.variablesview.usertyperenderers.codeinsight.PyTypeNameResolver
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import java.awt.event.MouseEvent


fun getRendererIndexWithId(typeRendererId: String): Int? {
  val settings = PyUserTypeRenderersSettings.getInstance()
  val renderers = settings.renderers
  return renderers.indices.firstOrNull { index ->
    renderers[index].isApplicable() && renderers[index].toType == typeRendererId
  }
}

class ConfigureTypeRenderersAction : XDebuggerTreeActionBase() {
  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().defaultProject
    val debugValue = node.valueContainer as? PyDebugValue ?: return
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

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false
    val tree = XDebuggerTree.getTree(e.dataContext)
    tree?.selectionPaths?.let {
      if (it.size > 1) return
      e.presentation.text = PyBundle.message("action.PyDebugger.CustomizeDataView.text")
      e.presentation.isVisible = true
    }
  }

  companion object {
    fun showSettingsWithNewRenderer(project: Project, debugValue: PyDebugValue) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, PyUserTypeRenderersConfigurable::class.java) {
        val newRenderer = PyUserNodeRenderer(true)
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
          if (classes.size == 1) {
            val canonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(classes.first(), null)
            canonicalImportPath?.let { path -> newRenderer.toType = path.toString() + "." + debugValue.type }
          }
        }

        it.setNewRendererToAdd(newRenderer)
      }
    }

    fun showSettingsWithSelectedRenderer(project: Project, rendererIndexToSelect: Int) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, PyUserTypeRenderersConfigurable::class.java) {
        it.setRendererIndexToSelect(rendererIndexToSelect)
      }
    }
  }
}

class ConfigureTypeRenderersHyperLink(
  private val myTypeRendererId: String,
  private val myProject: Project?
) : XDebuggerTreeNodeHyperlink("  " + PyBundle.message("form.debugger.variables.view.user.type.renderers.configure.renderer")) {

  private val TEXT_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.Foreground.PRESSED)

  override fun onClick(event: MouseEvent) {
    val project = myProject ?: ProjectManager.getInstance().defaultProject
    getRendererIndexWithId(myTypeRendererId)?.let {
      ConfigureTypeRenderersAction.showSettingsWithSelectedRenderer(project, it)
    }
  }

  override fun alwaysOnScreen() = true

  override fun getTextAttributes() = TEXT_ATTRIBUTES
}