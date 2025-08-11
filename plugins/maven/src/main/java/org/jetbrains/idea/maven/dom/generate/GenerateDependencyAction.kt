// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.generate

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId.Companion.getId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction
import org.jetbrains.idea.maven.dom.DependencyConflictId
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil.createDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog
import org.jetbrains.idea.maven.model.MavenCoordinate
import java.util.*
import java.util.function.Function

internal class GenerateDependencyAction : GenerateDomElementAction(GenerateDependencyProvider(), AllIcons.Nodes.PpLib) {
  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun isValidForFile(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
    return super.isValidForFile(project, editor, psiFile)
           && Optional.ofNullable<IdeaPluginDescriptor?>(getPlugin(getId("com.jetbrains.packagesearch.intellij-plugin"))).map(
      Function { p: IdeaPluginDescriptor? -> !p!!.isEnabled }).orElse(true)
  }
}

internal class GenerateDependencyProvider : MavenGenerateProvider<MavenDomDependency>(MavenDomBundle.message("generate.dependency.title"), MavenDomDependency::class.java) {
  override fun doGenerate(mavenModel: MavenDomProjectModel, editor: Editor): MavenDomDependency? {
    val project = mavenModel.getManager().getProject()

    val managedDependencies = GenerateManagedDependencyAction.collectManagingDependencies(mavenModel)

    val ids = MavenArtifactSearchDialog.searchForArtifact(project, managedDependencies.values)
    if (ids.isEmpty()) return null

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val psiFile = DomUtil.getFile(mavenModel)
    return createDependencyInWriteAction(mavenModel, editor, managedDependencies, ids, psiFile)
  }

  private fun createDependencyInWriteAction(
    mavenModel: MavenDomProjectModel,
    editor: Editor,
    managedDependencies: MutableMap<DependencyConflictId?, MavenDomDependency?>,
    ids: List<MavenCoordinate>,
    psiFile: XmlFile,
  ): MavenDomDependency? {
    return WriteCommandAction.writeCommandAction(psiFile.getProject(), psiFile).withName(MavenDomBundle.message("generate.dependency"))
      .compute<MavenDomDependency?, RuntimeException?>(
        ThrowableComputable { createDependency(mavenModel, editor, managedDependencies, ids) })
  }

  companion object {
    fun createDependency(
      mavenModel: MavenDomProjectModel,
      editor: Editor,
      managedDependencies: MutableMap<DependencyConflictId?, MavenDomDependency?>,
      ids: List<MavenCoordinate>,
    ): MavenDomDependency? {
      val isInsideManagedDependencies: Boolean

      val dependencyManagement = mavenModel.getDependencyManagement()
      val managedDependencyXml = dependencyManagement.getXmlElement()
      if (managedDependencyXml != null && managedDependencyXml.getTextRange().contains(editor.getCaretModel().getOffset())) {
        isInsideManagedDependencies = true
      }
      else {
        isInsideManagedDependencies = false
      }

      for (each in ids) {
        val res: MavenDomDependency?
        if (isInsideManagedDependencies) {
          res = createDomDependency(dependencyManagement.getDependencies(), editor, each)
        }
        else {
          val conflictId = DependencyConflictId(each.getGroupId(), each.getArtifactId(), null, null)
          val managedDependenciesDom = managedDependencies.get(conflictId)

          if (managedDependenciesDom != null
              && each.getVersion() == managedDependenciesDom.getVersion().getStringValue()
          ) {
            // Generate dependency without <version> tag
            res = createDomDependency(mavenModel.getDependencies(), editor)

            res.getGroupId().setStringValue(conflictId.getGroupId())
            res.getArtifactId().setStringValue(conflictId.getArtifactId())
          }
          else {
            res = createDomDependency(mavenModel.getDependencies(), editor, each)
          }
        }
        return (res)
      }
      return null
    }
  }
}

