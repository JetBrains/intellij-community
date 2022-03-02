// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager.Companion.getInstance
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.SmartList
import com.intellij.util.ui.UIUtil
import org.jetbrains.idea.maven.importing.MavenProjectImporter.Companion.isImportToTreeStructureEnabled
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
        if (isImportToTreeStructureEnabled() && child is PsiDirectoryNode && parent is PsiDirectoryNode) {
          childToAdd = getMavenModuleNode(project, child, settings) ?: child
        }
        modifiedChildren.add(childToAdd)
      }
      return modifiedChildren
    }
    return children
  }

  private fun getMavenModuleNode(project: Project,
                                  directoryNode: PsiDirectoryNode,
                                  settings: ViewSettings): MavenModuleDirectoryNode? {
    val psiDirectory = directoryNode.value ?: return null
    val virtualFile = psiDirectory.virtualFile
    if (!ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) return null
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val module = fileIndex.getModuleForFile(virtualFile)
    if (!isMavenModule(module)) return null
    val moduleShortName: String = getModuleShortName(module) ?: return null
    return MavenModuleDirectoryNode(project, psiDirectory, settings, moduleShortName, directoryNode.filter)
  }

  private fun isMavenModule(module: Module?): Boolean {
    return if (module != null && !module.isDisposed) getInstance(module).isMavenized() else false
  }

  private fun getModuleShortName(module: Module?): String? {
    if (module != null) {
      if (module.name.endsWith(".test")) {
        return "test";
      }
      if (module.name.endsWith(".main")) {
        return "main";
      }
    }
    return null;
  }

  private inner class MavenPomFileNode(project: Project?,
                                       value: PsiFile,
                                       viewSettings: ViewSettings?,
                                       val myIgnored: Boolean) : PsiFileNode(project, value, viewSettings) {
    val strikeAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, UIUtil.getInactiveTextColor())
    override fun updateImpl(data: PresentationData) {
      if (myIgnored) {
        data.addText(value.name, strikeAttributes)
      }
      super.updateImpl(data)
    }

    @Suppress("DEPRECATION")
    override fun getTestPresentation(): String? {
      if (myIgnored) {
        return "-MavenPomFileNode:" + super.getTestPresentation() + " (ignored)"
      } else {
        return "-MavenPomFileNode:" + super.getTestPresentation()
      }
    }
  }
}