// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.util.io.write
import com.intellij.util.lang.JavaVersion
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.toolchains.MavenDomToolchain
import org.jetbrains.idea.maven.dom.toolchains.MavenDomToolchainsModel
import java.nio.file.Path
import kotlin.io.path.isRegularFile

class ToolchainModel(
  val type: String,
  providesMap: Map<String, String>,
  configurationMap: Map<String, String>,
) {
  fun matches(requirement: ToolchainRequirement): Boolean {
    if (type != requirement.type) return false
    for (req in requirement.params) {
      val matcher = findMatcher(req.key)
      if (!matcher.matches(req.key, req.value, this)) return false
    }
    return true
  }

  val provides: Map<String, String> = HashMap(providesMap)
  val configuration: Map<String, String> = HashMap(configurationMap)

  companion object {
    fun fromSdk(sdk: Sdk): ToolchainModel? {
      if (sdk.sdkType !is JavaSdkType) return null
      val version = sdk.versionString?.let { JavaVersion.parse(it) }?.toFeatureString() ?: return null
      val path = sdk.homePath?.asTargetPath() ?: return null
      return ToolchainModel(
        type = "jdk",
        providesMap = mapOf("version" to version),
        configurationMap = mapOf("jdkHome" to path)
      )
    }
  }
}

/**
 * adds sdk to toolhains file and returns added xml element
 */
suspend fun addIntoToolchainsFile(project: Project, toolchains: Path, sdk: Sdk): MavenDomToolchain? {
  ensureFileValid(toolchains)
  val vFile = VfsUtil.findFile(toolchains, true) ?: return  null
  val model = readAction {
    MavenDomUtil.getMavenDomModel(project, vFile, MavenDomToolchainsModel::class.java)
  } ?: return null

  val existing = readAction {
    model.toolchains.firstOrNull() { it.configuration.jdkHome.stringValue == Path.of(sdk.homePath).asEelPath().toString() }
  }
  if (existing != null) return existing
  val toolchainModel = ToolchainModel.fromSdk(sdk) ?: return null
  return writeCommandAction(project, MavenDomBundle.message("maven.toolchain.add.command.name")) {
    with(model.addToolchain()) {
      this.type.stringValue = toolchainModel.type
      this.provides.version.stringValue = toolchainModel.provides["version"]
      this.configuration.jdkHome.stringValue = toolchainModel.jdkHome
      ensureTagExists()
      this
    }
  }
}

private fun ensureFileValid(toolchains: Path) {
  if (!toolchains.isRegularFile()) {
    toolchains.write("<toolchains>\n</toolchains>".toByteArray())
  }
}


private fun String?.asTargetPath(): String? {
  return this?.let { Path.of(it).asEelPath().toString() }
}


val ToolchainModel.jdkHome: String?
  get() {
    return this.configuration["jdkHome"]
  }