// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.platform.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.tooling.IdePlatformKindTooling
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.framework.JavaRuntimeLibraryDescription
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor.Companion.getTestStateIcon
import org.jetbrains.kotlin.idea.platform.getGenericTestIcon
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.PathUtil
import javax.swing.Icon

class JvmIdePlatformKindTooling : IdePlatformKindTooling() {
    override val kind = JvmIdePlatformKind

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

    private fun calculateUrls(declaration: KtNamedDeclaration, includeSlowProviders: Boolean? = null): List<String>? {
        val testFramework =
            KotlinTestFramework.getApplicableFor(declaration, includeSlowProviders?.takeUnless { it } ) ?: return null

        val relevantProvider = includeSlowProviders == null || includeSlowProviders == testFramework.isSlow
        if (!relevantProvider) return null

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

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        val urls = calculateUrls(declaration, allowSlowOperations)

        if (urls != null) {
            return getTestStateIcon(urls, declaration)
        } else if (allowSlowOperations) {
            return getGenericTestIcon(declaration, { declaration.resolveToDescriptorIfAny() }) { emptyList() }
        }

        return null
    }

    override fun acceptsAsEntryPoint(function: KtFunction) = true
}