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

data class JpsResolverSettings(val sha256ChecksumsEnabled: Boolean, val bindRepositoryEnabled: Boolean)

fun readJpsResolverSettings(communityRoot: File, monorepoRoot: File?): JpsResolverSettings {
    // Checksums and bind repository must be set locally and committed by developer:
    // don't update them automatically on teamcity.
    if (System.getenv("TEAMCITY_VERSION") != null
        && !System.getenv("MODEL_UPDATER_ALLOW_UPDATING_SETTINGS_ON_TEAMCITY").toBoolean()
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