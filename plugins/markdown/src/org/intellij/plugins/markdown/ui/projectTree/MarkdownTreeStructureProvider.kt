// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownTreeStructureProvider(private val project: Project) : TreeStructureProvider {
  override fun modify(
    parent: AbstractTreeNode<*>,
    children: MutableCollection<AbstractTreeNode<*>>,
    settings: ViewSettings?
  ): MutableCollection<AbstractTreeNode<*>> {
    if (children.all { it.value !is MarkdownFile }) {
      return children
    }

    val result = mutableListOf<AbstractTreeNode<*>>()
    val copyFiles = mutableListOf<AbstractTreeNode<*>>()

    for (child in children) {
      val childValue = child.value

      if (childValue is MarkdownFile && parent.value !is MarkdownFileNode) {
        val mdChildren = findMarkdownFileNodeChildren(childValue as PsiFile, children)

        if (mdChildren.size <= 1) {
          result.add(child)
          continue
        }
        val viewNode = createMarkdownViewNode(mdChildren, settings)

        result.add(viewNode)
        copyFiles.addAll(mdChildren)
      } else {
        result.add(child)
      }
    }

    result.removeAll(copyFiles)

    return result
  }

  private fun findMarkdownFileNodeChildren(
    childValue: PsiFile,
    children: MutableCollection<AbstractTreeNode<*>>
  ): MutableCollection<AbstractTreeNode<*>> {
    val mdFileName = childValue.virtualFile.nameWithoutExtension
    return children.filter { node ->
      val file = (node.value as? PsiFile)?.virtualFile
      file?.let { it.extension?.lowercase() in docExtensions && it.nameWithoutExtension == mdFileName } == true
    }.toMutableList()
  }

  private fun createMarkdownViewNode(mdChildren: MutableCollection<AbstractTreeNode<*>>, settings: ViewSettings?): MarkdownViewNode {
    val mdNodeChildren = mdChildren.map { it.value as PsiFile }
    val markdownNode = MarkdownFileNode(mdNodeChildren)
    return MarkdownViewNode(project, markdownNode, settings, mdChildren)
  }

  companion object {
    private val docExtensions = listOf("pdf", "docx", "html", "md")
  }
}
