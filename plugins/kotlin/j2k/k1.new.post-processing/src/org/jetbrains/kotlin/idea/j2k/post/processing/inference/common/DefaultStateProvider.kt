// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

abstract class DefaultStateProvider {
    abstract fun defaultStateFor(typeVariable: TypeVariable): State
}