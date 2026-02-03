// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate.classLoading

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.org.objectweb.asm.tree.MethodNode

const val GENERATED_FUNCTION_NAME = "generated_for_debugger_fun"
const val GENERATED_CLASS_NAME = "Generated_for_debugger_class"

@Suppress("ArrayInDataClass")
data class ClassToLoad(val className: String, val relativeFileName: String, val bytes: ByteArray) {
    val isMainClass: Boolean
        get() = className == GENERATED_CLASS_NAME
}

@ApiStatus.Internal
fun isEvaluationEntryPoint(methodName: String): Boolean {
    /*
        Short of inspecting the metadata, there are no indications in the bytecode of what precisely is the entrypoint
        to the compiled fragment. It's either:

        - named precisely GENERATED_FUNCTION_NAME
        - named GENERATED_FUNCTION_NAME-abcdefg, as a result of inline class mangling
        if the fragment captures a value of inline class type.

        and should not be confused with

        - GENERATED_FUNCTION_NAME$lambda-nn, introduced by SAM conversion
        - GENERATED_FUNCTION_NAME$foo, introduced by local functions in the fragment
    */

    return methodName == GENERATED_FUNCTION_NAME || methodName.startsWith("$GENERATED_FUNCTION_NAME-")
}

val MethodNode.isEvaluationEntryPoint: Boolean
    @ApiStatus.Internal get() = name != null && isEvaluationEntryPoint(name)