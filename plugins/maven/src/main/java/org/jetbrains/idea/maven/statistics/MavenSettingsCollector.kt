// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getEnumUsage
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenSettingsCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "statistics.build.maven.state"

  override fun getUsages(project: Project): Set<UsageDescriptor> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptySet()

    val usages = mutableSetOf<UsageDescriptor>()
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

    val importingSettings = manager.importingSettings
    usages.add(getEnumUsage("generatedSourcesFolder", importingSettings.generatedSourcesFolder))
    usages.add(getBooleanUsage("createModuleGroups", importingSettings.isCreateModuleGroups))
    usages.add(getBooleanUsage("createModulesForAggregators", importingSettings.isCreateModulesForAggregators))
    usages.add(getBooleanUsage("downloadDocsAutomatically", importingSettings.isDownloadDocsAutomatically))
    usages.add(getBooleanUsage("downloadSourcesAutomatically", importingSettings.isDownloadSourcesAutomatically))
    usages.add(getBooleanUsage("excludeTargetFolder", importingSettings.isExcludeTargetFolder))
    usages.add(getBooleanUsage("importAutomatically", importingSettings.isImportAutomatically))
    usages.add(getBooleanUsage("keepSourceFolders", importingSettings.isKeepSourceFolders))
    usages.add(getBooleanUsage("lookForNested", importingSettings.isLookForNested))
    usages.add(getBooleanUsage("useMavenOutput", importingSettings.isUseMavenOutput))
    usages.add(UsageDescriptor("updateFoldersOnImportPhase." + importingSettings.updateFoldersOnImportPhase))

    val runnerSettings = MavenRunner.getInstance(project).settings
    usages.add(getBooleanUsage("delegateBuildRun", runnerSettings.isDelegateBuildToMaven));
    usages.add(getBooleanUsage("passParentEnv", runnerSettings.isPassParentEnv));
    usages.add(getBooleanUsage("runMavenInBackground", runnerSettings.isRunMavenInBackground));
    usages.add(getBooleanUsage("skipTests", runnerSettings.isSkipTests));
    return usages
  }
}
