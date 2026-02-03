// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.jvmDecompiler

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.util.AbstractKotlinBundle

private const val BUNDLE = "messages.KotlinJvmDecompilerBundle"

internal object KotlinJvmDecompilerBundle : AbstractKotlinBundle(BUNDLE) {
    @Nls
    @JvmStatic
    fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return if (instance.containsKey(key)) getMessage(key, *params) else KotlinJvmDecompilerDeprecatedMessagesBundle.message(key)
    }
}