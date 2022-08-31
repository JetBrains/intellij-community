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

package com.jetbrains.packagesearch.intellij.plugin.gradle.tooling

import com.jetbrains.packagesearch.intellij.plugin.gradle.tooling.GradleConfigurationReportModelImpl.ConfigurationImpl
import com.jetbrains.packagesearch.intellij.plugin.gradle.tooling.GradleConfigurationReportModelImpl.DependencyImpl
import org.gradle.api.Project
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.io.Serializable as JavaSerializable

sealed interface GradleConfigurationReportModel : JavaSerializable {

    companion object {

        internal operator fun invoke(
            projectDir: String,
            configurations: List<Configuration>
        ): GradleConfigurationReportModel = GradleConfigurationReportModelImpl(projectDir, configurations)
    }

    val configurations: List<Configuration>
    val projectDir: String

    sealed interface Configuration : JavaSerializable {

        val name: String
        val dependencies: List<Dependency>

        companion object {

            internal operator fun invoke(
                name: String,
                dependencies: List<Dependency>
            ): Configuration = ConfigurationImpl(name, dependencies)
        }
    }

    sealed interface Dependency : JavaSerializable {

        val groupId: String
        val artifactId: String
        val version: String

        companion object {

            internal operator fun invoke(
                groupId: String,
                artifactId: String,
                version: String
            ): Dependency = DependencyImpl(groupId, artifactId, version)
        }
    }
}

internal data class GradleConfigurationReportModelImpl(
    override val projectDir: String,
    override val configurations: List<GradleConfigurationReportModel.Configuration>
) : GradleConfigurationReportModel {

    internal data class ConfigurationImpl(
        override val name: String,
        override val dependencies: List<GradleConfigurationReportModel.Dependency>
    ) : GradleConfigurationReportModel.Configuration

    internal data class DependencyImpl(
        override val groupId: String,
        override val artifactId: String,
        override val version: String
    ) : GradleConfigurationReportModel.Dependency
}

class GradleConfigurationModelBuilder : AbstractModelBuilderService() {

    override fun canBuild(modelName: String?): Boolean =
        modelName == GradleConfigurationReportModel::class.java.name

    override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): GradleConfigurationReportModel =
        GradleConfigurationReportModel(
            project.projectDir.absolutePath,
            project.configurations.map {
                GradleConfigurationReportModel.Configuration(
                    it.name,
                    it.dependencies.mapNotNull { dependency ->
                        project.projectDir
                        GradleConfigurationReportModel.Dependency(
                            groupId = dependency.group ?: return@mapNotNull null,
                            artifactId = dependency.name,
                            version = dependency.version ?: return@mapNotNull null
                        )
                    }
                )
            })

    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder =
        ErrorMessageBuilder
            .create(project, e, "Gradle import errors")
            .withDescription("Unable to import resolved versions from configurations in project ''${project.name}'' for the Dependency toolwindow.")
}