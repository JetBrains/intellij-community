package com.intellij.tools.ide.performanceTesting.commands.dto

import java.io.Serializable


data class MavenArchetypeInfo(val groupId: String = "org.apache.maven.archetypes",
                              val artefactId: String = "maven-archetype-archetype",
                              val version: String = "1.0") : Serializable
