// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.gui.previewer

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.nj2k.gui.common.FileTreePanel
import java.awt.Dimension
import javax.swing.JComponent

// 変換プレビュー
class Previewer(private val rootFile: VirtualFile) : DialogWrapper(true) {

    init {
        title = KotlinBundle.message("action.j2k.gui.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        // ファイルエクスプローラ
        val fileExplorer = FileTreePanel(rootFile)
        fileExplorer.preferredSize = Dimension(400, 400)
        // パネル作成
        return panel {
            row {
                cell(fileExplorer)
            }
        }
    }
}