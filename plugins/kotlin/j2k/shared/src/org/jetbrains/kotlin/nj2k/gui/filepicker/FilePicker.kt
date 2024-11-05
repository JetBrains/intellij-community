// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.gui.filepicker

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import java.awt.Dimension
import javax.swing.JComponent

class FilePicker(
    private val project: Project,
    private val rootFile: VirtualFile,
    private val convertFiles: MutableList<VirtualFile>
) : DialogWrapper(true) , FilePickListener{

    private val fileViewer = FileViewerPanel(project, rootFile)

    init {
        title = KotlinBundle.message("action.j2k.gui.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        // ファイルピッカー
        val filePicker = FilePickerPanel(project, rootFile, convertFiles)
        filePicker.fileSelectionListeners.add(this)
        filePicker.preferredSize = Dimension(400, 400)
        // ビューア
        fileViewer.preferredSize = Dimension(600, 400)
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
        }
    }

    override fun onFilePick(selectedFiles: List<VirtualFile>) {
        isOKActionEnabled = selectedFiles.isNotEmpty()
    }

    override fun onFocus(file: VirtualFile) {
        fileViewer.switchFile(file)
    }

    fun getPickedFiles(): List<VirtualFile> {
        return convertFiles
    }
}