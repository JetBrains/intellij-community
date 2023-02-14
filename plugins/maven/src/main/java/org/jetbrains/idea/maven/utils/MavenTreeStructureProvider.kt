// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.SmartList
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenTreeStructureProvider : TreeStructureProvider, DumbAware {
  override fun modify(parent: AbstractTreeNode<*>,
                      children: Collection<AbstractTreeNode<*>>,
                      settings: ViewSettings): Collection<AbstractTreeNode<*>> {
    val project = parent.project ?: return children
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) {
      return children
    }
    if (parent is ProjectViewProjectNode || parent is PsiDirectoryNode) {
      val modifiedChildren: MutableCollection<AbstractTreeNode<*>> = SmartList()
      for (child in children) {
        var childToAdd = child
        if (child is PsiFileNode) {
          if (child.virtualFile != null && MavenUtil.isPotentialPomFile(child.virtualFile!!.name)) {
            val mavenProject = manager.findProject(child.virtualFile!!)
            if (mavenProject != null) {
              childToAdd = MavenPomFileNode(project, child.value, settings, manager.isIgnored(mavenProject))
            }

          }
        }
        modifiedChildren.add(childToAdd)
      }
      return modifiedChildren
    }
    return children
  }

  private inner class MavenPomFileNode(project: Project?,
                                       value: PsiFile,
                                       viewSettings: ViewSettings?,
                                       val myIgnored: Boolean) : PsiFileNode(project, value, viewSettings) {
    val strikeAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, NamedColorUtil.getInactiveTextColor())
    override fun updateImpl(data: PresentationData) {
      if (myIgnored) {
        data.addText(value.name, strikeAttributes)
      }
      super.updateImpl(data)
    }

    @Suppress("DEPRECATION")
    override fun getTestPresentation(): String {
      if (myIgnored) {
        return "-MavenPomFileNode:" + super.getTestPresentation() + " (ignored)"
      } else {
        return "-MavenPomFileNode:" + super.getTestPresentation()
      }
    }
  }
}