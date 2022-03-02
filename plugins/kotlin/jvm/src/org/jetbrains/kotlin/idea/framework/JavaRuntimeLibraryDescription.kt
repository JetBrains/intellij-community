// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.LibraryKind
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator
import org.jetbrains.kotlin.idea.versions.getDefaultJvmTarget

/**
 * @param project null when project doesn't exist yet (called from project wizard)
 */
class JavaRuntimeLibraryDescription(project: Project?) : CustomLibraryDescriptorWithDeferredConfig(
    project,
    KotlinJavaModuleConfigurator.NAME,
    LIBRARY_NAME,
    KOTLIN_JAVA_RUNTIME_KIND,
    SUITABLE_LIBRARY_KINDS
) {

    override fun configureKotlinSettings(project: Project, sdk: Sdk?) {
        val defaultJvmTarget = getDefaultJvmTarget(sdk, KotlinPluginLayout.instance.ideCompilerVersion)
        if (defaultJvmTarget != null) {
            Kotlin2JvmCompilerArgumentsHolder.getInstance(project).update {
                jvmTarget = defaultJvmTarget.description
            }
        }
    }

    companion object {
        val KOTLIN_JAVA_RUNTIME_KIND: LibraryKind = LibraryKind.create("kotlin-java-runtime")
        const val LIBRARY_NAME = "KotlinJavaRuntime"

        val JAVA_RUNTIME_LIBRARY_CREATION
            @Nls
            get() = KotlinJvmBundle.message("java.runtime.library.creation")
        val DIALOG_TITLE
            @Nls
            get() = KotlinJvmBundle.message("create.kotlin.java.runtime.library")
        val SUITABLE_LIBRARY_KINDS: Set<LibraryKind> = setOf(KOTLIN_JAVA_RUNTIME_KIND)
    }
}
