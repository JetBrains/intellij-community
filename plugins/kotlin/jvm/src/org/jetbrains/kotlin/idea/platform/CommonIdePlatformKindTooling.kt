// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.platform.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.platforms.KotlinCommonLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.tooling.IdePlatformKindTooling
import org.jetbrains.kotlin.idea.base.platforms.tooling.tooling
import org.jetbrains.kotlin.idea.framework.CommonStandardLibraryDescription
import org.jetbrains.kotlin.idea.platform.isCompatibleWith
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.isCommon
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

object CommonIdePlatformKindTooling : IdePlatformKindTooling() {
    const val MAVEN_COMMON_STDLIB_ID = "kotlin-stdlib-common" // TODO: KotlinCommonMavenConfigurator

    override val kind = CommonIdePlatformKind

    override val mavenLibraryIds = listOf(MAVEN_COMMON_STDLIB_ID)
    override val gradlePluginId = "kotlin-platform-common"
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.COMMON)

    override val libraryKind = KotlinCommonLibraryKind

    override fun getLibraryDescription(project: Project) = CommonStandardLibraryDescription(project)

    private fun getRelevantToolings(platform: TargetPlatform?): List<IdePlatformKindTooling> {
        return getInstances()
            .filter { it != this }
            .filter { platform == null || it.kind.isCompatibleWith(platform) }
    }

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        val icons = getRelevantToolings(declaration.module?.platform)
            .mapNotNull { it.getTestIcon(declaration, allowSlowOperations) }
            .distinct()

        return when (icons.size) {
            0 -> null
            else -> icons.singleOrNull() ?: AllIcons.RunConfigurations.TestState.Run
        }
    }

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        val module = function.containingKtFile.module ?: return false
        return module.implementingModules.any { implementingModule ->
            implementingModule.platform.idePlatformKind.takeIf { !it.isCommon }?.tooling?.acceptsAsEntryPoint(function) ?: false
        }
    }
}