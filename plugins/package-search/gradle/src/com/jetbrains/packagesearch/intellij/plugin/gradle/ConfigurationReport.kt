package com.jetbrains.packagesearch.intellij.plugin.gradle

import kotlinx.serialization.Serializable

@Serializable
data class ConfigurationReport(
    val configurationName: String,
    val dependencies: List<Dependency>
) {

    @Serializable
    data class Dependency(val groupId: String, val artifactId: String, val version: String)
}