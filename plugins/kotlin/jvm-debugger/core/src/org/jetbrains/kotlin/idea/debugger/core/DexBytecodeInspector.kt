// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import com.sun.jdi.Method
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinMethodSmartStepTarget
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.SmartStepIntoContext
import java.nio.file.Path

/**
 * A debugger extension that allows to implement dex specific
 * bytecode inspections in Android Studio.
 */
interface DexBytecodeInspector {
    fun hasOnlyInvokeStatic(method: Method): Boolean {
        return false
    }

    suspend fun filterAlreadyExecutedTargets(
        targets: List<KotlinMethodSmartStepTarget>,
        context: SmartStepIntoContext
    ): List<KotlinMethodSmartStepTarget> {
        return targets
    }

    companion object {
        internal val EP: ExtensionPointName<DexBytecodeInspector> =
            ExtensionPointName.create("com.intellij.debugger.dexBytecodeInspector")

        val DEX_FILES_KEY: Key<List<Path>> = Key("DEX_FILES_KEY")
    }
}
