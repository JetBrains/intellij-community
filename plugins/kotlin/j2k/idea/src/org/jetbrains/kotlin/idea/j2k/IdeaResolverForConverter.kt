// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k

import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.j2k.ResolverForConverter
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

object IdeaResolverForConverter : ResolverForConverter {
    override fun resolveToDescriptor(declaration: KtDeclaration) = declaration.resolveToDescriptorIfAny(BodyResolveMode.FULL)
}
