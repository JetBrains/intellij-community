// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.platform.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.framework.JavaRuntimeDetectionUtil
import org.jetbrains.kotlin.idea.framework.JavaRuntimeLibraryDescription
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor.Companion.getTestStateIcon
import org.jetbrains.kotlin.idea.platform.IdePlatformKindTooling
import org.jetbrains.kotlin.idea.platform.getGenericTestIcon
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.testIntegration.framework.*
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.PathUtil
import javax.swing.Icon

class JvmIdePlatformKindTooling : IdePlatformKindTooling() {
    override val kind = JvmIdePlatformKind

    override fun compilerArgumentsForProject(project: Project) = Kotlin2JvmCompilerArgumentsHolder.getInstance(project).settings

    override val mavenLibraryIds = listOf(
        PathUtil.KOTLIN_JAVA_STDLIB_NAME,
        PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_NAME,
        PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME,
        PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_NAME,
        PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME
    )

    override val gradlePluginId = "kotlin-platform-jvm"
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.JVM, KotlinPlatform.ANDROID)

    override val libraryKind: PersistentLibraryKind<*>? = null
    override fun getLibraryDescription(project: Project) = JavaRuntimeLibraryDescription(project)

    override fun getLibraryVersionProvider(project: Project): (Library) -> IdeKotlinVersion? {
        return JavaRuntimeDetectionUtil::getJavaRuntimeVersion
    }

    private fun calculateUrls(declaration: KtNamedDeclaration, includeSlowProviders: Boolean? = null): List<String>? {
        val testFramework = KotlinTestFramework.getApplicableFor(declaration) ?: return null

        val relevantProvider = includeSlowProviders == null || includeSlowProviders == testFramework.isSlow
        if (relevantProvider) return null

        val qualifiedName = testFramework.qualifiedName(declaration) ?: return null
        return when (declaration) {
            is KtClassOrObject -> listOf("java:suite://$qualifiedName")
            is KtNamedFunction -> listOf(
                "java:test://$qualifiedName/${declaration.name}",
                "java:test://$qualifiedName.${declaration.name}"
            )
            else -> null
        }
    }

    override fun getTestIcon(
        declaration: KtNamedDeclaration,
        descriptorProvider: () -> DeclarationDescriptor?,
        includeSlowProviders: Boolean?
    ): Icon? =
        calculateUrls(declaration, includeSlowProviders)?.let { getTestStateIcon(it, declaration) }
            ?: getGenericTestIcon(declaration, descriptorProvider) { emptyList() }

    override fun acceptsAsEntryPoint(function: KtFunction) = true
}