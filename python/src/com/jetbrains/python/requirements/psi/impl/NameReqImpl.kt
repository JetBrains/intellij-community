// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a generated file. Not intended for manual editing.
package com.jetbrains.python.requirements.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.requirements.createVersionspec
import com.jetbrains.python.requirements.psi.Extras
import com.jetbrains.python.requirements.psi.HashOption
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.requirements.psi.QuotedMarker
import com.jetbrains.python.requirements.psi.SimpleName
import com.jetbrains.python.requirements.psi.Versionspec
import com.jetbrains.python.requirements.psi.Visitor

class NameReqImpl(node: ASTNode) : ASTWrapperPsiElement(node), NameReq {
  override val name: SimpleName
    get() = findNotNullChildByClass(SimpleName::class.java)

  override val extras: Extras?
    get() = findChildByClass(Extras::class.java)

  override val versionspec: Versionspec?
    get() = findChildByClass(Versionspec::class.java)

  override val hashOptionList: List<HashOption?>
    get() = PsiTreeUtil.getChildrenOfTypeAsList(this, HashOption::class.java)

  override val quotedMarker: QuotedMarker?
    get() = findChildByClass(QuotedMarker::class.java)

  fun accept(visitor: Visitor) {
    visitor.visitNameReq(this)
  }

  override fun accept(visitor: PsiElementVisitor) {
    if (visitor is Visitor)
      accept(visitor)
    else
      super.accept(visitor)
  }

  override fun setName(name: String): PsiElement {
    throw IncorrectOperationException()
  }

  override fun getName(): String? {
    return nameIdentifier.text
  }

  override fun getNameIdentifier(): PsiElement {
    return name
  }

  override val requirement: String
    get() = text

  override fun setVersion(newVersion: String) {
    WriteCommandAction.runWriteCommandAction(project,
                                             "Update package version",
                                             "Requirements", {
                                               val newVersionSpecNode = createVersionspec(project, newVersion)?.node
                                               if (newVersionSpecNode != null) {
                                                 val oldVersionSpecNode = versionspec?.node
                                                 if (oldVersionSpecNode == null) {
                                                   node.addChild(newVersionSpecNode)
                                                 } else {
                                                   node.replaceChild(oldVersionSpecNode, newVersionSpecNode)
                                                 }
                                               }
                                             })
  }
}
