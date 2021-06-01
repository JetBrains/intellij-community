// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate.classLoading

const val GENERATED_FUNCTION_NAME = "generated_for_debugger_fun"
const val GENERATED_CLASS_NAME = "Generated_for_debugger_class"

@Suppress("ArrayInDataClass")
data class ClassToLoad(val className: String, val relativeFileName: String, val bytes: ByteArray) {
    val isMainClass: Boolean
        get() = className == GENERATED_CLASS_NAME
}