// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.lang.MarkdownFileType

internal class MarkdownViewNode(
  project: Project?,
  value: MarkdownFileNode,
  viewSettings: ViewSettings?,
  private val children: MutableCollection<out AbstractTreeNode<*>>
) : ProjectViewNode<MarkdownFileNode>(project, value, viewSettings) {
  override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> = children

  override fun update(presentation: PresentationData) {
    presentation.setIcon(MarkdownFileType.INSTANCE.icon)
    presentation.presentableText = value.name
  }

  override fun contains(file: VirtualFile): Boolean {
    return children.find { (it as? ProjectViewNode)?.contains(file) == true } != null
  }
}
