// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenConstants.MAVEN_4_XLMNS
import org.jetbrains.idea.maven.model.MavenConstants.MAVEN_4_XSD
import org.jetbrains.idea.maven.project.MavenProjectBundle

class UpdateXmlsTo410 : LocalQuickFix {
  override fun getName(): String {
    return MavenProjectBundle.message("maven.project.updating.model.command.name", "modelVersion")
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return name
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {

    val rootTag = findProjectTag(descriptor) ?: return
    val xmlnsXsi = rootTag.getAttribute("xmlns:xsi")
    rootTag.setAttribute("xmlns", MAVEN_4_XLMNS)
    if (xmlnsXsi == null) {
      rootTag.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
    }
    rootTag.setAttribute("xsi:schemaLocation", "$MAVEN_4_XLMNS $MAVEN_4_XSD")
    val modelVersion = rootTag.findSubTags("modelVersion")
    if (modelVersion.isNotEmpty()) {
      modelVersion.forEach { it.value.setText(MavenConstants.MODEL_VERSION_4_1_0) }
    }
  }

  private fun findProjectTag(descriptor: ProblemDescriptor): XmlTag? {
    var tag = PsiTreeUtil.getParentOfType(descriptor.psiElement, XmlTag::class.java, false)
    while (tag != null) {
      if (tag.name == "project") return tag
      tag = tag.parentTag
    }
    return null
  }

}
