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
    val toolchains = toolchainPlugin.configurationElement?.getChild("toolchains") ?: return null
    val jdkToolchain = toolchains.getChild("jdk") ?: return null
    return fromToolchainConfig(jdkToolchain)
  }

  private fun fromToolchainSelectGoal(mavenProject: MavenProject): ToolchainRequirement? {
    val toolchainPlugin = mavenProject.findToolchainPlugin() ?: return null
    val execution = toolchainPlugin.executions.firstOrNull {
      it.goals.contains("select-jdk-toolchain")
    } ?: return null
    val config = execution.configurationElement ?: return null
    return fromToolchainConfig(config)
  }


  private fun fromToolchainConfig(config: Element): ToolchainRequirement {
    val builder = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
    config.children.forEach {
      builder.set(it.name, it.textTrim)
    }

    return builder.build()
  }
}

