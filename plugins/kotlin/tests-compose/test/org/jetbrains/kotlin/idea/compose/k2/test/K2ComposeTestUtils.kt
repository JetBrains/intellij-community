// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compose.k2.test

import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.PathManager
import com.intellij.project.IntelliJProjectConfiguration
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.library.JpsLibraryCollection
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.library.JpsTypedLibrary
import java.io.File
import java.nio.file.Path

/**
 * We're looking up the 'compose compiler' from the IntelliJ dependencies provided by kotlinc
 */
val composeCompilerJars: List<Path> by lazy {
    intelliJProjectConfiguration.libraryCollection.getLibrary("kotlinc.compose-compiler-plugin").getFiles(JpsOrderRootType.COMPILED)
        .ifEmpty { error("Missing compose compiler plugin binaries") }
        .map(File::toPath)
}

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

private val intelliJProjectConfiguration by lazy {
    IntelliJProjectConfiguration.loadIntelliJProject(PathManager.getHomePath())
}

private fun JpsLibraryCollection.getLibrary(name: String): JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> {
    return findLibrary(name, JpsRepositoryLibraryType.INSTANCE)
        ?: error("Could not find '$name' library")
}
