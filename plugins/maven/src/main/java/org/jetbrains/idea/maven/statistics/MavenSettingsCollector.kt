// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.statistics.ExternalSystemUsageFields
import com.intellij.openapi.externalSystem.statistics.ExternalSystemUsageFields.JRE_TYPE_FIELD
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.project.isDirectoryBased
import org.jetbrains.idea.maven.execution.MavenExecutionOptions
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder
import org.jetbrains.idea.maven.project.MavenImportingSettings.UPDATE_FOLDERS_PHASES
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenDistributionsCache

class MavenSettingsCollector : ProjectUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptySet()

    val usages = mutableSetOf<MetricEvent>()

    // to have a total users baseline to calculate percentages of settings
    usages.add(HAS_MAVEN_PROJECT.metric(true))

    // Main page
    val generalSettings = manager.generalSettings
    usages.add(CHECKSUM_POLICY.metric(generalSettings.checksumPolicy))
    usages.add(FAILURE_BEHAVIOR.metric(generalSettings.failureBehavior))
    usages.add(ALWAYS_UPDATE_SNAPSHOTS.metric(generalSettings.isAlwaysUpdateSnapshots))
    usages.add(SHOW_ADVANCED_SETTINGS.metric(generalSettings.isShowDialogWithAdvancedSettings))
    usages.add(NON_RECURSIVE.metric(generalSettings.isNonRecursive))
    usages.add(PRINT_ERROR_STACK_TRACES.metric(generalSettings.isPrintErrorStackTraces))
    usages.add(WORK_OFFLINE.metric(generalSettings.isWorkOffline))
    usages.add(OUTPUT_LEVEL.metric(generalSettings.outputLevel))
    @Suppress("DEPRECATION")
    usages.add(LOGGING_LEVEL.metric(generalSettings.loggingLevel))
    try {
      val mavenDistribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(
        manager.rootProjects.firstOrNull()?.directory)
      val mavenVersion = mavenDistribution.version?.let { Version.parseVersion(it)?.toCompactString() } ?: "unknown"
      usages.add(MAVEN_VERSION.metric(mavenVersion))
    }
    catch (ignore: Exception) {
      // ignore invalid maven home configuration
    }
    usages.add(LOCAL_REPOSITORY.metric(generalSettings.localRepository.isNotBlank()))
    usages.add(USER_SETTINGS_FILE.metric(generalSettings.userSettingsFile.isNotBlank()))

    // Importing page
    val importingSettings = manager.importingSettings
    usages.add(LOOK_FOR_NESTED.metric(importingSettings.isLookForNested))

    usages.add(USE_WORKSPACE_IMPORT.metric(true))
    usages.add(DEDICATED_MODULE_DIR.metric(false))
    usages.add(STORE_PROJECT_FILES_EXTERNALLY.metric(ExternalStorageConfigurationManager.getInstance(project).isEnabled))
    usages.add(IS_DIRECTORY_BASED_PROJECT.metric(project.isDirectoryBased))
    usages.add(AUTO_DETECT_COMPILER.metric(importingSettings.isAutoDetectCompiler))
    usages.add(CREATE_MODULES_FOR_AGGREGATORS.metric(true))
    usages.add(KEEP_SOURCE_FOLDERS.metric(true))
    usages.add(EXCLUDE_TARGET_FOLDER.metric(importingSettings.isExcludeTargetFolder))
    usages.add(USE_MAVEN_OUTPUT.metric(importingSettings.isUseMavenOutput))

    usages.add(GENERATED_SOURCES_FOLDER.metric(importingSettings.generatedSourcesFolder))
    usages.add(UPDATE_FOLDERS_ON_IMPORT_PHASE.metric(importingSettings.updateFoldersOnImportPhase))

    usages.add(DOWNLOAD_DOCS_AUTOMATICALLY.metric(importingSettings.isDownloadDocsAutomatically))
    usages.add(DOWNLOAD_SOURCES_AUTOMATICALLY.metric(importingSettings.isDownloadSourcesAutomatically))
    usages.add(CUSTOM_DEPENDENCY_TYPES.metric(MavenImportingSettings.DEFAULT_DEPENDENCY_TYPES != importingSettings.dependencyTypes))

    usages.add(JDK_TYPE_FOR_IMPORTER.metric(ExternalSystemUsageFields.getJreType(importingSettings.jdkForImporter)))
    usages.add(JDK_VERSION_FOR_IMPORTER.metric(ExternalSystemUsageFields.getJreVersion(project, importingSettings.jdkForImporter)))
    usages.add(HAS_VM_OPTIONS_FOR_IMPORTER.metric(importingSettings.vmOptionsForImporter.isNotBlank()))

    // Ignored Files page
    usages.add(HAS_IGNORED_FILES.metric(manager.ignoredFilesPaths.isNotEmpty()))
    usages.add(HAS_IGNORED_PATTERNS.metric(manager.ignoredFilesPatterns.isNotEmpty()))

    // Runner page
    val runnerSettings = MavenRunner.getInstance(project).settings
    usages.add(DELEGATE_BUILD_RUN.metric(runnerSettings.isDelegateBuildToMaven))
    usages.add(RUNNER_JRE_TYPE.metric(ExternalSystemUsageFields.getJreType(runnerSettings.jreName)))
    usages.add(RUNNER_JRE_VERSION.metric(ExternalSystemUsageFields.getJreVersion(project, runnerSettings.jreName)))
    usages.add(HAS_RUNNER_VM_OPTIONS.metric(runnerSettings.vmOptions.isNotBlank()))
    usages.add(HAS_RUNNER_ENV_VARIABLES.metric(!runnerSettings.environmentProperties.isEmpty()))
    usages.add(PASS_PARENT_ENV.metric(runnerSettings.isPassParentEnv))
    usages.add(SKIP_TESTS.metric(runnerSettings.isSkipTests))
    usages.add(HAS_RUNNER_MAVEN_PROPERTIES.metric(!runnerSettings.mavenProperties.isNullOrEmpty()))
    return usages
  }

  private val GROUP = EventLogGroup("build.maven.state", 12)
  private val HAS_MAVEN_PROJECT = GROUP.registerEvent("hasMavenProject", EventFields.Enabled)
  private val ALWAYS_UPDATE_SNAPSHOTS = GROUP.registerEvent("alwaysUpdateSnapshots", EventFields.Enabled)
  private val SHOW_ADVANCED_SETTINGS = GROUP.registerEvent("showDialogWithAdvancedSettings", EventFields.Enabled)
  private val NON_RECURSIVE = GROUP.registerEvent("nonRecursive", EventFields.Enabled)
  private val PRINT_ERROR_STACK_TRACES = GROUP.registerEvent("printErrorStackTraces", EventFields.Enabled)
  private val WORK_OFFLINE = GROUP.registerEvent("workOffline", EventFields.Enabled)
  private val LOCAL_REPOSITORY = GROUP.registerEvent("localRepository", EventFields.Enabled)
  private val USER_SETTINGS_FILE = GROUP.registerEvent("userSettingsFile", EventFields.Enabled)
  private val LOOK_FOR_NESTED = GROUP.registerEvent("lookForNested", EventFields.Enabled)
  private val USE_WORKSPACE_IMPORT = GROUP.registerEvent("useWorkspaceImport", EventFields.Enabled)
  private val DEDICATED_MODULE_DIR = GROUP.registerEvent("dedicatedModuleDir", EventFields.Enabled)
  private val STORE_PROJECT_FILES_EXTERNALLY = GROUP.registerEvent("storeProjectFilesExternally", EventFields.Enabled)
  private val IS_DIRECTORY_BASED_PROJECT = GROUP.registerEvent("useDirectoryBasedProject", EventFields.Enabled)
  private val AUTO_DETECT_COMPILER = GROUP.registerEvent("autoDetectCompiler", EventFields.Enabled)
  private val CREATE_MODULES_FOR_AGGREGATORS = GROUP.registerEvent("createModulesForAggregators", EventFields.Enabled)
  private val KEEP_SOURCE_FOLDERS = GROUP.registerEvent("keepSourceFolders", EventFields.Enabled)
  private val EXCLUDE_TARGET_FOLDER = GROUP.registerEvent("excludeTargetFolder", EventFields.Enabled)
  private val USE_MAVEN_OUTPUT = GROUP.registerEvent("useMavenOutput", EventFields.Enabled)
  private val DOWNLOAD_DOCS_AUTOMATICALLY = GROUP.registerEvent("downloadDocsAutomatically", EventFields.Enabled)
  private val DOWNLOAD_SOURCES_AUTOMATICALLY = GROUP.registerEvent("downloadSourcesAutomatically", EventFields.Enabled)
  private val CUSTOM_DEPENDENCY_TYPES = GROUP.registerEvent("customDependencyTypes", EventFields.Enabled)
  private val HAS_VM_OPTIONS_FOR_IMPORTER = GROUP.registerEvent("hasVmOptionsForImporter", EventFields.Enabled)
  private val HAS_IGNORED_FILES = GROUP.registerEvent("hasIgnoredFiles", EventFields.Enabled)
  private val HAS_IGNORED_PATTERNS = GROUP.registerEvent("hasIgnoredPatterns", EventFields.Enabled)
  private val DELEGATE_BUILD_RUN = GROUP.registerEvent("delegateBuildRun", EventFields.Enabled)
  private val HAS_RUNNER_VM_OPTIONS = GROUP.registerEvent("hasRunnerVmOptions", EventFields.Enabled)
  private val HAS_RUNNER_ENV_VARIABLES = GROUP.registerEvent("hasRunnerEnvVariables", EventFields.Enabled)
  private val PASS_PARENT_ENV = GROUP.registerEvent("passParentEnv", EventFields.Enabled)
  private val SKIP_TESTS = GROUP.registerEvent("skipTests", EventFields.Enabled)
  private val HAS_RUNNER_MAVEN_PROPERTIES = GROUP.registerEvent("hasRunnerMavenProperties", EventFields.Enabled)

  private val VERSION_FIELD = EventFields.StringValidatedByRegexpReference("value", "version")

  private val CHECKSUM_POLICY = GROUP.registerEvent("checksumPolicy", EventFields.Enum("value",
                                                                                       MavenExecutionOptions.ChecksumPolicy::class.java) { it.name.lowercase() })
  private val FAILURE_BEHAVIOR = GROUP.registerEvent("failureBehavior", EventFields.Enum("value",
                                                                                         MavenExecutionOptions.FailureMode::class.java) { it.name.lowercase() })
  private val OUTPUT_LEVEL = GROUP.registerEvent("outputLevel", EventFields.Enum("value",
                                                                                 MavenExecutionOptions.LoggingLevel::class.java) { it.name.lowercase() })
  private val LOGGING_LEVEL = GROUP.registerEvent("loggingLevel", EventFields.Enum("value",
                                                                                   MavenExecutionOptions.LoggingLevel::class.java) { it.name.lowercase() })
  private val MAVEN_VERSION = GROUP.registerEvent("mavenVersion", VERSION_FIELD)
  private val GENERATED_SOURCES_FOLDER = GROUP.registerEvent("generatedSourcesFolder", EventFields.Enum("value",
                                                                                                        GeneratedSourcesFolder::class.java) { it.name.lowercase() })
  private val UPDATE_FOLDERS_ON_IMPORT_PHASE = GROUP.registerEvent("updateFoldersOnImportPhase",
                                                                   EventFields.String("value", UPDATE_FOLDERS_PHASES.toList()))

  private val RUNNER_JRE_VERSION = GROUP.registerEvent("runnerJreVersion", VERSION_FIELD)
  private val JDK_VERSION_FOR_IMPORTER = GROUP.registerEvent("jdkVersionForImporter", VERSION_FIELD)
  private val RUNNER_JRE_TYPE = GROUP.registerEvent("runnerJreType", JRE_TYPE_FIELD)
  private val JDK_TYPE_FOR_IMPORTER = GROUP.registerEvent("jdkTypeForImporter", JRE_TYPE_FIELD)
}
