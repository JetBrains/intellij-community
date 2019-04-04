// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getEnumUsage
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

  override fun getUsages(project: Project): Set<UsageDescriptor> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptySet()

    val usages = mutableSetOf<UsageDescriptor>()

    // to have a total users base line to calculate pertentages of settings
    usages.add(getBooleanUsage("hasMavenProject", true))

    // Main page
    val generalSettings = manager.generalSettings
    usages.add(getEnumUsage("checksumPolicy", generalSettings.checksumPolicy))
    usages.add(getEnumUsage("failureBehavior", generalSettings.failureBehavior))
    usages.add(getBooleanUsage("alwaysUpdateSnapshots", generalSettings.isAlwaysUpdateSnapshots))
    usages.add(getBooleanUsage("nonRecursive", generalSettings.isNonRecursive))
    usages.add(getBooleanUsage("printErrorStackTraces", generalSettings.isPrintErrorStackTraces))
    usages.add(getBooleanUsage("usePluginRegistry", generalSettings.isUsePluginRegistry))
    usages.add(getBooleanUsage("workOffline", generalSettings.isWorkOffline))
    usages.add(getEnumUsage("outputLevel", generalSettings.outputLevel))
    usages.add(getEnumUsage("pluginUpdatePolicy", generalSettings.pluginUpdatePolicy))
    usages.add(getEnumUsage("loggingLevel", generalSettings.loggingLevel))
    try {
      var mavenVersion = MavenUtil.getMavenVersion(resolveMavenHome(generalSettings, project, null))
      mavenVersion = mavenVersion?.let { Version.parseVersion(it)?.toCompactString() } ?: "unknown"
      usages.add(UsageDescriptor("mavenVersion.$mavenVersion"))
    }
    catch (ignore: Exception) {
      // ignore invalid maven home configuration
    }
    usages.add(getBooleanUsage("localRepository", generalSettings.localRepository.isNotBlank()))
    usages.add(getBooleanUsage("userSettingsFile", generalSettings.userSettingsFile.isNotBlank()))

    // Importing page
    val importingSettings = manager.importingSettings
    usages.add(getBooleanUsage("lookForNested", importingSettings.isLookForNested))

    usages.add(getBooleanUsage("dedicatedModuleDir", importingSettings.dedicatedModuleDir.isNotBlank()))
    usages.add(getBooleanUsage("storeProjectFilesExternally", ExternalStorageConfigurationManager.getInstance(project).isEnabled))
    usages.add(getBooleanUsage("importAutomatically", importingSettings.isImportAutomatically))
    usages.add(getBooleanUsage("autoDetectCompiler", importingSettings.isAutoDetectCompiler))
    usages.add(getBooleanUsage("createModulesForAggregators", importingSettings.isCreateModulesForAggregators))
    usages.add(getBooleanUsage("createModuleGroups", importingSettings.isCreateModuleGroups))
    usages.add(getBooleanUsage("keepSourceFolders", importingSettings.isKeepSourceFolders))
    usages.add(getBooleanUsage("excludeTargetFolder", importingSettings.isExcludeTargetFolder))
    usages.add(getBooleanUsage("useMavenOutput", importingSettings.isUseMavenOutput))

    usages.add(getEnumUsage("generatedSourcesFolder", importingSettings.generatedSourcesFolder))
    usages.add(UsageDescriptor("updateFoldersOnImportPhase." + importingSettings.updateFoldersOnImportPhase))

    usages.add(getBooleanUsage("downloadDocsAutomatically", importingSettings.isDownloadDocsAutomatically))
    usages.add(getBooleanUsage("downloadSourcesAutomatically", importingSettings.isDownloadSourcesAutomatically))
    usages.add(getBooleanUsage("customDependencyTypes",
                               MavenImportingSettings.DEFAULT_DEPENDENCY_TYPES != importingSettings.dependencyTypes))

    usages.add(ExternalSystemUsagesCollector.getJRETypeUsage("jdkTypeForImporter", importingSettings.jdkForImporter))
    usages.add(ExternalSystemUsagesCollector.getJREVersionUsage(project, "jdkVersionForImporter", importingSettings.jdkForImporter))
    usages.add(getBooleanUsage("hasVmOptionsForImporter", importingSettings.vmOptionsForImporter.isNotBlank()))

    // Ignored Files page
    usages.add(getBooleanUsage("hasIgnoredFiles", manager.ignoredFilesPaths.isNotEmpty()))
    usages.add(getBooleanUsage("hasIgnoredPatterns", manager.ignoredFilesPatterns.isNotEmpty()))

    // Runner page
    val runnerSettings = MavenRunner.getInstance(project).settings
    usages.add(getBooleanUsage("delegateBuildRun", runnerSettings.isDelegateBuildToMaven));
    usages.add(getBooleanUsage("runMavenInBackground", runnerSettings.isRunMavenInBackground));
    usages.add(ExternalSystemUsagesCollector.getJRETypeUsage("runnerJreType", runnerSettings.jreName));
    usages.add(ExternalSystemUsagesCollector.getJREVersionUsage(project, "runnerJreVersion", runnerSettings.jreName));
    usages.add(getBooleanUsage("hasRunnerVmOptions", runnerSettings.vmOptions.isNotBlank()));
    usages.add(getBooleanUsage("hasRunnerEnvVariables", !runnerSettings.environmentProperties.isNullOrEmpty()));
    usages.add(getBooleanUsage("passParentEnv", runnerSettings.isPassParentEnv));
    usages.add(getBooleanUsage("skipTests", runnerSettings.isSkipTests));
    usages.add(getBooleanUsage("hasRunnerMavenProperties", !runnerSettings.mavenProperties.isNullOrEmpty()));
    return usages
  }
}
