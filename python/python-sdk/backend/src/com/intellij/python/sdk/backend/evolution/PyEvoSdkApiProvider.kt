@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.backend.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoSdkDto
import com.intellij.python.sdk.common.evolution.PyEvoSdkApi
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import fleet.rpc.remoteApiDescriptor
import java.nio.file.Path

private val LOG = logger<PyEvoSdkApiProvider>()

internal class PyEvoSdkApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<PyEvoSdkApi>()) { PyEvoSdkApiImpl }
  }
}

private object PyEvoSdkApiImpl : PyEvoSdkApi {
  private val providers get() = EvoSelectSdkProvider.EP_NAME.extensionList

  override suspend fun getCurrentSdk(projectId: ProjectId, moduleName: String): EvoSdkDto? {
    val module = resolveModule(projectId, moduleName) ?: return null
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return null
    // Ask each tool provider to recognize the current interpreter; fall back to a generic presentation.
    val evoSdk = providers.firstNotNullOfOrNull { it.parseModuleSdk(module, sdk) }
                 ?: EvoSdk(icon = AllIcons.Nodes.Unknown, name = sdk.name, pythonBinaryPath = sdk.homePath?.let { Path.of(it) })
    val version = evoSdk.pythonBinaryPath?.getPythonVersion()
    return evoSdk.withVersion(version).toDto()
  }

  override suspend fun listNodes(projectId: ProjectId, moduleName: String): List<EvoNodeDto> {
    resolveModule(projectId, moduleName) ?: return emptyList()
    return providers.map { it.getNode() }
  }

  override suspend fun loadNode(projectId: ProjectId, moduleName: String, nodeId: String): EvoLoadResultDto {
    val module = resolveModule(projectId, moduleName)
                 ?: return EvoLoadResultDto.Error(PySdkBundle.message("evolution.error.module.not.found", moduleName))
    val provider = providers.firstOrNull { it.id == nodeId }
                   ?: return EvoLoadResultDto.Error(PySdkBundle.message("evolution.error.unknown.node", nodeId))
    return try {
      provider.loadSections(module)
    }
    catch (e: Exception) {
      LOG.warn("Failed to load Evo node '$nodeId' for module '$moduleName'", e)
      EvoLoadResultDto.Error(e.message ?: e.javaClass.simpleName)
    }
  }

  private fun resolveModule(projectId: ProjectId, moduleName: String): Module? {
    val project = projectId.findProjectOrNull() ?: return null
    return ModuleManager.getInstance(project).findModuleByName(moduleName)
  }
}
