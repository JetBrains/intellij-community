// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor
import com.intellij.openapi.roots.ui.configuration.ContentRootPanel
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.module.PyContentEntriesEditor
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JavaSourceRootType
import javax.swing.JComponent

/**
 * Factory for a Sources-tab editing session. Session fields are non-null `val` — no has-editor
 * null-checks leak out. Module access confined to [createSession]; lifecycle driven by the
 * per-module scope from [PyModuleEditorScopes], so no `module.isDisposed` fences in callers.
 */
internal class PySourcesModel(private val pyProject: PyProject) {

  fun project(): Project = pyProject.project

  /**
   * Build a session. Cancellation propagates through the suspend chain: caller launches on the
   * per-module scope, so a cancelled scope aborts `readAction` / `withContext` with
   * `CancellationException` before any editor is built.
   */
  suspend fun createSession(): Session {
    val module = pyProject.residesOnModule
    val project = pyProject.project
    val model = readAction { ModuleRootManager.getInstance(module).modifiableModel }
    return withContext(Dispatchers.EDT) {
      val state = object : ModuleConfigurationStateImpl(project, DefaultModulesProvider(project)) {
        override fun getModifiableRootModel(): ModifiableRootModel = model
        override fun getCurrentRootModel(): ModifiableRootModel = model
      }
      val editor = object : PyContentEntriesEditor(module, state, true, JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE) {
        override fun createContentEntryEditor(contentEntryUrl: String): ContentEntryEditor {
          val customEditor = object : MyContentEntryEditor(contentEntryUrl, editHandlers) {
            public override fun getContentEntry(): ContentEntry? = super.getContentEntry()
            override fun createContentRootPane(): ContentRootPanel {
              val ownEditor = this
              return object : ContentRootPanel(ownEditor, editHandlers) {
                override fun getContentEntry(): ContentEntry? = ownEditor.getContentEntry()
                override fun addFolderGroupComponents() {}
                override fun setSelected(selected: Boolean) {}
              }
            }
          }
          contentEntryEditor = customEditor
          return customEditor
        }
      }
      Session(editor, model)
    }
  }

  /** One editor lifecycle. All fields non-null `val`. */
  class Session internal constructor(
    private val editor: PyContentEntriesEditor,
    private val model: ModifiableRootModel,
  ) {

    val isModified: Boolean get() = editor.isModified

    @RequiresEdt
    fun createComponent(): JComponent? = editor.createComponent()

    /**
     * Caller wraps in `runWithModalProgressBlocking` → user Swing events suppressed, so the
     * EDT-hops (`editor.apply()` then `writeAction { model.commit() }`) are effectively atomic.
     */
    suspend fun applyAndCommit(): Boolean = withContext(Dispatchers.EDT) {
      val wasModified = editor.isModified
      editor.apply()
      if (!wasModified) return@withContext false
      writeAction { model.commit() }
      true
    }

    /** Explicit teardown. Hops to EDT for the Swing-only `disposeUIResources`. */
    suspend fun disposeAsync() {
      withContext(Dispatchers.EDT) { editor.disposeUIResources() }
      model.dispose()
    }
  }
}
