// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.gui.filepicker

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.nj2k.gui.common.FilePickListener
import org.jetbrains.kotlin.nj2k.gui.common.FileTreePanel
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.UIManager

class FilePicker(
    private val project: Project,
    private val rootFile: VirtualFile,
    private val convertFiles: MutableList<VirtualFile>
) : DialogWrapper(true), FilePickListener {

    private val fileViewer = FileViewerPanel(project, rootFile)
    private val fileCounter = JBLabel(KotlinBundle.message("action.j2k.gui.file_picker.file_counter", getFileCount(convertFiles))).apply {
        font = JBFont.medium()
        foreground = UIManager.getColor("Component.infoForeground")
    }

    init {
        title = KotlinBundle.message("action.j2k.gui.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        // ファイルピッカー
        val filePicker = FileTreePanel(rootFile, convertFiles, true)
        filePicker.fileSelectionListeners.add(this)
        filePicker.preferredSize = Dimension(400, 400)
        // ビューア
        fileViewer.preferredSize = Dimension(600, 400)
        // パネル作成
        return panel {
            row{
                cell(JBLabel(KotlinBundle.message("action.j2k.gui.file_picker.header")).apply { font = JBFont.h3().asBold() })
            }
            row{
                label(KotlinBundle.message("action.j2k.gui.file_picker.description"))
                bottomGap(BottomGap.SMALL)
            }
            row {
                cell(filePicker).align(Align.FILL).resizableColumn()
                cell(fileViewer).align(Align.FILL)
            }.resizableRow()
            row{
                cell(fileCounter)
            }
        }
    }

    override fun onFilePick(selectedFiles: List<VirtualFile>) {
        isOKActionEnabled = selectedFiles.isNotEmpty()
        fileCounter.text = KotlinBundle.message("action.j2k.gui.file_picker.file_counter", getFileCount(convertFiles))
    }

    override fun onFocus(file: VirtualFile) {
        fileViewer.switchFile(file)
    }

    fun getPickedFiles(): List<VirtualFile> {
        return convertFiles
    }

    private fun getFileCount(files : List<VirtualFile>): Int {
        return files
            .mapNotNull { PsiManager.getInstance(project).findFile(it) as? PsiJavaFile }
            .filter { it.fileType == JavaFileType.INSTANCE }
            .size
    }
}