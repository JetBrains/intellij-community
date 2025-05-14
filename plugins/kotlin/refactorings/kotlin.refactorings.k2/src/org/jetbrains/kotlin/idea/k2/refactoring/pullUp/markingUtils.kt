// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.types.Variance

private var KtElement.newFqName: FqName? by CopyablePsiUserDataProperty(Key.create("NEW_FQ_NAME"))
private var KtElement.newTypeTextByTargetClass: MutableMap<FqName, () -> String?>? by CopyablePsiUserDataProperty(Key.create("NEW_TYPE_TEXT_MAP"))

@OptIn(KaExperimentalApi::class)
internal fun KaSession.markElements(
    declaration: KtNamedDeclaration,
    sourceClass: KtClassOrObject,
    targetClass: KtClassOrObject,
    substitutor: KaSubstitutor,
): List<KtElement> {
    val affectedElements = ArrayList<KtElement>()

    declaration.accept(
        object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                element.allChildren.forEach { it.accept(this) }
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                val resolvedCall = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return
                val receiver = resolvedCall.partiallyAppliedSymbol.extensionReceiver
                    ?: resolvedCall.partiallyAppliedSymbol.dispatchReceiver
                    ?: return

                val implicitThis = receiver.type.expandedSymbol ?: return

                if (implicitThis.classKind == KaClassKind.COMPANION_OBJECT
                    && implicitThis.psi != null && sourceClass.isAncestor(implicitThis.psi!!, strict = true)
                ) {
                    val qualifierFqName = implicitThis.importableFqName ?: return

                    expression.newFqName = FqName("${qualifierFqName.asString()}.${expression.getReferencedName()}")
                    affectedElements.add(expression)
                }
            }

            override fun visitTypeReference(typeReference: KtTypeReference) {
                val oldType = typeReference.type
                val rendered = substitutor.substitute(oldType).render(position = Variance.INVARIANT)

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

internal fun applyMarking(
    declaration: KtNamedDeclaration,
    targetClass: KtClassOrObject,
) {
    val psiFactory = KtPsiFactory(declaration.project)

    declaration.accept(
        object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                for (it in element.allChildren.toList()) {
                    it.accept(this)
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
    }
}
