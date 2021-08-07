// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.kotlin.idea.framework.JavaRuntimeDetectionUtil
import org.jetbrains.kotlin.idea.framework.JsLibraryStdDetectionUtil

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

fun isRuntimeOutdated(libraryVersion: String?, runtimeVersion: String): Boolean {
    return libraryVersion == null || libraryVersion.startsWith("internal-") != runtimeVersion.startsWith("internal-") ||
            VersionComparatorUtil.compare(runtimeVersion.substringBefore("-release-"), libraryVersion) > 0
}
