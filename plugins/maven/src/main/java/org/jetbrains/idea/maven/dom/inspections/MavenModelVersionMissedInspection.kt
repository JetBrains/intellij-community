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
import org.jetbrains.idea.maven.buildtool.quickfix.AddModelVersionQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.UpdateXmlsTo410
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.model.MavenConstants
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
