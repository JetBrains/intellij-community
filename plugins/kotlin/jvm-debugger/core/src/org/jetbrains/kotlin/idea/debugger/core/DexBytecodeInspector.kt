package org.jetbrains.kotlin.idea.debugger.core

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
