// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinProjectConfigurationUtils")

package org.jetbrains.kotlin.idea.projectConfiguration

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.ui.Messages
import org.eclipse.aether.version.Version
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptStdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.platforms.KotlinJvmStdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.util.findLibrary
import org.jetbrains.kotlin.idea.base.util.updateEx
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.impl.*
import org.jetbrains.kotlin.utils.PathUtil

fun getLibraryDescription(project: Project, platformKind: IdePlatformKind): CustomLibraryDescription? {
    return when (platformKind) {
        CommonIdePlatformKind -> CommonStandardLibraryDescription(project)
        JvmIdePlatformKind -> JavaRuntimeLibraryDescription(project)
        JsIdePlatformKind -> JSLibraryStdDescription(project)
        WasmJsIdePlatformKind -> null
        WasmWasiIdePlatformKind -> null
        NativeIdePlatformKind -> null
        else -> throw IllegalArgumentException("Unsupported platform kind: $platformKind")
    }
}

fun getJvmStdlibArtifactId(sdk: Sdk?, version: IdeKotlinVersion): String {
    if (!hasJreSpecificRuntime(version)) {
        return PathUtil.KOTLIN_JAVA_STDLIB_NAME
    }

    val jdkVersion = sdk?.let { JavaSdk.getInstance().getVersion(it) }
        ?: return PathUtil.KOTLIN_JAVA_STDLIB_NAME

    if (hasJdkLikeUpdatedRuntime(version)) {
        return when {
            jdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8) -> PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME
            jdkVersion == JavaSdkVersion.JDK_1_7 -> PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME
            else -> PathUtil.KOTLIN_JAVA_STDLIB_NAME
        }
    }

    return when {
        jdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8) -> PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_NAME
        jdkVersion == JavaSdkVersion.JDK_1_7 -> PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_NAME
        else -> PathUtil.KOTLIN_JAVA_STDLIB_NAME
    }
}

fun getDefaultJvmTarget(sdk: Sdk?, version: IdeKotlinVersion): JvmTarget? {
    if (!hasJreSpecificRuntime(version)) {
        return null
    }

    val jdkVersion = sdk?.let { JavaSdk.getInstance().getVersion(it) }
        ?: return null

    return when {
        jdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8) -> JvmTarget.JVM_1_8
        jdkVersion.isAtLeast(JavaSdkVersion.JDK_1_6) -> JvmTarget.JVM_1_6
        else -> null
    }
}

@ApiStatus.Internal
fun hasJdkLikeUpdatedRuntime(version: IdeKotlinVersion): Boolean {
    return version.compare("1.2.0-rc-39") >= 0 || version.isSnapshot
}

@ApiStatus.Internal
fun hasJreSpecificRuntime(version: IdeKotlinVersion): Boolean {
    return version.compare("1.1.0") >= 0 || version.isSnapshot
}

fun findKotlinRuntimeLibrary(module: Module): Library? {
    val project = module.project
    return module.findLibrary { library ->
        KotlinJvmStdlibDetectorFacility.isStdlib(project, library)
                || KotlinJavaScriptStdlibDetectorFacility.isStdlib(project, library)
    }
}

fun checkUpdateRuntime(project: Project, requiredVersion: ApiVersion): Boolean {
    val modulesWithOutdatedRuntime = project.modules.filter { module ->
        val parsedModuleRuntimeVersion = getRuntimeLibraryVersion(module)?.apiVersion
        parsedModuleRuntimeVersion != null && parsedModuleRuntimeVersion < requiredVersion
    }

    if (modulesWithOutdatedRuntime.isNotEmpty()) {
        val librariesToUpdate = modulesWithOutdatedRuntime.mapNotNull { findKotlinRuntimeLibrary(it) }
        if (!askUpdateRuntime(project, requiredVersion, librariesToUpdate)) {
            return false
        }
    }

    return true
}

fun askUpdateRuntime(project: Project, requiredVersion: ApiVersion, librariesToUpdate: List<Library>): Boolean {
    if (!isUnitTestMode()) {
        val rc = Messages.showOkCancelDialog(
            project,
            KotlinProjectConfigurationBundle.message(
                "this.language.feature.requires.version.0.or.later.of.the.kotlin.runtime.library.would.you.like.to.update.the.runtime.library.in.your.project",
                requiredVersion
            ),
            KotlinProjectConfigurationBundle.message("update.runtime.library"),
            Messages.getQuestionIcon()
        )
        if (rc != Messages.OK) return false
    }

    val upToMavenVersion = requiredVersion.toMavenArtifactVersion(project) ?: run {
        Messages.showErrorDialog(
            KotlinProjectConfigurationBundle.message("cant.fetch.available.maven.versions"),
            KotlinProjectConfigurationBundle.message("cant.fetch.available.maven.versions.title")
        )
        return false
    }
    updateLibraries(project, upToMavenVersion, librariesToUpdate)
    return true
}

private fun ApiVersion.toMavenArtifactVersion(project: Project): String? {
    val apiVersion = this
    var mavenVersion: String? = null
    object : Task.Modal(project, KotlinProjectConfigurationBundle.message("fetching.available.maven.versions.title"), true) {
        override fun run(indicator: ProgressIndicator) {
            val repositoryLibraryProperties = LibraryJarDescriptor.RUNTIME_JDK8_JAR.repositoryLibraryProperties
            val version: Version? = ArtifactRepositoryManager(
                JarRepositoryManager.getJPSLocalMavenRepositoryForIdeaProject(project).toFile()
            ).getAvailableVersions(
                repositoryLibraryProperties.groupId,
                repositoryLibraryProperties.artifactId,
                "[${apiVersion.versionString},)",
                ArtifactKind.ARTIFACT
            ).firstOrNull()
            mavenVersion = version?.toString()
        }
    }.queue()
    return mavenVersion
}

fun askUpdateRuntime(module: Module, requiredVersion: ApiVersion): Boolean {
    val library = findKotlinRuntimeLibrary(module) ?: return true
    return askUpdateRuntime(module.project, requiredVersion, listOf(library))
}

fun updateLibraries(project: Project, upToMavenVersion: String, libraries: Collection<Library>) {
    if (project.modules.any { module -> module.buildSystemType != BuildSystemType.JPS }) {
        val message =
            KotlinProjectConfigurationBundle.message("automatic.library.version.update.for.maven.and.gradle.projects.is.currently.unsupported.please.update.your.build.scripts.manually")
        val title = KotlinProjectConfigurationBundle.message("update.kotlin.runtime.library")
        Messages.showMessageDialog(project, message, title, Messages.getErrorIcon())
        return
    }

    val librariesToUpdate = libraries
        .asSequence()
        .mapNotNull { it as? LibraryEx }
        .mapNotNull { library ->
            val properties = library.properties as? RepositoryLibraryProperties ?: return@mapNotNull null
            library to properties
        }
        .filter { (_, properties) -> properties.groupId == KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID }

    for ((library, properties) in librariesToUpdate) {
        library.updateEx { modifiableModel ->
            modifiableModel.properties = RepositoryLibraryProperties(properties.groupId, properties.mavenId, upToMavenVersion)
            for (orderRootType in listOf(OrderRootType.SOURCES, OrderRootType.CLASSES, OrderRootType.DOCUMENTATION)) {
                modifiableModel.getUrls(orderRootType).forEach {
                    modifiableModel.removeRoot(it, orderRootType)
                }
            }

            JarRepositoryManager.loadDependenciesModal(project, properties, true, true, null, null).forEach {
                modifiableModel.addRoot(it.file, it.type)
            }
        }
    }
}