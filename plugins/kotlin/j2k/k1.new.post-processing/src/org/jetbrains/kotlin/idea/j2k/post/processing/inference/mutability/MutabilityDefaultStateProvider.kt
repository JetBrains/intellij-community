// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.DefaultStateProvider
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.TypeVariable

@K1Deprecation
class MutabilityDefaultStateProvider : DefaultStateProvider() {
    override fun defaultStateFor(typeVariable: TypeVariable): State =
        State.UPPER
}