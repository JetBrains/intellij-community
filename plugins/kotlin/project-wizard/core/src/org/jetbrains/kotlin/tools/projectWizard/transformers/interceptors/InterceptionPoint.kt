// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.transformers.interceptors

import org.jetbrains.annotations.NonNls

data class InterceptionPoint<out T: Any>(@NonNls val name: String, val initialValue: T)

data class InterceptionPointModifier<out T : Any>(
    val point: InterceptionPoint<@UnsafeVariance T>,
    val modifier: (@UnsafeVariance T) -> T
)