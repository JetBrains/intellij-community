// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.kotlin.idea.KotlinPluginUtil
import org.jetbrains.kotlin.idea.framework.JavaRuntimeDetectionUtil
import org.jetbrains.kotlin.idea.framework.JsLibraryStdDetectionUtil

data class VersionedLibrary(val library: Library, val version: String?, val usedInModules: Collection<Module>)

fun findOutdatedKotlinLibraries(project: Project): List<VersionedLibrary> {
    if (KotlinPluginUtil.isSnapshotVersion()) return emptyList() // plugin is run from sources, can't compare versions
    if (KotlinPluginUtil.isDevVersion()) return emptyList()
    if (project.isDisposed) return emptyList()

    val outdatedLibraries = arrayListOf<VersionedLibrary>()

    for ((library, modules) in findAllUsedLibraries(project).entrySet()) {
        getOutdatedRuntimeLibraryVersion(library, project)?.let { version ->
            outdatedLibraries.add(VersionedLibrary(library, version, modules))
        }
    }

    return outdatedLibraries
}

private fun getOutdatedRuntimeLibraryVersion(library: Library, project: Project): String? {
    val libraryVersion = getKotlinLibraryVersion(library, project) ?: return null
    val runtimeVersion = bundledRuntimeVersion()

    return if (isRuntimeOutdated(libraryVersion, runtimeVersion)) libraryVersion else null
}

private fun getKotlinLibraryVersion(library: Library, project: Project): String? =
    JavaRuntimeDetectionUtil.getJavaRuntimeVersion(library) ?: JsLibraryStdDetectionUtil.getJsLibraryStdVersion(library, project)

fun findKotlinRuntimeLibrary(module: Module, predicate: (Library, Project) -> Boolean = ::isKotlinRuntime): Library? {
    val orderEntries = ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>()
    return orderEntries.asSequence()
        .mapNotNull { it.library }
        .firstOrNull { predicate(it, module.project) }
}

private fun isKotlinRuntime(library: Library, project: Project) = isKotlinJavaRuntime(library) || isKotlinJsRuntime(library, project)

private fun isKotlinJavaRuntime(library: Library) =
    JavaRuntimeDetectionUtil.getRuntimeJar(library.getFiles(OrderRootType.CLASSES).asList()) != null

private fun isKotlinJsRuntime(library: Library, project: Project) =
    JsLibraryStdDetectionUtil.hasJsStdlibJar(library, project)

fun collectModulesWithOutdatedRuntime(libraries: List<VersionedLibrary>): List<Module> =
    libraries.flatMap { it.usedInModules }

fun isRuntimeOutdated(libraryVersion: String?, runtimeVersion: String): Boolean {
    return libraryVersion == null || libraryVersion.startsWith("internal-") != runtimeVersion.startsWith("internal-") ||
            VersionComparatorUtil.compare(runtimeVersion.substringBefore("-release-"), libraryVersion) > 0
}
