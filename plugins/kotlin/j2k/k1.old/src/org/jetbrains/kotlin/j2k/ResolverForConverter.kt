// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

object ResolverForConverter {
    fun resolveToDescriptor(declaration: KtDeclaration): DeclarationDescriptor? =
        declaration.resolveToDescriptorIfAny(BodyResolveMode.FULL)
}
