// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newBooleanMetric
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.statistics.ExternalSystemUsagesCollector
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import org.jetbrains.idea.maven.execution.MavenExternalParameters.resolveMavenHome
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenSettingsCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "build.maven.state"

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptySet()

    val usages = mutableSetOf<MetricEvent>()

    // to have a total users base line to calculate pertentages of settings
    usages.add(newBooleanMetric("hasMavenProject", true))

    // Main page
    val generalSettings = manager.generalSettings
    usages.add(newMetric("checksumPolicy", generalSettings.checksumPolicy))
    usages.add(newMetric("failureBehavior", generalSettings.failureBehavior))
    usages.add(newBooleanMetric("alwaysUpdateSnapshots", generalSettings.isAlwaysUpdateSnapshots))
    usages.add(newBooleanMetric("nonRecursive", generalSettings.isNonRecursive))
    usages.add(newBooleanMetric("printErrorStackTraces", generalSettings.isPrintErrorStackTraces))
    usages.add(newBooleanMetric("usePluginRegistry", generalSettings.isUsePluginRegistry))
    usages.add(newBooleanMetric("workOffline", generalSettings.isWorkOffline))
    usages.add(newMetric("outputLevel", generalSettings.outputLevel))
    usages.add(newMetric("pluginUpdatePolicy", generalSettings.pluginUpdatePolicy))
    @Suppress("DEPRECATION")
    usages.add(newMetric("loggingLevel", generalSettings.loggingLevel))
    try {
      var mavenVersion = MavenUtil.getMavenVersion(resolveMavenHome(generalSettings, project, null))
      mavenVersion = mavenVersion?.let { Version.parseVersion(it)?.toCompactString() } ?: "unknown"
      usages.add(newMetric("mavenVersion", mavenVersion))
    }
    catch (ignore: Exception) {
      // ignore invalid maven home configuration
    }
    usages.add(newBooleanMetric("localRepository", generalSettings.localRepository.isNotBlank()))
    usages.add(newBooleanMetric("userSettingsFile", generalSettings.userSettingsFile.isNotBlank()))

    // Importing page
    val importingSettings = manager.importingSettings
    usages.add(newBooleanMetric("lookForNested", importingSettings.isLookForNested))

    usages.add(newBooleanMetric("dedicatedModuleDir", importingSettings.dedicatedModuleDir.isNotBlank()))
    usages.add(newBooleanMetric("storeProjectFilesExternally", ExternalStorageConfigurationManager.getInstance(project).isEnabled))
    usages.add(newBooleanMetric("importAutomatically", importingSettings.isImportAutomatically))
    usages.add(newBooleanMetric("autoDetectCompiler", importingSettings.isAutoDetectCompiler))
    usages.add(newBooleanMetric("createModulesForAggregators", importingSettings.isCreateModulesForAggregators))
    usages.add(newBooleanMetric("createModuleGroups", importingSettings.isCreateModuleGroups))
    usages.add(newBooleanMetric("keepSourceFolders", importingSettings.isKeepSourceFolders))
    usages.add(newBooleanMetric("excludeTargetFolder", importingSettings.isExcludeTargetFolder))
    usages.add(newBooleanMetric("useMavenOutput", importingSettings.isUseMavenOutput))

    usages.add(newMetric("generatedSourcesFolder", importingSettings.generatedSourcesFolder))
    usages.add(newMetric("updateFoldersOnImportPhase", importingSettings.updateFoldersOnImportPhase))

    usages.add(newBooleanMetric("downloadDocsAutomatically", importingSettings.isDownloadDocsAutomatically))
    usages.add(newBooleanMetric("downloadSourcesAutomatically", importingSettings.isDownloadSourcesAutomatically))
    usages.add(newBooleanMetric("customDependencyTypes",
                               MavenImportingSettings.DEFAULT_DEPENDENCY_TYPES != importingSettings.dependencyTypes))

    usages.add(ExternalSystemUsagesCollector.getJRETypeUsage("jdkTypeForImporter", importingSettings.jdkForImporter))
    usages.add(ExternalSystemUsagesCollector.getJREVersionUsage(project, "jdkVersionForImporter", importingSettings.jdkForImporter))
    usages.add(newBooleanMetric("hasVmOptionsForImporter", importingSettings.vmOptionsForImporter.isNotBlank()))

    // Ignored Files page
    usages.add(newBooleanMetric("hasIgnoredFiles", manager.ignoredFilesPaths.isNotEmpty()))
    usages.add(newBooleanMetric("hasIgnoredPatterns", manager.ignoredFilesPatterns.isNotEmpty()))

    // Runner page
    val runnerSettings = MavenRunner.getInstance(project).settings
    usages.add(newBooleanMetric("delegateBuildRun", runnerSettings.isDelegateBuildToMaven))
    usages.add(newBooleanMetric("runMavenInBackground", runnerSettings.isRunMavenInBackground))
    usages.add(ExternalSystemUsagesCollector.getJRETypeUsage("runnerJreType", runnerSettings.jreName))
    usages.add(ExternalSystemUsagesCollector.getJREVersionUsage(project, "runnerJreVersion", runnerSettings.jreName))
    usages.add(newBooleanMetric("hasRunnerVmOptions", runnerSettings.vmOptions.isNotBlank()))
    usages.add(newBooleanMetric("hasRunnerEnvVariables", !runnerSettings.environmentProperties.isNullOrEmpty()))
    usages.add(newBooleanMetric("passParentEnv", runnerSettings.isPassParentEnv))
    usages.add(newBooleanMetric("skipTests", runnerSettings.isSkipTests))
    usages.add(newBooleanMetric("hasRunnerMavenProperties", !runnerSettings.mavenProperties.isNullOrEmpty()))
    return usages
  }
}
