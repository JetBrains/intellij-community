// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases

import com.intellij.jarRepository.RemoteRepositoryDescription
import org.jetbrains.kotlin.idea.codegen.findCompilerPluginJars
import org.jetbrains.kotlin.idea.codegen.googleMavenRepository
import java.nio.file.Path

// Downloading the compose runtime requires also specifying the Google repository.
internal fun jarRepositoriesForCompose(): List<RemoteRepositoryDescription> = listOf(
    RemoteRepositoryDescription.MAVEN_CENTRAL, googleMavenRepository
)

/**
 * We're looking up the 'compose compiler' from the IntelliJ dependencies provided by kotlinc
 */
internal val composeCompilerJars: List<Path> by lazy {
    findCompilerPluginJars("kotlinc.compose-compiler-plugin")
}