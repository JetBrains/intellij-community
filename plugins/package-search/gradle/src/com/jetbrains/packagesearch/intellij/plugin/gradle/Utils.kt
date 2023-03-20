/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.gradle.tooling.GradleConfigurationReportModel
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gradle.util.GradleConstants

internal fun StringBuilder.appendEscapedToRegexp(text: String) =
    StringUtil.escapeToRegexp(text, this)

val BuildSystemType.Companion.GRADLE_GROOVY
    get() = BuildSystemType(name = "GRADLE", language = "groovy", dependencyAnalyzerKey = GradleConstants.SYSTEM_ID)

val BuildSystemType.Companion.GRADLE_KOTLIN
    get() = BuildSystemType(name = "GRADLE", language = "kotlin", dependencyAnalyzerKey = GradleConstants.SYSTEM_ID)

// for gradle modules which only contain another gradle modules (in other words,for modules without its own build file)
val BuildSystemType.Companion.GRADLE_CONTAINER
    get() = BuildSystemType(name = "GRADLE", language = "any", dependencyAnalyzerKey = GradleConstants.SYSTEM_ID)

internal fun GradleConfigurationReportModel.toPublic() = PublicGradleConfigurationReportModel(
    projectDir,
    configurations.map {
        PublicGradleConfigurationReportModel.Configuration(
            it.name,
            it.dependencies.map { PublicGradleConfigurationReportModel.Dependency(it.groupId, it.artifactId, it.version) }
        )
    }
)

@Serializable
data class PublicGradleConfigurationReportModel(
    val projectDir: String,
    val configurations: List<Configuration>
) {

    @Serializable
    data class Configuration(
        val name: String,
        val dependencies: List<Dependency>
    )

    @Serializable
    data class Dependency(
        val groupId: String,
        val artifactId: String,
        val version: String
    )
}