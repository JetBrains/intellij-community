// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTag
import com.intellij.util.Processor
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.idea.maven.dom.MavenDomUtil.getMavenDomProjectModel
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.references.MavenModulePsiReference
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/**
 * Searches for module references in Maven POM files that should be renamed as part of the
 * "Rename Directory" refactoring.
 *
 * For example:
 * ```
 * <modules>
 *   <module>my_module_name</module>
 * </modules>
 * ```
 */
internal class MavenModuleReferenceSearcher : QueryExecutorBase<PsiReference?, ReferencesSearch.SearchParameters?>() {
  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference?>) {
    val directory = queryParameters.elementToSearch
    if (directory is PsiDirectory) {
      val project = queryParameters.project
      val projectsManager = MavenProjectsManager.getInstance(project)
      if (!projectsManager.isInitialized) return
      for (mavenProject in projectsManager.getProjects()) {
        processProject(project, mavenProject, directory, consumer)
      }
    }
  }

  private fun getPomTagReferencesToDirectory(
    tag: XmlTag?,
    pomFile: VirtualFile,
    directory: VirtualFile,
  ): List<PsiReference> {
    try {
      return doGetPomTagReferencesToDirectory(tag, pomFile, directory)
    }
    catch (_: InvalidPathException) {
      return listOf()
    }
  }

  private fun doGetPomTagReferencesToDirectory(
    tag: XmlTag?,
    pomFile: VirtualFile,
    directory: VirtualFile,
  ): List<PsiReference> {
    val references = ArrayList<PsiReference>()
    if (null != tag) {
      val oldDirectoryPath = Paths.get(directory.getPath()).normalize()
      val modulePath = tag.getValue().getTrimmedText()
      var tmpPath = Paths.get(pomFile.getParent().getPath()).normalize()
      val referencedDirectoryPath = Paths.get(pomFile.getParent().getPath(), modulePath).normalize()
      if (referencedDirectoryPath.startsWith(oldDirectoryPath)) {
        if (tag is ASTNode) {
          val textTag = tag.findChildByType(XmlElementType.XML_TEXT)
          if (null != textTag) {
            var from = textTag.getStartOffsetInParent()
            val length = directory.getName().length
            val items: Array<String?> =
              modulePath.split(DELIMITER_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (item in items) {
              tmpPath = Paths.get(tmpPath.toString(), item).normalize()
              if (DELIMITER != item && tmpPath == oldDirectoryPath) {
                references.add(MavenModulePsiReference(tag, tag.getText(), TextRange(from, from + length)))
              }
              from += item!!.length
            }
          }
        }
      }
    }
    return references
  }

  private fun processProject(
    project: Project,
    mavenProject: MavenProject,
    directory: PsiDirectory,
    consumer: Processor<in PsiReference?>,
  ) {
    val pomFile = mavenProject.file
    val mavenModel = ReadAction.compute<MavenDomProjectModel?, RuntimeException?>(ThrowableComputable {
      getMavenDomProjectModel(
        project,
        pomFile
      )
    })
    if (null == mavenModel) return
    val references = ReadAction.compute<@Unmodifiable List<PsiReference>, RuntimeException> {
      mavenModel.modules.modules.flatMap { mavenModule ->
        getPomTagReferencesToDirectory(
          mavenModule.xmlTag,
          pomFile,
          directory.virtualFile,
        )
      }
    }
    for (reference in references) {
      consumer.process(reference)
    }
  }
}

private const val DELIMITER = "/"

// Split with lookaheads and lookbehinds to keep the delimiters.
private const val DELIMITER_REGEX = "((?<=$DELIMITER)|(?=$DELIMITER))"