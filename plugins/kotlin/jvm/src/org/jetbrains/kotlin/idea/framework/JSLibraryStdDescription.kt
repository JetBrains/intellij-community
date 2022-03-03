// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.configuration.KotlinJsModuleConfigurator

/**
 * @param project null when project doesn't exist yet (called from project wizard)
 */
class JSLibraryStdDescription(project: Project?) : CustomLibraryDescriptorWithDeferredConfig(
    project,
    KotlinJsModuleConfigurator.NAME,
    LIBRARY_NAME,
    JSLibraryKind,
    SUITABLE_LIBRARY_KINDS
) {

    @TestOnly
    fun createNewLibraryForTests(): NewLibraryConfiguration {
        return createConfigurationFromPluginPaths()
    }

    companion object {
        const val LIBRARY_NAME = "KotlinJavaScript"

        val JAVA_SCRIPT_LIBRARY_CREATION get() = KotlinJvmBundle.message("javascript.library.creation")
        val DIALOG_TITLE get() = KotlinJvmBundle.message("create.kotlin.javascript.library")
        val SUITABLE_LIBRARY_KINDS = setOf(JSLibraryKind)
    }
}
