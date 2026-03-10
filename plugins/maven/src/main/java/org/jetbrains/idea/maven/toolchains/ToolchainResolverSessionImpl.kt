// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.buildtool.getToolchainsFile
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile


class ToolchainResolverSession private constructor(
  private val myProject: Project,
  private val myToolchainsFile: Path,
) {

  companion object {
    fun forSession(syncSession: MavenSyncSession): ToolchainResolverSession {
      val project = syncSession.project
      return syncSession.syncContext.getOrCreateUserData(TOOLCHAIN_SESSION_KEY) {
        ToolchainResolverSession(project,
                                 syncSession.getToolchainsFile())
      }
    }

    val TOOLCHAIN_SESSION_KEY: Key<ToolchainResolverSession> = Key<ToolchainResolverSession>.create("Sync.ToolchainResolverSession.cache")
  }

  private var cached: List<ToolchainModel>? = null

  private val resolvedSdks = HashMap<ToolchainRequirement, Sdk>()
  private val unresolvedSdks = HashSet<ToolchainRequirement>()

  fun unresolved(): List<ToolchainRequirement> = unresolvedSdks.toList()

  private suspend fun allToolchains(): List<ToolchainModel> {
    var result = cached
    if (result == null) {
      result = doReadToolchains()
      cached = result
    }
    return result
  }

  private suspend fun findToolchain(requirement: ToolchainRequirement): ToolchainModel? {
    val descriptors = allToolchains()
    return descriptors.firstOrNull { it.matches(requirement) }
  }

  suspend fun descriptorToSdk(descriptor: ToolchainModel?): Sdk? {
    if (descriptor == null) return null
    if (descriptor.type != "jdk") return null
    val jdkHome = descriptor.jdkHome ?: return null
    val projectJdkTable = ProjectJdkTable.getInstance()
    val sdkType = ExternalSystemJdkUtil.getJavaSdkType()
    return projectJdkTable.getSdksOfType(sdkType)
      .firstOrNull { it.homeDirectory?.toNioPath() == Path.of(jdkHome) }
  }

  suspend fun installSdkFromDescriptor(descriptor: ToolchainModel): Sdk? {
    if (descriptor.type != "jdk") return null
    val jdkHome = descriptor.jdkHome ?: return null
    val eelDescriptor = myProject.getEelDescriptor()
    val ideaPath = eelDescriptor.getPath(jdkHome).asNioPath()

    return withContext(Dispatchers.EDT) {
      SdkConfigurationUtil.createAndAddSDK(ideaPath.absolutePathString(), JavaSdk.getInstance())
    }
  }

  private suspend fun doReadToolchains(): List<ToolchainModel> {
    if (!myToolchainsFile.isRegularFile()) return emptyList()
    val toolchains = MavenJDOMUtil.read(myToolchainsFile, Charsets.UTF_8, null) ?: return emptyList()
    return toolchains.children.filter { it.name == "toolchain" }
      .mapNotNull { readToolchain(it) }

  }

  private fun readToolchain(element: Element): ToolchainModel? {
    val type = MavenJDOMUtil.findChildValueByPath(element, "type") ?: return null
    val provides = element.getChild("provides")?.children?.associate { it.name to it.textTrim } ?: emptyMap()
    val configuration = element.getChild("configuration")?.children?.associate { it.name to it.textTrim } ?: emptyMap()
    return ToolchainModel(type, provides, configuration)
  }


  suspend fun findOrInstallJdk(requirement: ToolchainRequirement?): Sdk? {
    if (requirement == null) return null
    resolvedSdks[requirement]?.let { return it }
    if (unresolvedSdks.contains(requirement)) return null

    val foundSdk = doFindOrInstall(requirement)

    if (foundSdk == null) {
      unresolvedSdks.add(requirement)
    }
    else {
      resolvedSdks[requirement] = foundSdk
    }
    return foundSdk;
  }

  private suspend fun doFindOrInstall(requirement: ToolchainRequirement): Sdk? {
    val descriptor = this.findToolchain(requirement)
    val foundSdk = if (descriptor != null) {
      descriptorToSdk(descriptor) ?: installSdkFromDescriptor(descriptor)
    }
    else {
      null
    }
    return foundSdk
  }
}
