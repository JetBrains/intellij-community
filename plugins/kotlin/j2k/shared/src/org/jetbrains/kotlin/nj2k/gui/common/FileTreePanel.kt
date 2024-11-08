// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.gui.common

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.kotlin.idea.util.isJavaFileType
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class FileTreePanel(
    rootFile: VirtualFile,
    private val selectedFiles: MutableList<VirtualFile> = mutableListOf(),
    hasCheckBox: Boolean = false
) : JBPanel<JBPanel<*>>(BorderLayout()) {

    private val tree : CheckboxTree
    private val rootNode : CheckedTreeNode
    val fileSelectionListeners = mutableListOf<FileTreeListener>()

    init {
        // ツリー構築
        rootNode = createNode(rootFile)
        tree = CheckboxTree(CheckBoxTreeCellRenderer(hasCheckBox), rootNode)
        uncheckAllNodes(rootNode)
        checkFilesNodes(selectedFiles)
        expandFilesNodes(selectedFiles)
        // イベント登録
        registerEvents(tree)
        // スクロールパネルを追加
        val scrollpane = JBScrollPane()
        scrollpane.setViewportView(tree)
        add(scrollpane, BorderLayout.CENTER)
    }

    // 再帰的にノード作成
    private fun createNode(file: VirtualFile): CheckedTreeNode {
        val node = CheckedTreeNode(file)
        if (file.isDirectory) {
            file.children.filter { it.isJavaFileType() || it.isDirectory }.forEach { child ->
                node.add(createNode(child))
            }
        }
        return node
    }

    // 全てのノードをチェック解除
    fun uncheckAllNodes(node: CheckedTreeNode) {
        node.isChecked = false
        node.children().asSequence().filterIsInstance<CheckedTreeNode>().forEach { uncheckAllNodes(it) }
    }

    // 指定したノードをチェック
    private fun checkNodes(node: CheckedTreeNode, files: List<VirtualFile>) {
        if (files.contains(node.userObject)) {
            node.isChecked = true
        }
        node.children().asSequence().filterIsInstance<CheckedTreeNode>().forEach {
            checkNodes(it, files)
        }
    }

    // 指定したファイルのノードをチェック
    fun checkFilesNodes(files: List<VirtualFile>) {
        checkNodes(rootNode, files)
    }

    // 指定したノードを展開する.Leafをexpandしても反映されない不具合があるため、親ディレクトリをexpandする
    private fun expandNodes(jTree: JTree, node: CheckedTreeNode, files: List<VirtualFile>) {
        val dirs = files.mapTo(mutableSetOf()) { if (it.isFile) it.parent else it }
        fun CheckedTreeNode.expandNodesInner(files: Set<VirtualFile>) {
            if (files.contains(this.userObject)) {
                jTree.expandPath(TreePath(this.path))
            }
            children().asSequence()
                .filterIsInstance<CheckedTreeNode>()
                .forEach { it.expandNodesInner(files) }
        }
        node.expandNodesInner(dirs)
    }

    // 指定したファイルのノードを展開
    fun expandFilesNodes(files: List<VirtualFile>) {
        expandNodes(tree, rootNode, files)
    }


    // イベント登録
    private fun registerEvents(checkBoxTree: CheckboxTree) {
        // チェクボックスの状態変更イベント
        checkBoxTree.addCheckboxTreeListener(object : CheckboxTreeListener {
            override fun nodeStateChanged(node: CheckedTreeNode) {
                super.nodeStateChanged(node)
                (node.userObject as? VirtualFile)?.let { file ->
                    if (node.isChecked) selectedFiles.add(file) else selectedFiles.remove(file)
                    fileSelectionListeners.forEach { listener ->
                        listener.onFilePick(selectedFiles)
                    }
                }
            }
        })
        // クリックイベント
        checkBoxTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val node = checkBoxTree.getLastSelectedPathComponent() as? DefaultMutableTreeNode
                (node?.userObject as? VirtualFile)?.let { file ->
                    fileSelectionListeners.forEach { listener ->
                        listener.onFocus(file)
                    }
                }
            }
        })
    }

    // カスタムセルレンダラ
    private class CheckBoxTreeCellRenderer(private val hasCheckBox: Boolean) : CheckboxTree.CheckboxTreeCellRenderer() {
        override fun customizeRenderer(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            if (value !is DefaultMutableTreeNode) return
            // チェックボックス表示設定
            checkbox.isVisible = hasCheckBox
            // アイコン/テキスト設定
            (value.userObject as? VirtualFile)?.let { file ->
                textRenderer.append(file.name)
                textRenderer.icon = when {
                    file.isDirectory -> AllIcons.Nodes.Folder
                    file.isFile -> FileTypeManager.getInstance().getFileTypeByFile(file).icon
                    else -> null
                }
            }
        }
    }

}

interface FileTreeListener {
    fun onFilePick(selectedFiles: List<VirtualFile>)
    fun onFocus(file: VirtualFile)
}
