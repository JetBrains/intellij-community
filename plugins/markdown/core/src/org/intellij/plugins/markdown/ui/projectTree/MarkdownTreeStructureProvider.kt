// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.BasePsiNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.settings.MarkdownSettings

private val extensionsToFold = listOf("pdf", "docx", "html", "md")

class MarkdownTreeStructureProvider(private val project: Project) : TreeStructureProvider {
  override fun modify(
    parent: AbstractTreeNode<*>,
    children: MutableCollection<AbstractTreeNode<*>>,
    settings: ViewSettings?
  ): MutableCollection<AbstractTreeNode<*>> {
    if (parent is MarkdownViewNode) return children
    if (children.none { it.markdownVirtualFile() != null }) {
      return children
    }
    val result = mutableListOf<AbstractTreeNode<*>>()
    val childrenToRemove = mutableListOf<AbstractTreeNode<*>>()
    for (child in children) {
      val childVirtualFile = child.markdownVirtualFile()
      if (childVirtualFile != null) {
        val markdownChildren = findMarkdownFileNodeChildren(childVirtualFile, children)
        if (markdownChildren.size <= 1) {
          result.add(child)
          continue
        }
        result.add(createMarkdownViewNode(childVirtualFile, markdownChildren, settings))
        childrenToRemove.addAll(markdownChildren)
      } else {
        result.add(child)
      }
    }
    result.removeAll(childrenToRemove)
    return result
  }

  private fun findMarkdownFileNodeChildren(
    markdownFile: VirtualFile,
    children: MutableCollection<AbstractTreeNode<*>>
  ): MutableCollection<AbstractTreeNode<*>> {
    val fileName = markdownFile.nameWithoutExtension
    return children.asSequence().filter { node ->
      val file = (node as? BasePsiNode<*>)?.virtualFile
      file?.let { isDocumentsGroupingEnabled(it, fileName) } == true
    }.toMutableList()
  }

  private fun createMarkdownViewNode(
    markdownFile: VirtualFile,
    children: MutableCollection<AbstractTreeNode<*>>,
    settings: ViewSettings?
  ): MarkdownViewNode {
    val markdownNode = MarkdownFileNode(markdownFile.nameWithoutExtension)
    return MarkdownViewNode(project, markdownNode, settings, children)
  }

  private fun isDocumentsGroupingEnabled(file: VirtualFile, fileName: String) =
    (file.fileType == MarkdownFileType.INSTANCE || file.extension?.lowercase() in extensionsToFold) &&
    file.nameWithoutExtension == fileName &&
    MarkdownSettings.getInstance(project).isFileGroupingEnabled


  private fun AbstractTreeNode<*>.markdownVirtualFile(): VirtualFile? =
    (this as? BasePsiNode<*>)?.virtualFile?.takeIf { it.fileType == MarkdownFileType.INSTANCE }
}
