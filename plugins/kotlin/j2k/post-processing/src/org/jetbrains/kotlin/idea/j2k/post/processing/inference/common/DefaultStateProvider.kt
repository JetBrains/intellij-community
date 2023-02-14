// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

abstract class DefaultStateProvider {
    abstract fun defaultStateFor(typeVariable: TypeVariable): State
}