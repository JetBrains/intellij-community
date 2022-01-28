// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.platform.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.framework.JavaRuntimeDetectionUtil
import org.jetbrains.kotlin.idea.framework.JavaRuntimeLibraryDescription
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor.Companion.getTestStateIcon
import org.jetbrains.kotlin.idea.platform.IdePlatformKindTooling
import org.jetbrains.kotlin.idea.platform.getGenericTestIcon
import org.jetbrains.kotlin.idea.platform.testintegration.LightTestFramework
import org.jetbrains.kotlin.idea.platform.testintegration.NoLightTestFrameworkResult
import org.jetbrains.kotlin.idea.platform.testintegration.ResolvedLightTestFrameworkResult
import org.jetbrains.kotlin.idea.platform.testintegration.UnsureLightTestFrameworkResult
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
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

    override fun getLibraryVersionProvider(project: Project): (Library) -> String? {
        return JavaRuntimeDetectionUtil::getJavaRuntimeVersion
    }

    private fun calculateUrls(declaration: KtNamedDeclaration): List<String>? {
        for (lightTestFramework in LightTestFramework.EXTENSION_NAME.extensionList) {
            when (lightTestFramework.detectFramework(declaration)) {
                is UnsureLightTestFrameworkResult -> continue
                is NoLightTestFrameworkResult -> return null
                is ResolvedLightTestFrameworkResult -> Unit
            }

            val qualifiedName = lightTestFramework.qualifiedName(declaration) ?: return null
            return when (declaration) {
                is KtClassOrObject -> listOf("java:suite://$qualifiedName")
                is KtNamedFunction -> listOf(
                    "java:test://$qualifiedName/${declaration.name}",
                    "java:test://$qualifiedName.${declaration.name}"
                )
                else -> null
            }
        }
        return null
    }

    override fun getTestIcon(declaration: KtNamedDeclaration, descriptorProvider: () -> DeclarationDescriptor?): Icon? =
        calculateUrls(declaration)?.let { getTestStateIcon(it, declaration) }
            ?: getGenericTestIcon(declaration, descriptorProvider) { emptyList() }

    override fun acceptsAsEntryPoint(function: KtFunction) = true
}