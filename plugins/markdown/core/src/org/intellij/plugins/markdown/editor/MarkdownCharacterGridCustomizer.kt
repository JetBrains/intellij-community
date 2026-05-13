// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.DoubleWidthCharacterStrategy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import org.intellij.plugins.markdown.editor.tables.TableCharacterWidthUtils
import org.intellij.plugins.markdown.lang.hasMarkdownType

internal class MarkdownCharacterGridCustomizer : TextEditorCustomizer {
  override suspend fun execute(textEditor: TextEditor) {
    if (!textEditor.file.hasMarkdownType()) return

    val editor = textEditor.editor as? EditorImpl ?: return
    editor.settings.characterGridWidthMultiplier = 1.0f

    val grid = editor.characterGrid ?: return
    grid.doubleWidthCharacterStrategy = DoubleWidthCharacterStrategy { codePoint ->
      TableCharacterWidthUtils.isFullWidthCharacter(codePoint)
    }
  }
}
