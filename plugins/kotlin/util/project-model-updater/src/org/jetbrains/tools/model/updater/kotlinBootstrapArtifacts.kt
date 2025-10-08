// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.isDirectory

/**
 * We have to do the manual copying of Kotlin bootstrap artifacts from the 
 * custom directory to the local Maven repository.
 * 
 * This has to be done on TeamCity agents to prevent Disk Cleaners
 * from cleaning the local Maven repository too early.
 * 
 * For the details, see KTI-2331.
 */
internal fun copyBootstrapArtifactsToMavenRepositoryIfExists() {
    val bootstrapLocation = System.getenv("MODEL_UPDATER_KOTLIN_BOOTSTRAP_LOCATION")
    
    if (bootstrapLocation == null) {
        println("Custom Kotlin bootstrap location is not specified, skipping")
        return
    }
    
    println("Custom Kotlin bootstrap location: $bootstrapLocation; trying to copy artifacts to Maven repository...")
    
    val bootstrapDir = Path(bootstrapLocation).toAbsolutePath().normalize()

    if (!bootstrapDir.isDirectory()) {
        System.err.println("Bootstrap location does not exist or is not a directory, skipping copy: ${bootstrapDir}")
        return
    }

    val mavenRepository = Path(System.getProperty("user.home"))
        .resolve(".m2")
        .resolve("repository")
        .toAbsolutePath()
        .normalize()

    println("Copying bootstrap artifacts from $bootstrapLocation to $mavenRepository...")

    @OptIn(ExperimentalPathApi::class)
    bootstrapDir.copyToRecursively(mavenRepository, followLinks = false, overwrite = true)
    
    println("Kotlin bootstrap artifacts copy completed")
}