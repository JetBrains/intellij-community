// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectConfiguration

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.configuration.KotlinJsModuleConfigurator

/**
 * @param project null when project doesn't exist yet (called from project wizard)
 */
class JSLibraryStdDescription(project: Project?) : CustomLibraryDescriptionWithDeferredConfig(
    project,
    KotlinJsModuleConfigurator.NAME,
    LIBRARY_NAME,
    KotlinJavaScriptLibraryKind,
    SUITABLE_LIBRARY_KINDS
) {

    @TestOnly
    fun createNewLibraryForTests(): NewLibraryConfiguration {
        return createConfigurationFromPluginPaths()
    }

    companion object {
        const val LIBRARY_NAME: String = "KotlinJavaScript"

        val JAVA_SCRIPT_LIBRARY_CREATION: String get() = KotlinProjectConfigurationBundle.message("javascript.library.creation")
        val DIALOG_TITLE: String get() = KotlinProjectConfigurationBundle.message("create.kotlin.javascript.library")
        val SUITABLE_LIBRARY_KINDS: Set<KotlinJavaScriptLibraryKind> = setOf(KotlinJavaScriptLibraryKind)
    }
}