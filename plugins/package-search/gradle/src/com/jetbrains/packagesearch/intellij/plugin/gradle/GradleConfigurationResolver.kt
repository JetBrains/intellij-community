package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.util.io.createDirectories
import com.intellij.util.io.exists
import com.intellij.util.io.readBytes
import com.jetbrains.packagesearch.intellij.plugin.gradle.GradleConfigurationReportNodeProcessor.Companion.ESM_REPORTS_KEY
import com.jetbrains.packagesearch.intellij.plugin.gradle.tooling.GradleConfigurationModelBuilder
import com.jetbrains.packagesearch.intellij.plugin.gradle.tooling.GradleConfigurationReportModel
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeBytes

class GradleConfigurationResolver : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses(): Set<Class<*>> =
        setOf(GradleConfigurationReportModel::class.java)

    override fun getToolingExtensionsClasses(): Set<Class<*>> =
        setOf(GradleConfigurationModelBuilder::class.java)

    private inline fun <reified T> IdeaModule.getExtraProject(): T? =
        resolverCtx.getExtraProject(this@getExtraProject, T::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        gradleModule.getExtraProject<GradleConfigurationReportModel>()
            ?.also { ideModule.createChild(ESM_REPORTS_KEY, it.toPublic()) }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}

class GradleConfigurationReportNodeProcessor : AbstractProjectDataService<PublicGradleConfigurationReportModel, Unit>() {

    companion object {

        internal val ESM_REPORTS_KEY: Key<PublicGradleConfigurationReportModel> =
            Key.create(PublicGradleConfigurationReportModel::class.java, 100)
    }

    @Service(Level.PROJECT)
    class Cache(private val project: Project) : Disposable {

        private val cacheFile
            get() = project.getProjectDataPath("pkgs")
                .also { if (!it.exists()) it.createDirectories() }
                .resolve("gradle.proto.bin")

        var state = load()
            internal set

        private fun load(): Map<String, PublicGradleConfigurationReportModel> =
            cacheFile.takeIf { it.exists() }
                ?.runCatching { ProtoBuf.decodeFromByteArray<Map<String, PublicGradleConfigurationReportModel>>(readBytes()) }
                ?.onFailure { logDebug(this::class.qualifiedName+"#load()", it) { "Error while decoding ${cacheFile.absolutePathString()}" } }
                ?.getOrNull()
                ?.let { emptyMap() }
                ?: emptyMap()

        override fun dispose() {
            cacheFile.writeBytes(ProtoBuf.encodeToByteArray(state))
        }
    }

    override fun getTargetDataKey() = ESM_REPORTS_KEY

    override fun importData(
      toImport: Collection<DataNode<PublicGradleConfigurationReportModel>>,
      projectData: ProjectData?,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider
    ) {
        project.service<Cache>().state = toImport.associate { it.data.projectDir to it.data }
        super.importData(toImport, projectData, project, modelsProvider)
    }
}

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

private fun GradleConfigurationReportModel.toPublic() =
    PublicGradleConfigurationReportModel(projectDir, configurations.toPublic())

@JvmName("toPublicGradleConfigurationReportModelConfiguration")
private fun List<GradleConfigurationReportModel.Configuration>.toPublic() =
    map { PublicGradleConfigurationReportModel.Configuration(it.name, it.dependencies.toPublic()) }

@JvmName("toPublicGradleConfigurationReportModelDependency")
private fun List<GradleConfigurationReportModel.Dependency>.toPublic() =
    map { PublicGradleConfigurationReportModel.Dependency(it.groupId, it.artifactId, it.version) }