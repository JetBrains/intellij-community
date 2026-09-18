// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import org.jdom.Element
import org.jetbrains.idea.maven.importing.MavenImportUtil.findCompilerPlugin
import org.jetbrains.idea.maven.importing.MavenImportUtil.findToolchainPlugin
import org.jetbrains.idea.maven.importing.MavenImportUtil.isCompileExecution
import org.jetbrains.idea.maven.importing.MavenImportUtil.isTestCompileExecution
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.MavenProject


class ToolchainFinder {

  fun allToolchainRequirements(mavenProject: MavenProject): Set<ToolchainRequirement> {
    val compilerPlugin = mavenProject.findCompilerPlugin()
    val result = HashSet<ToolchainRequirement>()
    compilerPlugin?.executions
      ?.mapNotNull { getToolchain(it) }?.let { result.addAll(it) }
    fromToolchainPluginConfiguration(mavenProject)?.let { result.add(it) }
    fromToolchainSelectGoal(mavenProject)?.let { result.add(it) }
    return result
  }

  fun searchToolchainRequirementForExecution(
    mavenProject: MavenProject,
    executionId: String,
  ): ToolchainRequirement? {
    fromCompilePlugin(mavenProject) { it.executionId == executionId }?.let { return it }
    fromToolchainPluginConfiguration(mavenProject)?.let { return it }
    fromToolchainSelectGoal(mavenProject)?.let { return it }
    return null
  }

  fun searchToolchainRequirementForMain(
    mavenProject: MavenProject,
  ): ToolchainRequirement? {
    fromCompilePlugin(mavenProject) { isCompileExecution(it) }?.let { return it }
    fromToolchainPluginConfiguration(mavenProject)?.let { return it }
    fromToolchainSelectGoal(mavenProject)?.let { return it }
    return null
  }


  fun searchToolchainRequirementForTest(
    mavenProject: MavenProject,
  ): ToolchainRequirement? {
    fromCompilePlugin(mavenProject) { isTestCompileExecution(it) }?.let { return it }
    return searchToolchainRequirementForMain(mavenProject)
  }


  fun getToolchain(execution: MavenPlugin.Execution): ToolchainRequirement? {
    val jdkToolchain = execution.configurationElement?.getChild("jdkToolchain") ?: return null
    return fromToolchainConfig(jdkToolchain)
  }


  private fun fromCompilePlugin(mavenProject: MavenProject, predicate: (MavenPlugin.Execution) -> Boolean): ToolchainRequirement? {
    val compilerPlugin = mavenProject.findCompilerPlugin()
    return compilerPlugin?.executions
      ?.filter(predicate)
      ?.firstNotNullOfOrNull { getToolchain(it) }
  }

  private fun fromToolchainPluginConfiguration(mavenProject: MavenProject): ToolchainRequirement? {
    val toolchainPlugin = mavenProject.findToolchainPlugin() ?: return null
    if (toolchainPlugin.executions.none { it.goals.contains(TOOLCHAIN_GOAL) }) return null
    val toolchains = toolchainPlugin.configurationElement?.getChild("toolchains") ?: return null
    val jdkToolchain = toolchains.getChild("jdk") ?: return null
    return fromToolchainConfig(jdkToolchain)
  }

  private fun fromToolchainSelectGoal(mavenProject: MavenProject): ToolchainRequirement? {
    val toolchainPlugin = mavenProject.findToolchainPlugin() ?: return null
    val execution = toolchainPlugin.executions.firstOrNull {
      it.goals.contains(SELECT_JDK_TOOLCHAIN_GOAL)
    } ?: return null
    val builder = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .useImporterJdkIfMatches(canUseImporterJdkIfMatches(mavenProject, execution))
      .discoverJdks(canDiscoverJdks(mavenProject, execution))
    var hasRequirements = false
    for (parameter in SELECT_JDK_TOOLCHAIN_PARAMETERS) {
      val value = readParameter(mavenProject, execution, parameter.xmlName, parameter.propertyName)
      if (value != null) {
        builder.set(parameter.requirementName, value)
        hasRequirements = true
      }
    }
    if (!hasRequirements) return null
    return builder.build()
  }

  /** A blank configuration value counts as absent, so the documented user property still applies. */
  private fun readParameter(
    mavenProject: MavenProject,
    execution: MavenPlugin.Execution,
    xmlName: String,
    propertyName: String,
  ): String? {
    val configured = execution.configurationElement?.getChildTextTrim(xmlName)
    if (!configured.isNullOrBlank()) return configured
    return mavenProject.properties.getProperty(propertyName)?.trim()?.takeIf { it.isNotBlank() }
  }


  private fun fromToolchainConfig(config: Element): ToolchainRequirement {
    val builder = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
    config.children.forEach {
      builder.set(it.name, it.textTrim)
    }

    return builder.build()
  }

  private fun canUseImporterJdkIfMatches(mavenProject: MavenProject, execution: MavenPlugin.Execution): Boolean {
    val mode = readParameter(mavenProject, execution, USE_JDK_PARAMETER, TOOLCHAIN_JDK_MODE_PROPERTY)
    return mode == null || mode.equals(USE_JDK_IF_MATCH, ignoreCase = true)
  }

  private fun canDiscoverJdks(mavenProject: MavenProject, execution: MavenPlugin.Execution): Boolean {
    val discoverToolchains = readParameter(mavenProject, execution, DISCOVER_TOOLCHAINS_PARAMETER, TOOLCHAIN_JDK_DISCOVER_PROPERTY)
    return !discoverToolchains.equals("false", ignoreCase = true)
  }

  private data class SelectJdkToolchainParameter(
    val xmlName: String,
    val propertyName: String,
    val requirementName: String,
  )

  private companion object {
    const val TOOLCHAIN_GOAL = "toolchain"
    const val SELECT_JDK_TOOLCHAIN_GOAL = "select-jdk-toolchain"
    const val USE_JDK_PARAMETER = "useJdk"
    const val USE_JDK_IF_MATCH = "IfMatch"
    const val DISCOVER_TOOLCHAINS_PARAMETER = "discoverToolchains"
    const val TOOLCHAIN_JDK_MODE_PROPERTY = "toolchain.jdk.mode"
    const val TOOLCHAIN_JDK_DISCOVER_PROPERTY = "toolchain.jdk.discover"

    val SELECT_JDK_TOOLCHAIN_PARAMETERS = listOf(
      SelectJdkToolchainParameter("version", "toolchain.jdk.version", "version"),
      SelectJdkToolchainParameter("vendor", "toolchain.jdk.vendor", "vendor"),
      SelectJdkToolchainParameter("runtimeName", "toolchain.jdk.runtime.name", "runtime.name"),
      SelectJdkToolchainParameter("runtimeVersion", "toolchain.jdk.runtime.version", "runtime.version"),
      SelectJdkToolchainParameter("env", "toolchain.jdk.env", "env"),
    )
  }
}
