// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.importableFqName
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenOptionsForIde
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionCall
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.CopyablePsiUserDataProperty
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiverOrThis
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.types.Variance

private var KtElement.newFqName: FqName? by CopyablePsiUserDataProperty(Key.create("NEW_FQ_NAME"))
private var KtElement.replaceWithTargetThis: Boolean? by CopyablePsiUserDataProperty(Key.create("REPLACE_WITH_TARGET_THIS"))
private var KtElement.newTypeTextByTargetClass: MutableMap<FqName, () -> String?>? by CopyablePsiUserDataProperty(Key.create("NEW_TYPE_TEXT_MAP"))

context(session: KaSession)
internal fun markElements(
    declaration: KtElement,
    sourceClass: KtClassOrObject,
    targetClass: KtClassOrObject,
    substitutor: KaSubstitutor,
): List<KtElement> {
    val affectedElements = ArrayList<KtElement>()

    declaration.accept(
        object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement): Unit = element.allChildren.forEach { it.accept(this) }
            override fun visitThisExpression(expression: KtThisExpression): Unit = visitSuperOrThis(expression)
            override fun visitSuperExpression(expression: KtSuperExpression): Unit = visitSuperOrThis(expression)

            private fun visitSuperOrThis(expression: KtInstanceExpressionWithLabel) {
                val callee = expression.getQualifiedExpressionForReceiver()?.selectorExpression?.getCalleeExpressionIfAny() ?: return
                val calleeTarget = callee.resolveSuccessfulExpressionSymbol() ?: return
                if (calleeTarget.containingDeclaration == targetClass.symbol) {
                    expression.replaceWithTargetThis = true
                    affectedElements.add(expression)
                }
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (declaration is KtClassOrObject &&
                    expression.resolveSuccessfulExpressionSymbol() == sourceClass.symbol &&
                    expression.getQualifiedExpressionForReceiver() != null
                ) {
                    expression.newFqName = targetClass.fqName ?: return
                    affectedElements.add(expression)
                    return
                }

                val resolvedCall = expression.resolveSuccessfulExpressionCall()?.simple ?: return
                val receiverValue = resolvedCall.extensionReceiver ?: resolvedCall.dispatchReceiver ?: return

                val implicitThis = receiverValue.type.expandedSymbol ?: return
                val implicitThisElement = implicitThis.psi ?: return

                if (
                    implicitThis.classKind == KaClassKind.COMPANION_OBJECT &&
                    sourceClass.isAncestor(implicitThisElement, strict = true) &&
                    !declaration.isAncestor(implicitThisElement, strict = false)
                ) {
                    val qualifierFqName = implicitThis.importableFqName ?: return

                    expression.newFqName = FqName("${qualifierFqName.asString()}.${expression.getReferencedName()}")
                    affectedElements.add(expression)
                }
            }

            override fun visitTypeReference(typeReference: KtTypeReference) {
                val oldType = typeReference.type
                val substituted = substitutor.substitute(oldType)
                if (substituted.semanticallyEquals(oldType)) return
                val rendered = substituted.render(position = Variance.INVARIANT)

                val targetFqName = targetClass.fqName ?: return
                val map = typeReference.newTypeTextByTargetClass ?: mutableMapOf<FqName, () -> String?>().also {
                    typeReference.newTypeTextByTargetClass = it
                }

                map[targetFqName] = { rendered }
                affectedElements.add(typeReference)
            }
        }
    )

    return affectedElements
}

private var KtElement.originalClassifier: PsiElement? by CopyablePsiUserDataProperty(Key.create("ORIGINAL_CLASSIFIER"))

context(_: KaSession)
internal fun markTypeReferences(declaration: KtElement): List<KtElement> {
    val marked = ArrayList<KtElement>()

    declaration.forEachDescendantOfType<KtTypeReference> { typeReference ->
        val type = typeReference.type
        val classifier = (type.abbreviation?.symbol ?: type.expandedSymbol)?.psi ?: return@forEachDescendantOfType
        if (declaration.isAncestor(classifier, strict = false)) return@forEachDescendantOfType
        typeReference.originalClassifier = classifier
        marked += typeReference
    }

    return marked
}

internal fun rebindTypeReferences(
    movedDeclaration: KtElement,
    movedDeclarations: Collection<PsiElement>,
) {
    for (typeReference in movedDeclaration.collectDescendantsOfType<KtTypeReference>()) {
        val classifier = typeReference.originalClassifier ?: continue
        typeReference.originalClassifier = null
        if (!typeReference.isValid || classifier in movedDeclarations) continue

        val reference = (typeReference.typeElement?.unwrapNullability() as? KtUserType)
            ?.referenceExpression?.mainReference ?: continue
        reference.bindToElement(classifier, KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING)
    }
}

internal fun applyMarking(
    declaration: KtElement,
    targetClass: KtClassOrObject,
) {
    val psiFactory = KtPsiFactory(declaration.project)
    val targetThis = psiFactory.createExpression("this@${targetClass.fqName?.quoteIfNeeded() ?: return}")

    declaration.accept(
        object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement): Unit = element.allChildren.toList().forEach { it.accept(this) }
            override fun visitThisExpression(expression: KtThisExpression): Unit = this.visitSuperOrThis(expression)
            override fun visitSuperExpression(expression: KtSuperExpression): Unit = this.visitSuperOrThis(expression)

            private fun visitSuperOrThis(expression: KtInstanceExpressionWithLabel) {
                expression.replaceWithTargetThis?.let {
                    expression.replaceWithTargetThis = null

                    val newThisExpression = expression.replace(targetThis) as KtExpression
                    shortenReferences(newThisExpression.getQualifiedExpressionForReceiverOrThis(), ShortenOptionsForIde.ALL_ENABLED)
                }
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                expression.newFqName?.let {
                    expression.newFqName = null

                    shortenReferences(expression.mainReference.bindToFqName(it) as KtElement)
                }
            }

            override fun visitTypeReference(typeReference: KtTypeReference) {
                val newTypeTextFn = typeReference.newTypeTextByTargetClass?.remove(targetClass.fqName) ?: return
                val newTypeText = newTypeTextFn() ?: return

                shortenReferences(typeReference.replace(psiFactory.createType(newTypeText)) as KtElement)
            }
        }
    )
}

internal fun clearMarking(markedElements: List<KtElement>) {
    markedElements.forEach {
        it.newFqName = null
        it.newTypeTextByTargetClass?.clear()
        it.newTypeTextByTargetClass = null
        it.replaceWithTargetThis = null
        it.originalClassifier = null
    }
}