// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compose.k2.debugger.test

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

private val intelliJProjectConfiguration by lazy {
    IntelliJProjectConfiguration.loadIntelliJProject(PathManager.getHomePath())
}

/**
 * We're looking up the 'compose compiler' from the IntelliJ dependencies provided by kotlinc
 */
internal val composeCompilerJars: List<Path> by lazy {
    getLibrary("kotlinc.compose-compiler-plugin").getFiles(JpsOrderRootType.COMPILED)
        .ifEmpty { error("Missing compose compiler plugin binaries") }
        .map(File::toPath)
}

private fun getLibrary(name: String): JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> {
    return intelliJProjectConfiguration.libraryCollection.getLibrary(name)
}

private fun JpsLibraryCollection.getLibrary(name: String): JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> {
    return findLibrary(name, JpsRepositoryLibraryType.INSTANCE)
        ?: error("Could not find '$name' library")
}