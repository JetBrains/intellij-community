// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater.impl

import java.io.File

data class JpsRemoteRepository(val id: String, val url: String) {
  init {
    require(id.isNotBlank())

    require(url.isNotBlank())
    require(url.startsWith("https://"))
    require(!url.endsWith("/"))
  }
}

data class JpsResolverSettings(val sha256ChecksumsEnabled: Boolean, val bindRepositoryEnabled: Boolean) {
    companion object {
        /**
         * If true, then dependencies of [JpsLibrary.LibraryType.Repository] type should be registered 
         * as Maven dependencies, and should be resolved via Maven coordinates.
         * 
         * Otherwise, dependencies should be registered as simple paths to the jars. 
         * 
         * This can be desirable to avoid issues with the Maven dependency resolver being flaky.
         * 
         * In this case, all the other settings should be ignored.
         *
         * See IJI-1238 and KTI-2331 for details.
         */
        val useMavenResolver: Boolean
            get() = !System.getenv("MODEL_UPDATER_USE_KOTLIN_DEPENDENCIES_DIRECTLY_FROM_MAVEN_FOLDER").toBoolean()
    }
}

fun readJpsResolverSettings(communityRoot: File, monorepoRoot: File?): JpsResolverSettings {
    val isUnderTeamcity = System.getenv("TEAMCITY_VERSION") != null
    
    println("State of JpsResolverSettings.useMavenResolver == ${JpsResolverSettings.useMavenResolver}")

    // Checksums and bind repository must be set locally and committed by developer:
    // don't update them automatically on teamcity.
    if (
        isUnderTeamcity &&
        !System.getenv("MODEL_UPDATER_ALLOW_UPDATING_SETTINGS_ON_TEAMCITY").toBoolean()
    ) {
        val settings = JpsResolverSettings(false, false)
        println("Under TeamCity, resetting settings to: $settings")
        return settings
    }

    val effectiveRoot = monorepoRoot ?: communityRoot
    val resolverSettingsFile = effectiveRoot.resolve(".idea").resolve("dependencyResolver.xml")
    if (!resolverSettingsFile.isFile) return JpsResolverSettings(false, false)

    val settingsOptions = resolverSettingsFile.readXml().rootElement.getChildren("component")
        .single { it.getAttributeValue("name") == "MavenDependencyResolverConfiguration" }
        .getChildren("option")

    val sha256ChecksumsEnabled = settingsOptions
        .singleOrNull { it.getAttributeValue("name") == "VERIFY_SHA256_CHECKSUMS" }
        ?.getAttributeValue("value").toBoolean()

    val useBindRepositories = settingsOptions
        .singleOrNull { it.getAttributeValue("name") == "USE_BIND_REPOSITORY" }
        ?.getAttributeValue("value").toBoolean()

    return JpsResolverSettings(sha256ChecksumsEnabled, useBindRepositories)
}