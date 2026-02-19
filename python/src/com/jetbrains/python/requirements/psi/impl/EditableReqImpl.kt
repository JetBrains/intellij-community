// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.requirements.psi.EditableReq
import com.jetbrains.python.requirements.psi.UriReference
import com.jetbrains.python.requirements.psi.Visitor

class EditableReqImpl(node: ASTNode) : ASTWrapperPsiElement(node), EditableReq {
  override val uriReference: UriReference?
    get() = findChildByClass(UriReference::class.java)

  override val requirement: String
    get() = "-$text"

  fun accept(visitor: Visitor) {
    visitor.visitEditableReq(this)
  }

  override fun accept(visitor: PsiElementVisitor) {
    if (visitor is Visitor) accept(visitor)
    else super.accept(visitor)
  }

  override fun setName(name: String): PsiElement {
    throw IncorrectOperationException()
  }

  override fun getNameIdentifier(): PsiElement? {
    return uriReference
  }
}
