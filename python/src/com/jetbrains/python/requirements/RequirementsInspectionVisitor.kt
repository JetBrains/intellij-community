// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.requirements.psi.UriReference

open class RequirementsInspectionVisitor(val holder: ProblemsHolder,
                                         val session: LocalInspectionToolSession) : PsiElementVisitor() {

  override fun visitElement(element: PsiElement) {
    when (element) {
      is RequirementsFile -> visitRequirementsFile(element)
      is UriReference -> visitUriReference(element)
      is NameReq -> visitNameReq(element)
      else -> super.visitElement(element)
    }
  }

  open fun visitUriReference(element: UriReference) {
    super.visitElement(element)
  }

  open fun visitNameReq(element: NameReq) {
    super.visitElement(element)
  }

  open fun visitRequirementsFile(element: RequirementsFile) {
    super.visitElement(element)
  }
}
