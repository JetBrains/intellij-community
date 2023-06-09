// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.psi.*

class K2MoveRefactoringSupport : KotlinMoveRefactoringSupport {
    override fun isExtensionRef(expr: KtSimpleNameExpression): Boolean {
        TODO("Not yet implemented")
    }

    override fun isQualifiable(callableReferenceExpression: KtCallableReferenceExpression): Boolean {
        TODO("Not yet implemented")
    }

    override fun traverseOuterInstanceReferences(
        member: KtNamedDeclaration,
        stopAtFirst: Boolean,
        body: (OuterInstanceReferenceUsageInfo) -> Unit
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun addDelayedImportRequest(elementToImport: PsiElement, file: KtFile) {
        TODO("Not yet implemented")
    }

    override fun addDelayedShorteningRequest(element: KtElement) {
        TODO("Not yet implemented")
    }

    override fun processInternalReferencesToUpdateOnPackageNameChange(
        element: KtElement,
        containerChangeInfo: MoveContainerChangeInfo,
        body: (originalRefExpr: KtSimpleNameExpression, usageFactory: KotlinUsageInfoFactory) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun isValidTargetForImplicitCompanionAsDispatchReceiver(
        moveTarget: KotlinMoveTarget,
        companionObject: KtObjectDeclaration
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun renderType(ktObjectDeclaration: KtClassOrObject): String {
        TODO("Not yet implemented")
    }
}