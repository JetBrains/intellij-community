// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.XmlDomBundle
import org.jetbrains.idea.maven.model.MavenConstants.MODEL_VERSION_4_0_0

class AddModelVersionQuickFix : LocalQuickFix {
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
