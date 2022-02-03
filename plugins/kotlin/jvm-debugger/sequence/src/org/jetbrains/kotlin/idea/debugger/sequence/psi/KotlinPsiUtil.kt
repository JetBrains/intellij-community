// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.psi

import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.approximateFlexibleTypes
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.KotlinType

object KotlinPsiUtil {
    fun getTypeName(type: KotlinType): String {
        if (type is FlexibleType) {
            return DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(type.approximateFlexibleTypes())
        }

        return DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(type)
    }

    fun getTypeWithoutTypeParameters(type: KotlinType): String {
        val descriptor = type.constructor.declarationDescriptor ?: return getTypeName(type)
        return descriptor.fqNameSafe.asString()
    }
}

fun KtCallExpression.callName(): String = this.calleeExpression!!.text

// Used in JAICF plugin (https://plugins.jetbrains.com/plugin/17391-jaicf/)
@Deprecated(
    "Function is semantically incorrect as there might be both dispatch and extension receivers. Replace call with appropriate logic.",
    level = DeprecationLevel.ERROR
)
fun KtCallExpression.receiverValue(): ReceiverValue? {
    val resolvedCall = getResolvedCall(analyze(BodyResolveMode.PARTIAL)) ?: return null
    return resolvedCall.dispatchReceiver ?: resolvedCall.extensionReceiver
}

// Used in JAICF plugin (https://plugins.jetbrains.com/plugin/17391-jaicf/)
@Deprecated(
    "Function is semantically incorrect as there might be both dispatch and extension receivers. Replace call with appropriate logic.",
    level = DeprecationLevel.ERROR
)
fun KtCallExpression.receiverType(): KotlinType? {
    @Suppress("DEPRECATION_ERROR")
    return receiverValue()?.type
}

fun KtCallExpression.previousCall(): KtCallExpression? {
    val parent = this.parent as? KtDotQualifiedExpression ?: return null
    val receiverExpression = parent.receiverExpression
    if (receiverExpression is KtCallExpression) return receiverExpression
    if (receiverExpression is KtDotQualifiedExpression) return receiverExpression.selectorExpression as? KtCallExpression
    return null
}

@Deprecated(
    "Use org.jetbrains.kotlin.idea.core.analyze() instead.",
    ReplaceWith("resolveType()", "org.jetbrains.kotlin.idea.core.analyze"),
    level = DeprecationLevel.ERROR
)
fun KtExpression.resolveType(): KotlinType =
    this.analyze(BodyResolveMode.PARTIAL).getType(this)!!