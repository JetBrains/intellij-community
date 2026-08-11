// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.ide.projectView.impl.nodes.BasePsiNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownViewNode(
  project: Project?,
  value: MarkdownFileNode,
  viewSettings: ViewSettings?,
  private val children: MutableCollection<out AbstractTreeNode<*>>
) : AbstractPsiBasedNode<MarkdownFileNode>(project, value, viewSettings) {
  override fun extractPsiFromValue(): PsiElement? = children.asSequence()
    .filterIsInstance<BasePsiNode<*>>().firstNotNullOfOrNull { it.value }

  override fun getChildrenImpl(): MutableCollection<out AbstractTreeNode<*>> = children

  override fun getRoots(): Collection<VirtualFile> = children
    .filterIsInstance<ProjectViewNode<*>>()
    .flatMapTo(mutableSetOf()) { it.getRoots() }

  override fun updateImpl(presentation: PresentationData) {
    presentation.setIcon(MarkdownFileType.INSTANCE.icon)
    presentation.presentableText = value.name
  }

  override fun contains(file: VirtualFile): Boolean {
    return children.find { (it as? ProjectViewNode)?.contains(file) == true } != null
  }
}
