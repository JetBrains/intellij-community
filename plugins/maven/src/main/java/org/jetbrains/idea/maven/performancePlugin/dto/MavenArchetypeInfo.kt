package org.jetbrains.idea.maven.performancePlugin.dto


data class MavenArchetypeInfo(val groupId: String = "org.apache.maven.archetypes",
                              val artefactId: String = "maven-archetype-archetype",
                              val version: String = "1.0")
