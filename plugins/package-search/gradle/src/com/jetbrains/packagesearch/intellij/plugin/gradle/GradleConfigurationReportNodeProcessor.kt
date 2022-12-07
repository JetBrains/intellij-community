package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.util.io.createDirectories
import com.intellij.util.io.exists
import com.intellij.util.io.readBytes
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeBytes

class GradleConfigurationReportNodeProcessor : AbstractProjectDataService<PublicGradleConfigurationReportModel, Unit>() {

    companion object {

        internal val ESM_REPORTS_KEY: Key<PublicGradleConfigurationReportModel> =
          Key.create(PublicGradleConfigurationReportModel::class.java, 100)
    }

    @Service(Service.Level.PROJECT)
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
                ?.onFailure {
                  logDebug(this::class.qualifiedName + "#load()", it) { "Error while decoding ${cacheFile.absolutePathString()}" }
                }
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