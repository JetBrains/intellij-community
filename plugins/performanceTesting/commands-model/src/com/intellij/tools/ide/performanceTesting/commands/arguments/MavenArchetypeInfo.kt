package com.intellij.tools.ide.performanceTesting.commands.arguments

data class MavenArchetypeInfo(val groupId: String = "org.apache.maven.archetypes",
                              val artefactId: String = "maven-archetype-archetype",
                              val version: String = "1.0")
