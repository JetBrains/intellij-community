/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package com.intellij.toml.frontend.split

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_BACKSPACE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MATCH_BRACE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.jetbrains.rd.ide.model.ActionCallStrategyKind
import com.jetbrains.rdclient.editorActions.cwm.FrontendEditorActionHandlerStrategyCustomizer
import org.toml.lang.psi.TomlFileType

class TomlFrontendEditorActionCustomizer : FrontendEditorActionHandlerStrategyCustomizer {
    override fun getCustomStrategy(actionId: String, editor: Editor, caret: Caret?, dataContext: DataContext): ActionCallStrategyKind? {
        if (actionId !in SUPPRESSED_ACTION_IDS) return null
        val editedFileName = editor.virtualFile?.nameSequence ?: return null
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(editedFileName)
        return if (fileType == TomlFileType) ActionCallStrategyKind.FrontendOnly else null
    }
}

private val SUPPRESSED_ACTION_IDS = setOf(ACTION_EDITOR_COMPLETE_STATEMENT,
                                          ACTION_EDITOR_MATCH_BRACE,
                                          ACTION_EDITOR_BACKSPACE,
                                          ACTION_EDITOR_SELECT_WORD_AT_CARET,
                                          ACTION_EDITOR_UNSELECT_WORD_AT_CARET)