// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.text.nullize
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.execution.*
import org.jetbrains.idea.maven.execution.MavenExternalParameters.MAVEN_OPTS
import org.jetbrains.idea.maven.execution.MavenExternalParameters.encodeProfiles
import org.jetbrains.idea.maven.execution.RunnerBundle.message
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.jetbrains.idea.maven.server.MavenServerEmbedder
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenUtil.getJdkForImporter
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

private const val JAVA_HOME_ENV_KEY = "JAVA_HOME"

class MavenCommandLineSetup(
  private val project: Project,
  private val name: @NlsSafe String,
  private val request: TargetEnvironmentRequest,
) {

  val commandLine = TargetedCommandLineBuilder(request)
  val platform = request.targetPlatform.platform

  private val defaultMavenRuntimeConfiguration: MavenRuntimeTargetConfiguration? = request.configuration?.runtimes?.findByType(
    MavenRuntimeTargetConfiguration::class.java)
  private val defaultJavaRuntimeConfiguration: JavaLanguageRuntimeConfiguration? = request.configuration?.runtimes?.findByType(
    JavaLanguageRuntimeConfiguration::class.java)

  private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, TargetProgressIndicator>>()
  private val dependingOnEnvironmentPromise = mutableListOf<Promise<Unit>>()

  init {
    commandLine.putUserData(setupKey, this)
  }

  @Throws(CantRunException::class)
  @JvmOverloads
  fun setupCommandLine(settings: MavenRunConfiguration.MavenSettings, setupEventListener: Boolean = true): MavenCommandLineSetup {
    val mavenOptsValues = mutableListOf<TargetValue<String>>()
    setupExePath()
    setupTargetJavaRuntime(mavenRunnerSettings(settings))
    setupTargetProjectDirectories(settings)
    if (setupEventListener) {
      setupMavenExtClassPath()
    }
    addMavenParameters(settings, mavenOptsValues)
    setupTargetEnvironmentVariables(settings, mavenOptsValues)
    return this
  }

  fun provideEnvironment(environment: TargetEnvironment, progressIndicator: TargetProgressIndicator) {
    environmentPromise.setResult(environment to progressIndicator)
    for (promise in dependingOnEnvironmentPromise) {
      promise.blockingGet(0)
    }
  }

  @Throws(CantRunException::class)
  private fun setupExePath() {
    if (defaultMavenRuntimeConfiguration == null) {
      commandLine.setExePath("mvn")
      return
    }

    val homePath = defaultMavenRuntimeConfiguration.homePath

    if (StringUtil.isEmptyOrSpaces(homePath)) {
      commandLine.setExePath("mvn")
      return
    }

    commandLine.addEnvironmentVariable("MAVEN_HOME", homePath)
    commandLine.addEnvironmentVariable("M2_HOME", homePath)
    commandLine.setExePath(joinPath(arrayOf(homePath, "bin", "mvn")))
  }

  private fun setupTargetJavaRuntime(runnerSettings: MavenRunnerSettings) {
    val javaHomePath: String? =
      when {
        runnerSettings.jreName != MavenRunnerSettings.USE_PROJECT_JDK -> runnerSettings.jreName
        defaultJavaRuntimeConfiguration?.homePath?.isNotBlank() == true -> defaultJavaRuntimeConfiguration.homePath
        else -> null
      } ?: runBlockingCancellable { calculateJavaHome() }
    if (javaHomePath != null) {
      commandLine.addEnvironmentVariable(JAVA_HOME_ENV_KEY, javaHomePath)
    }
  }

  private suspend fun calculateJavaHome(): String? {
    val descriptor = project.getEelDescriptor()
    val eel = descriptor.upgrade()
    val targetEnv = eel.exec.fetchLoginShellEnvVariables()
    val targetJavaHome = targetEnv[JAVA_HOME_ENV_KEY]
    if (targetJavaHome != null) {
      return targetJavaHome
    }
    val jdk = ProjectRootManager.getInstance(project).getProjectSdk() ?: getJdkForImporter(project)
    return jdk.homePath?.asTargetPathString()
  }

  private fun setupMavenExtClassPath() {
    val mavenEventListener = MavenServerManager.getInstance().getMavenEventListener()
    val uploadPath = Paths.get(toSystemDependentName(mavenEventListener.path))
    val uploadRoot = createUploadRoot(MavenRuntimeTypeConstants.MAVEN_EXT_CLASS_PATH_VOLUME, uploadPath.parent)
    request.uploadVolumes += uploadRoot
    val targetValue = upload(uploadRoot, uploadPath.toString(), uploadPath.fileName.toString())
    commandLine.addParameter(TargetValue.map(targetValue) { "-D" + MavenServerEmbedder.MAVEN_EXT_CLASS_PATH + "=" + it })
  }

  private fun addMavenParameters(settings: MavenRunConfiguration.MavenSettings, mavenOptsValues: MutableList<TargetValue<String>>) {
    val generalSettings: MavenGeneralSettings = mavenGeneralSettings(settings)
    val runnerSettings = mavenRunnerSettings(settings)
    if (runnerSettings.isSkipTests) {
      commandLine.addParameter("-DskipTests=true")
    }
    mavenOptsValues.add(TargetValue.fixed("-Didea.version=${MavenUtil.getIdeaVersionToPassToMavenProcess()}"))
    if (runnerSettings.vmOptions.isNotBlank()) {
      mavenOptsValues.add(TargetValue.fixed(runnerSettings.vmOptions))
    }

    val mavenPropertiesList = ParametersList()
    runnerSettings.mavenProperties
      .filterKeys { it.isNotEmpty() }
      .forEach { (key, value) -> mavenPropertiesList.addProperty(key, value) }
    commandLine.addParameters(mavenPropertiesList.parameters)

    val runnerParameters = settings.myRunnerParameters ?: MavenRunnerParameters()
    for (goal in runnerParameters.goals) {
      commandLine.addParameter(goal)
    }
    if (runnerParameters.pomFileName != null && !namesEqual(MavenConstants.POM_XML, runnerParameters.pomFileName)) {
      commandLine.addParameter("-f")
      commandLine.addParameter(runnerParameters.pomFileName)
    }

    val encodeProfiles = encodeProfiles(runnerParameters.profilesMap)
    if (encodeProfiles.isNotEmpty()) {
      commandLine.addParameters("-P", encodeProfiles)
    }
    if (generalSettings.isWorkOffline) {
      commandLine.addParameter("--offline")
    }
    if (generalSettings.outputLevel == MavenExecutionOptions.LoggingLevel.DEBUG) {
      commandLine.addParameter("--debug")
    }
    if (generalSettings.isNonRecursive) {
      commandLine.addParameter("--non-recursive")
    }
    if (generalSettings.isPrintErrorStackTraces) {
      commandLine.addParameter("--errors")
    }
    if (generalSettings.isAlwaysUpdateSnapshots) {
      commandLine.addParameter("--update-snapshots")
    }
    val threads = generalSettings.threads
    if (!threads.isNullOrBlank()) {
      commandLine.addParameters("-T", threads)
    }
    generalSettings.failureBehavior.commandLineOption.nullize(true)?.also { commandLine.addParameter(it) }
    generalSettings.checksumPolicy.commandLineOption.nullize(true)?.also { commandLine.addParameter(it) }

    if (generalSettings.userSettingsFile.isNotBlank()) {
      commandLine.addParameters("-s", generalSettings.userSettingsFile.asTargetPathString())
    }
    if (generalSettings.localRepository.isNotBlank()) {
      commandLine.addParameter("-Dmaven.repo.local=${MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo()}")
    }
  }

  private fun setupTargetEnvironmentVariables(
    settings: MavenRunConfiguration.MavenSettings,
    mavenOptsValues: MutableList<TargetValue<String>>,
  ) {
    val runnerSettings = mavenRunnerSettings(settings)
    runnerSettings.environmentProperties.forEach { (name, value) ->
      if (MAVEN_OPTS == name) {
        mavenOptsValues.add(TargetValue.fixed(value))
      }
      else {
        commandLine.addEnvironmentVariable(name, value)
      }
    }
    val targetValue = TargetValue.composite(mavenOptsValues) { it.joinToString(separator = " ") }
    commandLine.addEnvironmentVariable(MAVEN_OPTS, targetValue)
  }

  private fun mavenGeneralSettings(settings: MavenRunConfiguration.MavenSettings): MavenGeneralSettings {
    return settings.myGeneralSettings ?: MavenProjectsManager.getInstance(project).generalSettings
  }

  private fun mavenRunnerSettings(settings: MavenRunConfiguration.MavenSettings): MavenRunnerSettings {
    return settings.myRunnerSettings ?: MavenRunner.getInstance(project).state
  }

  private fun upload(
    uploadRoot: TargetEnvironment.UploadRoot,
    uploadPathString: String,
    uploadRelativePath: String,
  ): TargetValue<String> {
    val result = DeferredTargetValue(uploadPathString)
    dependingOnEnvironmentPromise += environmentPromise.then { (environment, progress) ->
      val volume = environment.uploadVolumes.getValue(uploadRoot)
      val resolvedTargetPath = volume.resolveTargetPath(uploadRelativePath)
      volume.upload(uploadRelativePath, progress)
      result.resolve(resolvedTargetPath)
    }
    return result
  }

  private fun createUploadRoot(volumeDescriptor: VolumeDescriptor, localRootPath: Path): TargetEnvironment.UploadRoot {
    return MavenRuntimeTargetConfiguration.createUploadRoot(defaultMavenRuntimeConfiguration, request, volumeDescriptor, localRootPath)
  }

  private fun setupTargetProjectDirectories(settings: MavenRunConfiguration.MavenSettings) {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val file = settings.myRunnerParameters?.let { VfsUtil.findFile(Path.of(it.workingDirPath), false) } ?: throw CantRunException(
      message("maven.target.message.unable.to.use.working.directory", name))
    val module = ReadAction.compute<Module?, Throwable> { ProjectFileIndex.getInstance(project).getModuleForFile(file) }
                 ?: throw CantRunException(
                   message("maven.target.message.unable.to.find.maven.project.for.working.directory", name))
    val mavenProject: MavenProject = mavenProjectsManager.findProject(module) ?: throw CantRunException(
      message("maven.target.message.unable.to.find.maven.project.for.working.directory", name))

    val mavenProjectDirectory = toSystemDependentName(mavenProject.directory)
    val pathsToUpload = findPathsToUpload(mavenProjectsManager, mavenProject)
    val commonAncestor = findCommonAncestor(pathsToUpload)
    val uploadPath = Paths.get(toSystemDependentName(commonAncestor!!))
    val uploadRoot = createUploadRoot(MavenRuntimeTypeConstants.PROJECT_FOLDER_VOLUME, uploadPath)
    request.uploadVolumes += uploadRoot
    val targetFileSeparator = request.targetPlatform.platform.fileSeparator

    var targetWorkingDirectory: TargetValue<String>? = null
    for (path in pathsToUpload) {
      val relativePath = getRelativePath(commonAncestor, path, File.separatorChar)
      val targetValue = upload(uploadRoot, path, relativePath!!)
      if (targetWorkingDirectory == null && isAncestor(path, mavenProjectDirectory, false)) {
        val workingDirRelativePath = getRelativePath(path, mavenProjectDirectory, File.separatorChar)!!
        val targetWorkingDirRelativePath = if (workingDirRelativePath == ".") ""
        else toSystemDependentName(workingDirRelativePath, targetFileSeparator)
        targetWorkingDirectory = TargetValue.map(targetValue) { "$it$targetFileSeparator$targetWorkingDirRelativePath" }
      }
    }
    commandLine.setWorkingDirectory(targetWorkingDirectory!!)
  }

  private fun findCommonAncestor(paths: Set<String>): String? {
    var commonRoot: File? = null
    for (path in paths) {
      commonRoot = if (commonRoot == null) File(path) else findAncestor(commonRoot, File(path))
      requireNotNull(commonRoot) { "no common root found" }
    }
    assert(commonRoot != null)
    return commonRoot!!.path
  }

  private fun findPathsToUpload(mavenProjectsManager: MavenProjectsManager, project: MavenProject): Set<String> {
    val rootProject = mavenProjectsManager.findRootProject(project) ?: return emptySet()
    val projects = LinkedList<MavenProject>()
    projects += rootProject
    projects.addAll(findAllInheritors(mavenProjectsManager, rootProject))
    val paths: MutableSet<String> = HashSet()
    while (projects.isNotEmpty()) {
      val mavenProject = projects.pop()
      val projectDirectory = toSystemDependentName(mavenProject!!.directory)
      if (paths.any { isAncestor(it, projectDirectory, false) }) {
        continue
      }
      paths.removeIf { isAncestor(projectDirectory, it, false) }
      paths.add(projectDirectory)
    }
    return paths
  }

  private fun findAllInheritors(mavenProjectsManager: MavenProjectsManager, project: MavenProject): List<MavenProject> {
    val inheritors = mavenProjectsManager.findInheritors(project)
    val result = ArrayList(inheritors)
    for (inheritor in inheritors) {
      result += findAllInheritors(mavenProjectsManager, inheritor)
    }
    return result
  }

  private fun joinPath(segments: Array<String>) = segments.joinTo(StringBuilder(), platform.fileSeparator.toString()).toString()

  companion object {
    @JvmStatic
    val setupKey = Key.create<MavenCommandLineSetup>("org.jetbrains.idea.maven.execution.target.MavenCommandLineSetup")
  }

  private fun String.asTargetPathString(): String = Path.of(this).asEelPath().toString()
}