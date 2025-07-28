// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import java.io.File

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
    
    println("Custom Kotlin bootstrap location: $bootstrapLocation; trying to copy artifacts to Maven repository")
    
    val bootstrapDir = File(bootstrapLocation).absoluteFile

    if (!bootstrapDir.isDirectory) {
        println("WARN: Bootstrap location does not exist or is not a directory, skipping copy: ${bootstrapDir}")
        return
    }
    
    val mavenRepository = File(System.getProperty("user.home")).resolve(".m2").resolve("repository").absoluteFile

    println("Copying bootstrap artifacts from $bootstrapLocation to $mavenRepository")

    bootstrapDir.copyRecursively(mavenRepository, overwrite = true)
    
    println("Kotlin bootstrap artifacts copy completed")
}