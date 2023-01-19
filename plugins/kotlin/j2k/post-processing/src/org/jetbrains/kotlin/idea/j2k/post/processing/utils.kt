// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor


internal fun KtExpression.unpackedReferenceToProperty(): KtProperty? =
    when (this) {
        is KtDotQualifiedExpression -> selectorExpression as? KtNameReferenceExpression
        is KtNameReferenceExpression -> this
        else -> null
    }?.references
        ?.firstOrNull { it is KtSimpleNameReference }
        ?.resolve() as? KtProperty


internal fun KtDeclaration.type() =
    (resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType

internal fun KtReferenceExpression.resolve() =
    mainReference.resolve()

internal fun KtPsiFactory.createGetter(body: KtExpression?, modifiers: String?): KtPropertyAccessor {
    val property =
        createProperty(
            "val x\n ${modifiers.orEmpty()} get" +
                    when (body) {
                        is KtBlockExpression -> "() { return 1 }"
                        null -> ""
                        else -> "() = 1"
                    } + "\n"

        )
    val getter = property.getter!!
    val bodyExpression = getter.bodyExpression

    bodyExpression?.replace(body!!)
    return getter
}

internal fun KtPsiFactory.createSetter(body: KtExpression?, fieldName: String?, modifiers: String?): KtPropertyAccessor {
    val modifiersText = modifiers.orEmpty()
    val property = when (body) {
        null -> createProperty("var x = 1\n  get() = 1\n $modifiersText set")
        is KtBlockExpression -> createProperty("var x get() = 1\n $modifiersText  set($fieldName) {\n field = $fieldName\n }")
        else -> createProperty("var x get() = 1\n $modifiersText set($fieldName) = TODO()")
    }
    val setter = property.setter!!
    if (body != null) {
        setter.bodyExpression?.replace(body)
    }
    return setter
}


internal fun KtElement.hasUsagesOutsideOf(inElement: KtElement, outsideElements: List<KtElement>): Boolean =
    ReferencesSearch.search(this, LocalSearchScope(inElement)).any { reference ->
        outsideElements.none { it.isAncestor(reference.element) }
    }

internal inline fun <reified T : PsiElement> List<PsiElement>.descendantsOfType(): List<T> =
    flatMap { it.collectDescendantsOfType() }

internal fun PsiElement.isInRange(outerRange: TextRange) =
    outerRange.contains(textRange)

internal fun runUndoTransparentActionInEdt(inWriteAction: Boolean, action: () -> Unit) {
    ApplicationManager.getApplication().invokeAndWait {
        CommandProcessor.getInstance().runUndoTransparentAction {
            if (inWriteAction) {
                runWriteAction(action)
            } else {
                action()
            }
        }
    }
}