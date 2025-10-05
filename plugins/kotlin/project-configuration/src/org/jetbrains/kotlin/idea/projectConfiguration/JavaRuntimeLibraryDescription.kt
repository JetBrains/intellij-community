// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectConfiguration

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.LibraryKind
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator

/**
 * @param project null when project doesn't exist yet (called from project wizard)
 */
class JavaRuntimeLibraryDescription(project: Project?) : CustomLibraryDescriptionWithDeferredConfig(
    project,
    KotlinJavaModuleConfigurator.NAME,
    LIBRARY_NAME,
    KOTLIN_JAVA_RUNTIME_KIND,
    SUITABLE_LIBRARY_KINDS
) {
    override fun configureKotlinSettings(project: Project, sdk: Sdk?) {
        val defaultJvmTarget = getDefaultJvmTarget(sdk, KotlinPluginLayout.ideCompilerVersion)
        if (defaultJvmTarget != null) {
            val kotlin2JvmCompilerArgumentsHolder = Kotlin2JvmCompilerArgumentsHolder.getInstance(project)
            if (kotlin2JvmCompilerArgumentsHolder.settings.jvmTarget == null) {
                kotlin2JvmCompilerArgumentsHolder.update {
                    jvmTarget = defaultJvmTarget.description
                }
            }
        }
    }

    companion object {
        val KOTLIN_JAVA_RUNTIME_KIND: LibraryKind = LibraryKind.create("kotlin-java-runtime")
        const val LIBRARY_NAME: String = "KotlinJavaRuntime"

        val JAVA_RUNTIME_LIBRARY_CREATION: String
            @Nls
            get() = KotlinProjectConfigurationBundle.message("java.runtime.library.creation")
        val DIALOG_TITLE: String
            @Nls
            get() = KotlinProjectConfigurationBundle.message("create.kotlin.java.runtime.library")
        val SUITABLE_LIBRARY_KINDS: Set<LibraryKind> = setOf(KOTLIN_JAVA_RUNTIME_KIND)
    }
}