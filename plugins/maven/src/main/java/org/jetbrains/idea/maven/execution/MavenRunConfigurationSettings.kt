// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.util.ProgramParametersUtil.expandPathAndMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Transient
import org.jdom.Element
import org.jetbrains.idea.maven.execution.MavenExecutionOptions.*
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProjectsManager

@Suppress("DuplicatedCode")
class MavenRunConfigurationSettings : Cloneable {
  /** @see MavenRunnerParameters */
  var commandLine: String = ""
  var workingDirectory: String = ""
  var profilesMap: Map<String, Boolean> = HashMap()
  var isResolveToWorkspace: Boolean = false

  /** @see org.jetbrains.idea.maven.project.MavenGeneralSettings */
  var mavenHome: String? = null
  var userSettings: String? = null
  var localRepository: String? = null
  var threads: String? = null
  var checksumPolicy: ChecksumPolicy? = null
  var outputLevel: LoggingLevel? = null
  var failureBehavior: FailureMode? = null
  var isWorkOffline: Boolean = false
  var isUsePluginRegistry: Boolean = false
  var isPrintErrorStackTraces: Boolean = false
  var isAlwaysUpdateSnapshots: Boolean = false
  var isNonRecursive: Boolean = false

  /** @see MavenRunnerSettings */
  var jreName: String? = null
  var vmOptions: String? = null
  var environment: Map<String, String> = HashMap()
  var mavenProperties: Map<String, String> = HashMap()
  var isPassParentEnvs: Boolean = true
  var isSkipTests: Boolean = false

  public override fun clone(): MavenRunConfigurationSettings {
    val clone = super.clone() as MavenRunConfigurationSettings
    clone.setSettings(this)
    return clone
  }

  private fun setSettings(settings: MavenRunConfigurationSettings) {
    commandLine = settings.commandLine
    workingDirectory = settings.workingDirectory
    profilesMap = settings.profilesMap
    isResolveToWorkspace = settings.isResolveToWorkspace

    mavenHome = settings.mavenHome
    userSettings = settings.userSettings
    localRepository = settings.localRepository
    threads = settings.threads
    isWorkOffline = settings.isWorkOffline
    checksumPolicy = settings.checksumPolicy
    outputLevel = settings.outputLevel
    isUsePluginRegistry = settings.isUsePluginRegistry
    isPrintErrorStackTraces = settings.isPrintErrorStackTraces
    isAlwaysUpdateSnapshots = settings.isAlwaysUpdateSnapshots
    failureBehavior = settings.failureBehavior
    isNonRecursive = settings.isNonRecursive

    jreName = settings.jreName
    vmOptions = settings.vmOptions
    environment = settings.environment
    mavenProperties = settings.mavenProperties
    isPassParentEnvs = settings.isPassParentEnvs
    isSkipTests = settings.isSkipTests
  }

  @Transient
  fun getGeneralSettings(project: Project): MavenGeneralSettings? {
    val projectsManager = MavenProjectsManager.getInstance(project)
    val originalSettings = projectsManager.generalSettings
    val settings = originalSettings.clone()
    mavenHome?.let { settings.mavenHome = it }
    userSettings?.let { settings.setUserSettingsFile(expandPathAndMacros(it, null, project)) }
    localRepository?.let { settings.setLocalRepository(expandPathAndMacros(it, null, project)) }
    threads?.let { settings.threads = it }
    checksumPolicy?.let { settings.checksumPolicy = it }
    outputLevel?.let { settings.outputLevel = it }
    failureBehavior?.let { settings.failureBehavior = it }
    if (isWorkOffline) settings.isWorkOffline = isWorkOffline
    if (isUsePluginRegistry) settings.isUsePluginRegistry = isUsePluginRegistry
    if (isPrintErrorStackTraces) settings.isPrintErrorStackTraces = isPrintErrorStackTraces
    if (isAlwaysUpdateSnapshots) settings.isAlwaysUpdateSnapshots = isAlwaysUpdateSnapshots
    if (isNonRecursive) settings.isNonRecursive = isNonRecursive
    return if (settings == originalSettings) null else settings
  }

  @Transient
  fun setGeneralSettings(settings: MavenGeneralSettings) {
    mavenHome = settings.mavenHome
    userSettings = settings.userSettingsFile
    localRepository = settings.localRepository
    threads = settings.threads
    isWorkOffline = settings.isWorkOffline
    checksumPolicy = settings.checksumPolicy
    outputLevel = settings.outputLevel
    isUsePluginRegistry = settings.isUsePluginRegistry
    isPrintErrorStackTraces = settings.isPrintErrorStackTraces
    isAlwaysUpdateSnapshots = settings.isAlwaysUpdateSnapshots
    failureBehavior = settings.failureBehavior
    isNonRecursive = settings.isNonRecursive
  }

  @Transient
  fun getRunnerSettings(project: Project): MavenRunnerSettings? {
    val mavenRunner = MavenRunner.getInstance(project)
    val originalSettings = mavenRunner.settings
    val settings = originalSettings.clone()
    jreName?.let { settings.setJreName(it) }
    vmOptions?.let { settings.setVmOptions(expandPathAndMacros(it, null, project)) }
    isPassParentEnvs.let { settings.isPassParentEnv = it }
    if (environment.isNotEmpty()) settings.environmentProperties = environment
    if (mavenProperties.isNotEmpty()) settings.mavenProperties = mavenProperties
    if (isSkipTests) settings.isSkipTests = isSkipTests
    return if (settings == originalSettings) null else settings
  }

  @Transient
  fun setRunnerSettings(settings: MavenRunnerSettings) {
    jreName = settings.jreName
    vmOptions = settings.vmOptions
    environment = settings.environmentProperties
    mavenProperties = settings.mavenProperties
    isPassParentEnvs = settings.isPassParentEnv
    isSkipTests = settings.isSkipTests
  }

  @Transient
  fun getRunnerParameters(): MavenRunnerParameters {
    val parameters = MavenRunnerParameters()
    parameters.commandLine = commandLine
    parameters.workingDirPath = workingDirectory
    parameters.profilesMap = profilesMap
    parameters.isResolveToWorkspace = isResolveToWorkspace
    return parameters
  }

  @Transient
  fun setRunnerParameters(parameters: MavenRunnerParameters) {
    commandLine = parameters.commandLine
    workingDirectory = parameters.workingDirPath
    profilesMap = parameters.profilesMap
    isResolveToWorkspace = parameters.isResolveToWorkspace
  }

  fun readExternal(element: Element) {
    val settingsElement = element.getChild(MavenRunConfigurationSettings::class.simpleName) ?: return
    setSettings(XmlSerializer.deserialize(settingsElement, MavenRunConfigurationSettings::class.java))
  }

  fun writeExternal(element: Element) {
    element.addContent(XmlSerializer.serialize(this))
  }
}