// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement

/**
 * Provides a custom navigation target for a declaration in a compiled Kotlin file.
 *
 * The Kotlin plugin calls each policy in extension order. It uses the first non-null result or
 * the standard navigation policy when all registered policies return null.
 *
 * The Kotlin plugin does not cache the result. A policy must cache an expensive calculation when necessary.
 * This behavior lets a policy use external state that can change without a PSI change, responsibility for invalidation is on the policy itself.
 *
 * Similar to [com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy] but for kotlin.
 */
interface KotlinClsCustomNavigationPolicy {
    /**
     * Returns a custom navigation target for [declaration].
     *
     * The declaration always belongs to a compiled Kotlin file.
     * Return null to let the next policy handle the declaration.
     */
    fun getNavigationElement(declaration: KtDeclaration): KtElement?

    companion object {
        private val EP_NAME = ExtensionPointName.create<KotlinClsCustomNavigationPolicy>("org.jetbrains.kotlin.clsCustomNavigationPolicy")

        fun getNavigationElement(declaration: KtDeclaration) : KtElement? = EP_NAME.computeSafeIfAny { it.getNavigationElement(declaration) }
    }
}
