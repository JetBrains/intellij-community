// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.gui.filepicker

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout

class FileViwerPanel(private val project: Project, file: VirtualFile) : JBPanel<JBPanel<*>>(BorderLayout()) {

    // エディタ
    private val editorTextField: EditorTextField

    init {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: getEmptyDocument()
        val fileType = FileTypeManager.getInstance().getFileTypeByFile(file)
        editorTextField = CustomEditorTextField(document, project, fileType, true).apply {
            // ファイルの最初の行にフォーカスを当てる
            setCaretPosition(0)
            // 複数行での表示にする
            setOneLineMode(false)
        }
        // リサイズさせるためにCENTERに配置
        add(editorTextField, BorderLayout.CENTER)
    }

    // ファイル切り替えメソッド
    fun switchFile(javaFile: VirtualFile) {
        val document = FileDocumentManager.getInstance().getDocument(javaFile) ?: getEmptyDocument()
        val fileType = FileTypeManager.getInstance().getFileTypeByFile(javaFile)
        editorTextField.fileType = fileType
        editorTextField.document = document
        // カーソル位置を戦闘に戻す
        invokeLater {
            editorTextField.setCaretPosition(0)
            editorTextField.editor?.scrollingModel?.scrollToCaret(ScrollType.RELATIVE)
        }
    }

    // 空のドキュメントを取得する
    private fun getEmptyDocument() = EditorFactory.getInstance().createDocument("")
}

// 常時スクロールバーを表示するカスタムEditorTextField
internal class CustomEditorTextField(document: Document?, project: Project, fileType: FileType, isViewer: Boolean) :
    EditorTextField(document, project, fileType, isViewer) {

    override fun createEditor(): EditorEx {
        val editor = super.createEditor()
        editor.setVerticalScrollbarVisible(true)
        editor.setHorizontalScrollbarVisible(true)
        return editor
    }

}