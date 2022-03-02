// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.ui.floating.FloatingToolbar
import org.jetbrains.annotations.ApiStatus

/**
 * This provider will create default text editor and attach [FloatingToolbar].
 * Toolbar will be disposed right before parent editor disposal.
 *
 * This provider is not registered in plugin.xml, so it can be used only manually.
 */
@ApiStatus.Experimental
class MarkdownTextEditorProvider: PsiAwareTextEditorProvider() {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!super.accept(project, file)) {
      return false
    }
    return FileTypeRegistry.getInstance().isFileOfType(file, MarkdownFileType.INSTANCE) || shouldAcceptScratchFile(project, file)
  }

  private fun shouldAcceptScratchFile(project: Project, file: VirtualFile): Boolean {
    return ScratchUtil.isScratch(file) && LanguageUtil.getLanguageForPsi(project, file, file.fileType) == MarkdownLanguage.INSTANCE
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val actualEditor = super.createEditor(project, file)
    if (actualEditor is TextEditor && !AdvancedSettings.getBoolean("markdown.hide.floating.toolbar")) {
      val toolbar = FloatingToolbar(actualEditor.editor, "Markdown.Toolbar.Floating")
      Disposer.register(actualEditor, toolbar)
    }
    return actualEditor
  }
}
