// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands.dto

import java.io.Serializable


data class MavenArchetypeInfo(val groupId: String = "org.apache.maven.archetypes",
                              val artefactId: String = "maven-archetype-archetype",
                              val version: String = "1.0") : Serializable
