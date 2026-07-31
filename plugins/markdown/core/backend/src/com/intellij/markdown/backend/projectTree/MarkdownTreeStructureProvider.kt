// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.backend.projectTree

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.BasePsiNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.projectTree.MarkdownFileNode
import org.intellij.plugins.markdown.ui.projectTree.MarkdownViewNode

private val extensionsToFold = listOf("pdf", "docx", "html", "md")

class MarkdownTreeStructureProvider(private val project: Project) : TreeStructureProvider {
  override fun modify(
    parent: AbstractTreeNode<*>,
    children: MutableCollection<AbstractTreeNode<*>>,
    settings: ViewSettings?
  ): MutableCollection<AbstractTreeNode<*>> {
    if (parent is MarkdownViewNode) return children
    if (children.none { it.markdownVirtualFile() != null }) return children
    if (!MarkdownSettings.getInstance(project).isFileGroupingEnabled) return children

    ProgressManager.checkCanceled()
    val documentNodesByName = mutableMapOf<String, MutableList<AbstractTreeNode<*>>>()
    for (child in children) {
      val file = (child as? BasePsiNode<*>)?.virtualFile ?: continue
      if (!file.isDocumentToFold()) continue
      documentNodesByName.getOrPut(file.nameWithoutExtension) { mutableListOf() }.add(child)
    }

    ProgressManager.checkCanceled()
    val result = mutableListOf<AbstractTreeNode<*>>()
    val childrenToRemove = mutableListOf<AbstractTreeNode<*>>()
    for (child in children) {
      val childVirtualFile = child.markdownVirtualFile()
      if (childVirtualFile != null) {
        val markdownChildren = documentNodesByName[childVirtualFile.nameWithoutExtension]
        if (markdownChildren == null || markdownChildren.size <= 1) {
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

  private fun createMarkdownViewNode(
    markdownFile: VirtualFile,
    children: MutableCollection<AbstractTreeNode<*>>,
    settings: ViewSettings?
  ): MarkdownViewNode {
    val markdownNode = MarkdownFileNode(markdownFile.nameWithoutExtension)
    return MarkdownViewNode(project, markdownNode, settings, children)
  }

  private fun VirtualFile.isDocumentToFold(): Boolean =
    fileType == MarkdownFileType.INSTANCE || extension?.lowercase() in extensionsToFold


  private fun AbstractTreeNode<*>.markdownVirtualFile(): VirtualFile? =
    (this as? BasePsiNode<*>)?.virtualFile?.takeIf { it.fileType == MarkdownFileType.INSTANCE }
}
