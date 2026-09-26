// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor
import com.intellij.openapi.roots.ui.configuration.ContentRootPanel
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.jetbrains.python.module.PyContentEntriesEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * State for [PySourcesConfigurable].
 *
 * Owns the [ModifiableRootModel] and the wrapped [PyContentEntriesEditor] so the pane never touches
 * either directly. Reads (`ModuleRootManager.modifiableModel`) go through [readAction] and commits
 * through [writeAction] — the pane no longer calls `ReadAction.computeBlocking` on the EDT.
 */
internal class PySourcesModel(private val module: Module) {

  private var modifiableModel: ModifiableRootModel? = null
  private var editor: PyContentEntriesEditor? = null

  /** Project this model's module belongs to. Used by the pane to scope its coroutines. */
  fun moduleProject(): Project = module.project

  /**
   * Builds a fresh [PyContentEntriesEditor] backed by a fresh [ModifiableRootModel]. Runs the
   * model read inside [readAction] and hops to [Dispatchers.EDT] for `createComponent`, which
   * Swing requires on the EDT.
   *
   * [JavaSourceRootType.SOURCE] + [JavaSourceRootType.TEST_SOURCE] pass through to
   * `CommonContentEntriesEditor` as the vararg of `JpsModuleSourceRootType` the tree lets the user
   * mark. Only "Sources" and "Test Sources" are exposed on the Python page — matches the legacy
   * [com.intellij.pycharm.community.ide.impl.configuration.PyContentEntriesModuleConfigurable]
   * (line 53). Other JPS roots (resources, generated) are not offered because pyproject-based
   * tooling ignores them.
   */
  suspend fun createEditor(): PyContentEntriesEditor {
    val model = readAction { ModuleRootManager.getInstance(module).modifiableModel }
    modifiableModel = model
    val state = object : ModuleConfigurationStateImpl(module.project, DefaultModulesProvider(module.project)) {
      override fun getModifiableRootModel(): ModifiableRootModel = model
      override fun getCurrentRootModel(): ModifiableRootModel = model
    }
    val newEditor = object : PyContentEntriesEditor(module, state, true, JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE) {
      override fun createContentEntryEditor(contentEntryUrl: String): ContentEntryEditor {
        val customEditor = object : MyContentEntryEditor(contentEntryUrl, editHandlers) {
          public override fun getContentEntry(): ContentEntry? = super.getContentEntry()
          override fun createContentRootPane(): ContentRootPanel {
            val ownEditor = this
            return object : ContentRootPanel(ownEditor, editHandlers) {
              override fun getContentEntry(): ContentEntry? = ownEditor.getContentEntry()
              override fun addFolderGroupComponents() {
              }

              override fun setSelected(selected: Boolean) {
              }
            }
          }
        }
        contentEntryEditor = customEditor
        return customEditor
      }
    }
    editor = newEditor
    return newEditor
  }

  /**
   * The last editor built by [createEditor], or `null` before the first build and after
   * [disposeEditor]. Nullable on purpose — the pane attaches the editor asynchronously
   * ([PySourcesConfigurable.launchEditorAttach]), so `reset()` and `detachEditor()` need a "no
   * editor yet" guard to skip Swing work that would otherwise blow up on `NullPointerException`.
   */
  fun currentEditor(): PyContentEntriesEditor? = editor

  fun isModified(): Boolean = editor?.isModified == true

  /**
   * Commits the current editor when it is dirty, then recreates a fresh model for the next round of
   * edits. Returns `true` when a commit ran so the caller knows a UI rebuild is due.
   */
  suspend fun apply(): Boolean {
    val currentEditor = editor ?: return false
    val wasModified = currentEditor.isModified
    withContext(Dispatchers.EDT) { currentEditor.apply() }
    if (!wasModified) return false
    writeAction { modifiableModel?.commit() }
    return true
  }

  /** Drops the current [ModifiableRootModel] so the next [createEditor] call starts from a fresh one. */
  fun disposeModel() {
    modifiableModel?.dispose()
    modifiableModel = null
  }

  fun disposeEditor() {
    editor?.disposeUIResources()
    editor = null
  }
}
