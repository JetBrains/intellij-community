// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractClass

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.checkVisibilityInAbstractedMembers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperConflictSearcher
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

internal class K2ExtractSuperConflictSearcher : KotlinExtractSuperConflictSearcher {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun collectConflicts(
        originalClass: KtClassOrObject,
        memberInfos: List<KotlinMemberInfo>,
        targetParent: PsiElement,
        newClassName: String,
        isExtractInterface: Boolean
    ): MultiMap<PsiElement, String> {
        val conflicts = MultiMap<PsiElement, String>()
        val project = originalClass.project

        ActionUtil.underModalProgress(
            project,
            RefactoringBundle.message("detecting.possible.conflicts"),
        ) {
            if (targetParent is KtElement) {
                val targetSibling = originalClass.parentsWithSelf.first { it.parent == targetParent } as KtElement

                analyze(originalClass) {
                    targetSibling.containingKtFile
                        .scopeContext(targetSibling)
                        .compositeScope()
                        .classifiers { it.identifier == newClassName }
                        .firstNotNullOfOrNull { it.psi }
                        ?.let {
                            conflicts.putValue(
                                it,
                                KotlinBundle.message(
                                    "text.class.0.already.exists.in.the.target.scope",
                                    newClassName,
                                )
                            )
                        }
                }

                // TODO: Improve conflict checking.
                // See: org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2ExtractionTestGenerated.ExtractSuperclass.testPrivateClass
                // This case should report conflicts but currently passes without showing any.

                if (targetParent is PsiDirectory) {
                    ExtractSuperClassUtil.checkSuperAccessible(targetParent, conflicts, originalClass.toLightClass())
                }

                analyze(originalClass) {
                    checkVisibilityInAbstractedMembers(memberInfos, conflicts)
                }
            }
        }

        return conflicts
    }
}
