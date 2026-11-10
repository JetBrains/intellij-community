// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.getsOuterInstanceParameter
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclarationWithReturnType
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.isCompanion

/**
 * Reports callable references that "Move to companion" variations can break.
 *
 * Changes that happen with outer instance affect different callable references differently.
 * The function type of callable reference changes in these cases:
 *
 * - The unbound reference `A::member` loses the `A` parameter (from its type) when instance parameter is not added.
 * - The bound reference `a::member` gets the `A` parameter when the declaration gets the outer instance parameter.
 * - Inside the moved declaration, the reference `::member` can bind the implicit class instance.
 *   After the move, this reference loses the receiver.
 */
internal fun checkCallableReferenceConflictForCompanionTargets(
    declarationsToMove: Collection<KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>,
    target: K2MoveTargetDescriptor.Declaration<*>?
): MultiMap<PsiElement, String> {
    if (target !is K2MoveTargetDescriptor.CompanionObject &&
        target !is K2MoveTargetDescriptor.CompanionBlock &&
        target !is K2MoveTargetDescriptor.CompanionExtension
    ) return MultiMap.empty()

    val movedDeclarations = declarationsToMove.toSet()
    val externalReferences = usages.mapNotNull { usage ->
        val usageElement = usage.element ?: return@mapNotNull null
        (usageElement.parent as? KtCallableReferenceExpression)?.takeIf { it.callableReference == usageElement }
    }
    val internalReferences = declarationsToMove.flatMap { it.collectDescendantsOfType<KtCallableReferenceExpression>() }
    val outerInstanceParameterByDeclaration = mutableMapOf<KtNamedDeclaration, Boolean>()

    val conflicts = MultiMap<PsiElement, String>()
    for (reference in (externalReferences + internalReferences).distinct()) {
        val referencedDeclaration = reference.referencedDeclaration()
        val isBroken = if (referencedDeclaration != null && referencedDeclaration in movedDeclarations) {
            // reference to a moved declaration
            val declarationGetsClassInstanceParam = outerInstanceParameterByDeclaration.getOrPut(referencedDeclaration) {
                referencedDeclaration.getsOuterInstanceParameter()
            }
            val referenceHasClassInstanceInType = analyze(reference) {
                reference.containsClassInstanceInType(referencedDeclaration)
            } ?: continue
            referenceHasClassInstanceInType != declarationGetsClassInstanceParam
        } else {
            // reference to something that wasn't moved inside a moved declaration
            val classOfMovedDeclaration = movedDeclarations.firstOrNull { it.isAncestor(reference) }?.containingClassOrObject
            when {
                classOfMovedDeclaration !is KtClass -> false
                // refs to companion block/extension declarations stay resolvable after the caller is moved to the companion scope
                referencedDeclaration is KtDeclarationWithReturnType && referencedDeclaration.isCompanion -> false
                else -> analyze(reference) {
                    reference.bindsImplicitInstanceOf(classOfMovedDeclaration)
                }
            }
        }
        if (isBroken) {
            conflicts.putValue(reference, KotlinBundle.message("text.callable.reference.will.not.be.processed.0", reference.text))
        }
    }
    return conflicts
}

/**
 * Resolve reference target, taking class instead of constructor.
 */
private fun KtCallableReferenceExpression.referencedDeclaration(): KtNamedDeclaration? {
    val resolved = callableReference.mainReference.resolve()
    return (resolved as? KtConstructor<*>)?.getContainingClassOrObject() ?: resolved as? KtNamedDeclaration
}

/**
 * Returns whether the function type of this reference holds the instance of the class owning [referencedDeclaration].
 *
 * The logic is inverse to regular calls: if [referencedDeclaration] is a member declaration,
 * then callable refs with a dispatch receiver won't have the corresponding class in their type.
 *
 * Returns `null` for non-resolvable references.
 *
 * Example:
 * The type of `String::hashCode` is `(String) -> Int` — `String` is present in the type.
 * The type of `"asdf"::hashCode` is `() -> Int` — `String` is absent in the type, the `String` instance is already bound.
 */
context(_: KaSession)
private fun KtCallableReferenceExpression.containsClassInstanceInType(referencedDeclaration: KtNamedDeclaration): Boolean? {
    val call = resolveSuccessfulCall() ?: return null
    if (call.dispatchReceiver != null) return false
    if (referencedDeclaration.containingClassOrObject !is KtClass) return false
    val isMemberOrInnerClassReference = referencedDeclaration !is KtClassOrObject
            || (referencedDeclaration is KtClass && referencedDeclaration.isInner())
    return isMemberOrInnerClassReference
}

context(_: KaSession)
private fun KtCallableReferenceExpression.bindsImplicitInstanceOf(classOrObject: KtClassOrObject): Boolean {
    val call = resolveSuccessfulCall() ?: return false
    val classSymbol = classOrObject.classSymbol ?: return false
    return listOfNotNull(call.dispatchReceiver, call.extensionReceiver).any { receiver ->
        (receiver.unwrapSmartCasts() as? KaImplicitReceiverValue)?.symbol == classSymbol
    }
}
