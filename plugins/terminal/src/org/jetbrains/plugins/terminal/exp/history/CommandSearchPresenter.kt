// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.history

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.containers.map2Array

class CommandSearchPresenter(private val project: Project, private val editor: Editor) {
  fun showCommandSearch(history: List<String>) {
    val command = editor.document.text
    val arranger = LookupArranger.DefaultArranger()
    // Reverse the history to move the most recent values to the top.
    // It will be reversed again internally to show them at the bottom.
    val elements = history.asReversed().map2Array { LookupElementBuilder.create(it) }
    val lookup = LookupManager.getInstance(project).createLookup(editor, elements, command, arranger) as LookupImpl
    lookup.putUserData(IS_COMMAND_SEARCH_LOOKUP_KEY, true)

    if (lookup.showLookup()) {
      lookup.ensureSelectionVisible(false)
    }
  }

  companion object {
    private val IS_COMMAND_SEARCH_LOOKUP_KEY: Key<Boolean> = Key.create("isCommandSearchLookup")

    val Lookup.isTerminalCommandSearch: Boolean
      get() = (this as? UserDataHolder)?.getUserData(IS_COMMAND_SEARCH_LOOKUP_KEY) == true
  }
}