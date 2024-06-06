// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.openapi.extensions.ExtensionPointName
import com.sun.jdi.Method

/**
 * A debugger extension that allows to implement dex specific
 * bytecode inspections in Android Studio.
 */
interface DexBytecodeInspector {
    fun hasOnlyInvokeStatic(method: Method): Boolean {
        return false
    }

    companion object {
        @JvmStatic
        val EP: ExtensionPointName<DexBytecodeInspector> =
            ExtensionPointName.create("com.intellij.debugger.dexBytecodeInspector")
    }
}
