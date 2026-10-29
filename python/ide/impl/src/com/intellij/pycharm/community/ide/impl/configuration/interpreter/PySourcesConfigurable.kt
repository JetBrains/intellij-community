// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.project.PyProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** Sources tab (PY-89840). Owns the active [PySourcesModel.Session] + Swing panel. */
internal class PySourcesConfigurable(pyProject: PyProject) : Configurable {
  private val model = PySourcesModel(pyProject)
  private val moduleScope = PyModuleEditorScopes.of(pyProject)
  private val topPanel: JPanel = JPanel(BorderLayout())

  /** Null = "not yet built" or "disposed". Real state. */
  @Volatile
  private var session: PySourcesModel.Session? = null

  /** Lazy — platform may call `createComponent` twice per open (PY-92513). */
  private val panel: JPanel by lazy {
    launchEditorAttach()
    topPanel
  }

  override fun getDisplayName(): String = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.tab.sources")
  override fun getHelpTopic(): String? = null

  override fun createComponent(): JComponent = panel

  override fun isModified(): Boolean = session?.isModified == true

  /** Sync via modal progress — avoids race with OK dispose chain. */
  @RequiresEdt
  override fun apply() {
    val active = session ?: return
    val committed = runWithModalProgressBlocking(model.project(), PyBundle.message("configurable.PyWorkspaceStructureConfigurable.tab.sources")) {
      active.applyAndCommit()
    }
    if (committed) {
      session = null
      detachEditor()
      moduleScope.launch { active.disposeAsync() }
      launchEditorAttach()
    }
  }

  override fun reset() {
    val previous = session ?: return
    session = null
    moduleScope.launch {
      withContext(Dispatchers.EDT) { detachEditor() }
      previous.disposeAsync()
      launchEditorAttach()
    }
  }

  override fun disposeUIResources() {
    val active = session ?: return
    session = null
    topPanel.removeAll()
    moduleScope.launch { active.disposeAsync() }
  }

  /** Teardown from any dispatcher. */
  suspend fun disposeAsync() {
    val active = session ?: return
    session = null
    withContext(Dispatchers.EDT) { topPanel.removeAll() }
    active.disposeAsync()
  }

  private fun launchEditorAttach() {
    moduleScope.launch {
      val fresh = model.createSession()
      withContext(Dispatchers.EDT) {
        if (topPanel.componentCount > 0 || session != null) {
          fresh.disposeAsync()
          return@withContext
        }
        val component = fresh.createComponent()
        if (component == null) {
          fresh.disposeAsync()
          return@withContext
        }
        session = fresh
        topPanel.add(component, BorderLayout.CENTER)
        topPanel.revalidate()
        topPanel.repaint()
      }
    }
  }

  private fun detachEditor() {
    topPanel.removeAll()
    topPanel.revalidate()
    topPanel.repaint()
  }
}
