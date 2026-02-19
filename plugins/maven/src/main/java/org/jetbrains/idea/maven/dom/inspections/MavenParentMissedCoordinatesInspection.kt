// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.XmlDomBundle
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor
import com.intellij.util.xml.highlighting.DomElementsInspection
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.model.MavenDomParent
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

abstract class MavenParentMissedCoordinatesInspection : DomElementsInspection<MavenDomProjectModel?>(MavenDomProjectModel::class.java) {
  override fun getGroupDisplayName(): String {
    return MavenDomBundle.message("inspection.group")
  }

  override fun getDefaultLevel(): HighlightDisplayLevel {
    return HighlightDisplayLevel.ERROR
  }

  protected fun getParentIfExists(domFileElement: DomFileElement<MavenDomProjectModel?>): MavenDomParent? {
    val model = domFileElement.rootElement
    val parent = model.mavenParent
    return parent.takeIf { it.exists() }
  }

  protected fun reportMissedChildTagProblem(
    holder: DomElementAnnotationHolder,
    parent: MavenDomParent,
    tagName: String,
    tagValue: String = "",
  ): DomElementProblemDescriptor? = holder.createProblem(
    parent,
    HighlightSeverity.ERROR,
    XmlDomBundle.message("dom.inspections.child.tag.0.should.be.defined", tagName),
    AddChildTagQuickFix(tagName, tagValue)
  )

  private class AddChildTagQuickFix(private val tagName: String, private val tagValue: String?) : LocalQuickFix {
    override fun getName(): String {
      return XmlDomBundle.message("dom.quickfix.insert.required.tag.text", tagName)
    }

    override fun getFamilyName(): String {
      return XmlDomBundle.message("dom.quickfix.insert.required.tag.family")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val tag = PsiTreeUtil.getParentOfType(descriptor.psiElement, XmlTag::class.java, false)
      tag?.add(tag.createChildTag(tagName, tag.namespace, tagValue, false))
    }
  }
}