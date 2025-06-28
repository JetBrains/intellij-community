// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.XmlDomBundle
import com.intellij.util.xml.highlighting.BasicDomElementsInspection
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.model.MavenConstants.*
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenModelVersionMissedInspection : BasicDomElementsInspection<MavenDomProjectModel?>(MavenDomProjectModel::class.java) {
  override fun getGroupDisplayName(): String {
    return MavenDomBundle.message("inspection.group")
  }

  override fun getDefaultLevel(): HighlightDisplayLevel {
    return HighlightDisplayLevel.ERROR
  }

  override fun checkFileElement(
    domFileElement: DomFileElement<MavenDomProjectModel?>,
    holder: DomElementAnnotationHolder,
  ) {
    val projectModel = domFileElement.getRootElement()
    if (projectModel.modelVersion.exists()) return
    val rootTag = domFileElement.rootTag
    if (MavenUtil.isMaven410(
        rootTag?.getAttribute("xmlns")?.value,
        rootTag?.getAttribute("xsi:schemaLocation")?.value)) return
    holder.createProblem(projectModel,
                         HighlightSeverity.ERROR,
                         MavenDomBundle.message("inspection.missed.model.version"),
                         AddModelVersionQuickFix(),
                         UpdateXmlsTo410()


    )
  }
}

private class AddModelVersionQuickFix : LocalQuickFix {
  override fun getName(): String {
    return XmlDomBundle.message("dom.quickfix.insert.required.tag.text", "modelVersion")
  }

  override fun getFamilyName(): String {
    return XmlDomBundle.message("dom.quickfix.insert.required.tag.family")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val tag = PsiTreeUtil.getParentOfType(descriptor.psiElement, XmlTag::class.java, false) ?: return
    val childTag = tag.createChildTag("modelVersion", tag.namespace, MODEL_VERSION_4_0_0, false)
    tag.addSubTag(childTag, true)
  }

}

private class UpdateXmlsTo410 : LocalQuickFix {
  override fun getName(): String {
    return MavenProjectBundle.message("maven.project.updating.model.command.name", "modelVersion")
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return name
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val rootTag = PsiTreeUtil.getParentOfType(descriptor.psiElement, XmlTag::class.java, false) ?: return
    rootTag.setAttribute("xmlns", MAVEN_4_XLMNS)
    rootTag.setAttribute("xsi:schemaLocation", "$MAVEN_4_XLMNS $MAVEN_4_XSD")
  }

}


