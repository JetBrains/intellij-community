// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.text.nullize
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.execution.MavenExecutionOptions
import org.jetbrains.idea.maven.execution.MavenExternalParameters.MAVEN_OPTS
import org.jetbrains.idea.maven.execution.MavenExternalParameters.encodeProfiles
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.RunnerBundle.message
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerEmbedder
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class MavenCommandLineSetup(private val project: Project,
                            private val name: @NlsSafe String,
                            private val request: TargetEnvironmentRequest,
                            private val target: TargetEnvironmentConfiguration) {

  val commandLine = TargetedCommandLineBuilder(request)
  val platform = request.targetPlatform.platform

  private val defaultMavenRuntimeConfiguration: MavenRuntimeTargetConfiguration? = target.runtimes.findByType(
    MavenRuntimeTargetConfiguration::class.java)
  private val defaultJavaRuntimeConfiguration: JavaLanguageRuntimeConfiguration? = target.runtimes.findByType(
    JavaLanguageRuntimeConfiguration::class.java)

  private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, ProgressIndicator>>()
  private val dependingOnEnvironmentPromise = mutableListOf<Promise<Unit>>()

  init {
    commandLine.putUserData(setupKey, this)
  }

  @Throws(CantRunException::class)
  fun setupCommandLine(settings: MavenRunConfiguration.MavenSettings): MavenCommandLineSetup {
    val mavenOptsValues = mutableListOf<TargetValue<String>>()
    setupExePath(settings.myGeneralSettings)
    setupTargetJavaRuntime(settings.myRunnerSettings)
    setupTargetProjectDirectories(settings)
    setupMavenExtClassPath(mavenOptsValues)
    addMavenParameters(settings, mavenOptsValues)
    commandLine.addEnvironmentVariable(MAVEN_OPTS,
                                       TargetValue.composite(mavenOptsValues) { values -> values.joinToString(separator = " ") })
    return this
  }

  fun provideEnvironment(environment: TargetEnvironment, progressIndicator: ProgressIndicator) {
    environmentPromise.setResult(environment to progressIndicator)
    for (promise in dependingOnEnvironmentPromise) {
      promise.blockingGet(0)
    }
  }

  @Throws(CantRunException::class)
  private fun setupExePath(generalSettings: MavenGeneralSettings) {
    if (defaultMavenRuntimeConfiguration == null) {
      throw CantRunException(message("maven.target.message.cannot.find.maven.configuration.in.target", target.displayName))
    }

    val homePath: String
    if (generalSettings.mavenHome == MavenServerManager.BUNDLED_MAVEN_3) {
      homePath = defaultMavenRuntimeConfiguration.homePath
    }
    else {
      homePath = generalSettings.mavenHome
    }

    if (StringUtil.isEmptyOrSpaces(homePath)) {
      throw CantRunException(message("maven.target.message.maven.home.not.configured.for.target", target.displayName))
    }

    commandLine.addEnvironmentVariable("MAVEN_HOME", homePath)
    commandLine.setExePath(joinPath(arrayOf(homePath, "bin", "mvn")))
  }

  private fun setupTargetJavaRuntime(runnerSettings: MavenRunnerSettings) {
    when {
      runnerSettings.jreName != MavenRunnerSettings.USE_PROJECT_JDK -> runnerSettings.jreName
      defaultJavaRuntimeConfiguration?.homePath?.isNotBlank() == true -> defaultJavaRuntimeConfiguration.homePath
      else -> null
    }?.let { commandLine.addEnvironmentVariable("JAVA_HOME", it) }
  }

  private fun setupMavenExtClassPath(mavenOptsValues: MutableList<TargetValue<String>>) {
    val mavenEventListener = MavenServerManager.getMavenEventListener()
    val uploadPath = Paths.get(toSystemDependentName(mavenEventListener.path))
    val uploadRoot = createUploadRoot(MavenRuntimeType.MAVEN_EXT_CLASS_PATH_VOLUME, uploadPath.parent)
    request.uploadVolumes += uploadRoot
    val targetValue = upload(uploadRoot, uploadPath.toString(), uploadPath.fileName.toString())
    mavenOptsValues.add(TargetValue.map(targetValue) { "-D" + MavenServerEmbedder.MAVEN_EXT_CLASS_PATH + "=" + it })
  }

  private fun addMavenParameters(settings: MavenRunConfiguration.MavenSettings, mavenOptsValues: MutableList<TargetValue<String>>) {
    val generalSettings: MavenGeneralSettings = settings.myGeneralSettings ?: MavenProjectsManager.getInstance(project).generalSettings
    val runnerSettings: MavenRunnerSettings = settings.myRunnerSettings ?: MavenRunner.getInstance(project).state
    if (runnerSettings.isSkipTests) {
      commandLine.addParameter("-DskipTests=true")
    }
    mavenOptsValues.add(TargetValue.fixed("-Didea.version=${MavenUtil.getIdeaVersionToPassToMavenProcess()}"))
    if (runnerSettings.vmOptions.isNotBlank()) {
      mavenOptsValues.add(TargetValue.fixed(runnerSettings.vmOptions))
    }

    runnerSettings.environmentProperties.forEach { (name, value) ->
      if (MAVEN_OPTS == name) {
        mavenOptsValues.add(TargetValue.fixed(value))
      }
      else {
        commandLine.addEnvironmentVariable(name, value)
      }
    }
    val mavenPropertiesList = ParametersList()
    runnerSettings.mavenProperties
      .filterKeys { it.isNotEmpty() }
      .forEach { (key, value) -> mavenPropertiesList.addProperty(key, value) }
    commandLine.addParameters(mavenPropertiesList.parameters)

    val runnerParameters = settings.myRunnerParameters
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
    if (!generalSettings.threads.isNullOrBlank()) {
      commandLine.addParameters("-T", generalSettings.threads)
    }
    generalSettings.failureBehavior.commandLineOption.nullize(true)?.also { commandLine.addParameter(it) }
    generalSettings.checksumPolicy.commandLineOption.nullize(true)?.also { commandLine.addParameter(it) }

    if (generalSettings.userSettingsFile.isNotBlank()) {
      commandLine.addParameters("-s", generalSettings.userSettingsFile)
    }
    generalSettings.localRepository.nullize(true)?.also { commandLine.addParameter("-Dmaven.repo.local=$it") }
  }

  private fun upload(uploadRoot: TargetEnvironment.UploadRoot,
                     uploadPathString: String,
                     uploadRelativePath: String): TargetValue<String> {
    val result = DeferredTargetValue(uploadPathString)
    dependingOnEnvironmentPromise += environmentPromise.then { (environment, progress) ->
      val volume = environment.uploadVolumes.getValue(uploadRoot)
      result.resolve(volume.upload(uploadRelativePath, progress))
    }
    return result
  }

  private fun createUploadRoot(volumeDescriptor: VolumeDescriptor, localRootPath: Path): TargetEnvironment.UploadRoot {
    return defaultMavenRuntimeConfiguration?.createUploadRoot(volumeDescriptor, localRootPath)
           ?: TargetEnvironment.UploadRoot(localRootPath = localRootPath,
                                           targetRootPath = TargetEnvironment.TargetPath.Temporary())
  }

  private fun setupTargetProjectDirectories(settings: MavenRunConfiguration.MavenSettings) {
    val workingDirectory = settings.myRunnerParameters.workingDirFile

    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val file = VfsUtil.findFileByIoFile(workingDirectory, false) ?: throw CantRunException(
      message("maven.target.message.unable.to.use.working.directory", name))
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(file) ?: throw CantRunException(
      message("maven.target.message.unable.to.find.maven.project.for.working.directory", name))
    val mavenProject: MavenProject = mavenProjectsManager.findProject(module) ?: throw CantRunException(
      message("maven.target.message.unable.to.find.maven.project.for.working.directory", name))

    val mavenProjectDirectory = toSystemDependentName(mavenProject.directory)
    val pathsToUpload = findPathsToUpload(mavenProjectsManager, mavenProject)
    val commonAncestor = findCommonAncestor(pathsToUpload)
    val uploadPath = Paths.get(toSystemDependentName(commonAncestor!!))
    val uploadRoot = createUploadRoot(MavenRuntimeType.PROJECT_FOLDER_VOLUME, uploadPath)
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
}