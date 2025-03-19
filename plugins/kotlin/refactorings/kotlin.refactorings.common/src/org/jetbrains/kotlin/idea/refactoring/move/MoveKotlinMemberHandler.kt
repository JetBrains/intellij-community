// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.moveMembers.MoveJavaMemberHandler
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference

class MoveKotlinMemberHandler : MoveJavaMemberHandler() {
    override fun getUsage(
      member: PsiMember,
      psiReference: PsiReference,
      membersToMove: MutableSet<PsiMember>,
      targetClass: PsiClass
    ): MoveMembersProcessor.MoveMembersUsageInfo? {
        if (psiReference is KtSimpleNameReference && psiReference.getImportAlias() != null) return null
        return super.getUsage(member, psiReference, membersToMove, targetClass)
    }

    override fun changeExternalUsage(options: MoveMembersOptions, usage: MoveMembersProcessor.MoveMembersUsageInfo): Boolean {
        val reference = usage.getReference()
        if (reference is KtSimpleNameReference && reference.getImportAlias() != null) return true
        return super.changeExternalUsage(options, usage)
    }
}