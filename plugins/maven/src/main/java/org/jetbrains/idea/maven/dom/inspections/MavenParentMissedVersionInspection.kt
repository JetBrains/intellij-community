// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.XmlDomBundle
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomElementsInspection
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.converters.MavenConsumerPomUtil.isConsumerPomResolutionApplicable
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

class MavenParentMissedVersionInspection : DomElementsInspection<MavenDomProjectModel?>(MavenDomProjectModel::class.java) {
  override fun checkFileElement(domFileElement: DomFileElement<MavenDomProjectModel?>,
                                holder: DomElementAnnotationHolder) {
    val model = domFileElement.rootElement
    val parent = model.mavenParent
    if (!parent.exists()) {
      return
    }

    if (!parent.version.exists() &&
        !isConsumerPomResolutionApplicable(domFileElement.file.project)) {
      val version = getParentVersion(domFileElement.file, model)
      listOf(
        holder.createProblem(
          parent,
          HighlightSeverity.ERROR,
          XmlDomBundle.message("dom.inspections.child.tag.0.should.be.defined", "version"),
          AddVersionQuickFix(version)
        )
      )
    }
  }

  private fun getParentVersion(currentFile: XmlFile, model: MavenDomProjectModel): String {
    val xmlFileParent = currentFile.parent?.parent?.findFile("pom.xml") as? XmlFile ?: return "";
    val parentModel = MavenDomUtil.getMavenDomModel(xmlFileParent, MavenDomProjectModel::class.java) ?: return "";
    val parentElement = model.mavenParent
    if (parentModel.artifactId.value == parentElement.artifactId.value && parentModel.groupId.value == parentElement.groupId.value) {
      return parentModel.version.value ?: ""
    }
    return ""
  }

  override fun getGroupDisplayName(): String {
    return MavenDomBundle.message("inspection.group")
  }

  override fun getShortName(): String {
    return "MavenParentMissedVersionInspection"
  }

  override fun getDefaultLevel(): HighlightDisplayLevel {
    return HighlightDisplayLevel.ERROR
  }

  private class AddVersionQuickFix(private val myVersion: String) : LocalQuickFix {
    override fun getName(): String {
      return XmlDomBundle.message("dom.quickfix.insert.required.tag.text", "version")
    }

    override fun getFamilyName(): String {
      return XmlDomBundle.message("dom.quickfix.insert.required.tag.family")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val tag = PsiTreeUtil.getParentOfType(descriptor.psiElement,
                                            XmlTag::class.java, false)
      tag?.add(tag.createChildTag("version", tag.namespace, myVersion, false))
    }

  }
}