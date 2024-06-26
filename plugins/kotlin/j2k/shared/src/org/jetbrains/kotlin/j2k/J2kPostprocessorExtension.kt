// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile
import com.intellij.openapi.application.writeAction

/**
 * The `org.jetbrains.kotlin.j2kPostprocessorExtension` extension point enables running custom postprocessing steps on Java files before they
 * are converted to Kotlin. At runtime, all registered extensions are collected and executed sequentially. To implement your own
 * postprocessor in a separate plugin, simply extend this interface and register the extension point in your plugin's xml file, e.g.
 * ```
 * <extensions defaultExtensionNs="org.jetbrains.kotlin">
 *   <j2kPostprocessorExtension implementation="org.jetbrains.kotlin.j2k.FooPostprocessorExtension"/>
 * </extensions>
 * ```
 *
 * All postprocessors are run on a background thread using coroutines, write actions must be wrapped in `j2kWriteAction { ... }` so that
 * they are executed on the EDT thread with the write lock. Read actions must be wrapped in `runReadAction { ... }`, and analysis must be
 * done outside write actions.
 */
interface J2kPostprocessorExtension {
    /**
     * Override this method to analyze and edit Java files before conversion. This method is always executed on a background thread, so
     * write actions must be wrapped in `j2kWriteAction { ... }`. Read actions must be wrapped in `readAction { ... }`, and analysis must
     * be done outside write actions.
     */
    suspend fun processFiles(project: Project, files: List<KtFile>)

    companion object {
        val EP_NAME = ExtensionPointName<J2kPostprocessorExtension>("org.jetbrains.kotlin.j2kPostprocessorExtension")

        suspend fun <T> j2kWriteAction(action: () -> T) {
            writeAction {
                CommandProcessor.getInstance().runUndoTransparentAction {
                    action()
                }
            }
        }
    }
}