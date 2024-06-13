// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile

/**
 * The `org.jetbrains.kotlin.j2kPreprocessorExtension` extension point enables running custom preprocessing steps on Java files before they
 * are converted to Kotlin. At runtime, all registered extensions are collected and executed sequentially. To implement your own
 * preprocessor in a separate plugin, simply extend this interface and register the extension point in your plugin's xml file, e.g.
 * ```
 * <extensions defaultExtensionNs="org.jetbrains.kotlin">
 *   <j2kPreprocessorExtension implementation="org.jetbrains.kotlin.j2k.FooPreprocessorExtension"/>
 * </extensions>
 * ```
 *
 * All preprocessors are run on a background thread, write actions must be wrapped in
 * `runUndoTransparentActionInEdt(inWriteAction = true) { ... }` so that they are executed on the EDT thread. As usual, read actions must
 * be wrapped in `runReadAction { ... }`, and analysis must be done outside write actions.
 */
interface J2kPreprocessorExtension {

    /**
     * Override this method to analyze and edit Java files before conversion. This method is always executed on a background thread, so
     * write actions must be wrapped in `runUndoTransparentActionInEdt(inWriteAction = true) { ... }`. As usual, read actions must be
     * wrapped in `runReadAction { ... }`, and analysis must be done outside write actions.
     */
    fun processFiles(project: Project, files: List<PsiJavaFile>)

    companion object {
        val EP_NAME = ExtensionPointName<J2kPreprocessorExtension>("org.jetbrains.kotlin.j2kPreprocessorExtension")
    }
}