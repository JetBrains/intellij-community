// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownTreeStructureProvider(private val project: Project) : TreeStructureProvider {
  override fun modify(
    parent: AbstractTreeNode<*>,
    children: MutableCollection<AbstractTreeNode<*>>,
    settings: ViewSettings?
  ): MutableCollection<AbstractTreeNode<*>> {
    if (children.find { it.value is MarkdownFile } == null) {
      return children
    }
    val result = mutableListOf<AbstractTreeNode<*>>()
    val childrenToRemove = mutableListOf<AbstractTreeNode<*>>()
    for (child in children) {
      val childValue = (child.value as? MarkdownFile)
      val childVirtualFile = childValue?.virtualFile
      if (childVirtualFile != null && parent.value !is MarkdownFileNode) {
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
      val file = (node.value as? PsiFile)?.virtualFile
      file?.let { it.extension?.lowercase() in extensionsToFold && it.nameWithoutExtension == fileName } == true
    }.toMutableList()
  }

  private fun createMarkdownViewNode(
    markdownFile: VirtualFile,
    children: MutableCollection<AbstractTreeNode<*>>,
    settings: ViewSettings?
  ): MarkdownViewNode {
    val nodeChildren = children.mapNotNull { it.value as? PsiFile }
    val markdownNode = MarkdownFileNode(markdownFile.nameWithoutExtension, nodeChildren)
    return MarkdownViewNode(project, markdownNode, settings, children)
  }

  companion object {
    private val extensionsToFold = listOf("pdf", "docx", "html", "md")
  }
}
