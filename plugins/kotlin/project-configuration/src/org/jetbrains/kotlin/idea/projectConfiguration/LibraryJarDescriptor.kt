// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectConfiguration

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.utils.PathUtil

enum class LibraryJarDescriptor(val mavenArtifactId: String) {
    STDLIB_JAR(PathUtil.KOTLIN_JAVA_STDLIB_NAME),
    REFLECT_JAR(PathUtil.KOTLIN_JAVA_REFLECT_NAME),
    SCRIPT_RUNTIME_JAR(PathUtil.KOTLIN_JAVA_SCRIPT_RUNTIME_NAME),
    TEST_JAR(PathUtil.KOTLIN_TEST_NAME),
    RUNTIME_JDK8_JAR(PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME),
    JS_STDLIB_JAR(PathUtil.JS_LIB_NAME);

    fun findExistingJar(library: Library): VirtualFile? =
        library.getFiles(OrderRootType.CLASSES).firstOrNull { it.name.startsWith(mavenArtifactId) }

    val repositoryLibraryProperties: RepositoryLibraryProperties
        get() = RepositoryLibraryProperties(
            JpsMavenRepositoryLibraryDescriptor(
                KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
                mavenArtifactId,
                KotlinPluginLayout.standaloneCompilerVersion.artifactVersion,
                if (this == JS_STDLIB_JAR) "klib" else "jar",
                true,
                emptyList()
            )
        )
}