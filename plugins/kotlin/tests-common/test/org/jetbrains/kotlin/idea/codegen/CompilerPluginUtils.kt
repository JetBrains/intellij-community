// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codegen

import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.PathManager
import com.intellij.project.IntelliJProjectConfiguration
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import java.io.File

/**
 * The 'google()' maven repository as seen in
 * ```
 * repositories {
 *     google()
 * }
 * ```
 *
 * This repository can be used to download compose-related artifacts.
 *
 * Note: This repository is proxied by the JetBrains cache-redirector.
 */
val googleMavenRepository = RemoteRepositoryDescription(
    "google", "Google Maven Repository",
    "https://cache-redirector.jetbrains.com/dl.google.com.android.maven2"
)

/**
 * This function looks up the [compilerPlugin] from the IntelliJ dependencies provided by kotlinc.
 */
fun findCompilerPluginJars(compilerPlugin: String) =
    intelliJProjectConfiguration.libraryCollection.getLibrary(compilerPlugin).getFiles(JpsOrderRootType.COMPILED)
        .ifEmpty { error("Missing compose compiler plugin binaries") }.map(File::toPath)

private val intelliJProjectConfiguration by lazy {
    IntelliJProjectConfiguration.loadIntelliJProject(PathManager.getHomePath())
}

private fun JpsLibraryCollection.getLibrary(name: String): JpsLibrary {
    return findLibrary(name, JpsRepositoryLibraryType.INSTANCE)
        ?: getLibraries().firstOrNull { it.name.contains(name) }
        ?: error("Could not find '$name' library")
}