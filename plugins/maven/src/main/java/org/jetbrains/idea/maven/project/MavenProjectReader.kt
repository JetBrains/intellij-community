// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import org.jdom.Element
import org.jetbrains.idea.maven.buildtool.MavenLogEventHandler
import org.jetbrains.idea.maven.dom.converters.MavenConsumerPomUtil.isAutomaticVersionFeatureEnabled
import org.jetbrains.idea.maven.internal.ReadStatisticsCollector
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenProjectResolutionUtil.Companion.resolveProjectSync
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.ProfileApplicationResult
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildValueByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildrenByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildrenValuesByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.hasChildByPath
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.IOException
import java.nio.file.Path

private const val UNKNOWN = MavenId.UNKNOWN_VALUE

class MavenProjectReader(private val myProject: Project) {
  private val myCache = MavenReadProjectCache()
  private val myReadHelper: MavenProjectModelReadHelper = MavenUtil.createModelReadHelper(myProject)
  private var mySettingsProfilesCache: SettingsProfilesCache? = null

  @Deprecated("Use async method", ReplaceWith("readProjectAsync(generalSettings, file, explicitProfiles, locator) }"))
  fun readProject(generalSettings: MavenGeneralSettings,
                  file: VirtualFile,
                  explicitProfiles: MavenExplicitProfiles,
                  locator: MavenProjectReaderProjectLocator): MavenProjectReaderResult {
    return runBlockingMaybeCancellable { readProjectAsync(generalSettings, file, explicitProfiles, locator) }
  }

  suspend fun readProjectAsync(generalSettings: MavenGeneralSettings,
                               file: VirtualFile,
                               explicitProfiles: MavenExplicitProfiles,
                               locator: MavenProjectReaderProjectLocator): MavenProjectReaderResult {
    val basedir = MavenUtil.getBaseDir(file)

    val readResult = doReadProjectModel(generalSettings, basedir, file, explicitProfiles, HashSet(), locator)

    val model = myReadHelper.interpolate(basedir, file, readResult.first.model)

    val modelMap: MutableMap<String, String?> = HashMap()
    modelMap["groupId"] = model.mavenId.groupId
    modelMap["artifactId"] = model.mavenId.artifactId
    modelMap["version"] = model.mavenId.version
    modelMap["build.outputDirectory"] = model.build.outputDirectory
    modelMap["build.testOutputDirectory"] = model.build.testOutputDirectory
    modelMap["build.finalName"] = model.build.finalName
    modelMap["build.directory"] = model.build.directory

    return MavenProjectReaderResult(model,
                                    modelMap,
                                    readResult.second,
                                    null,
                                    readResult.first.problems,
                                    HashSet())
  }

  private suspend fun doReadProjectModel(generalSettings: MavenGeneralSettings,
                                         projectPomDir: Path,
                                         file: VirtualFile,
                                         explicitProfiles: MavenExplicitProfiles,
                                         recursionGuard: MutableSet<VirtualFile>,
                                         locator: MavenProjectReaderProjectLocator): Pair<RawModelReadResult, MavenExplicitProfiles> {
    var cachedModel = myCache[file]
    if (cachedModel == null) {
      cachedModel = doReadProjectModel(myProject, file, false)
      myCache.put(file, cachedModel)
    }

    // todo modifying cached model and problems here??????
    var model = cachedModel.model
    val alwaysOnProfiles = cachedModel.alwaysOnProfiles
    val problems = cachedModel.problems

    model = resolveInheritance(generalSettings, model, projectPomDir, file, explicitProfiles, recursionGuard, locator, problems)
    addSettingsProfiles(generalSettings, model, alwaysOnProfiles, problems)

    val applied = applyProfiles(model, projectPomDir, MavenUtil.getBaseDir(file), explicitProfiles, alwaysOnProfiles)
    model = applied.model

    repairModelBody(model)

    return Pair.create(RawModelReadResult(model, problems, alwaysOnProfiles), applied.activatedProfiles)
  }

  private suspend fun addSettingsProfiles(generalSettings: MavenGeneralSettings,
                                          model: MavenModel,
                                          alwaysOnProfiles: MutableSet<String>,
                                          problems: MutableCollection<MavenProjectProblem>) {
    if (mySettingsProfilesCache == null) {
      val settingsProfiles: MutableList<MavenProfile> = ArrayList()
      val settingsProblems = MavenProjectProblem.createProblemsList()
      val settingsAlwaysOnProfiles: MutableSet<String> = HashSet()

      for (each in generalSettings.effectiveSettingsFiles) {
        collectProfilesFromSettingsXmlOrProfilesXml(each,
                                                    "settings",
                                                    false,
                                                    MavenConstants.PROFILE_FROM_SETTINGS_XML,
                                                    settingsProfiles,
                                                    settingsAlwaysOnProfiles,
                                                    settingsProblems)
      }
      mySettingsProfilesCache = SettingsProfilesCache(settingsProfiles, settingsAlwaysOnProfiles, settingsProblems)
    }

    val modelProfiles: MutableList<MavenProfile> = ArrayList(model.profiles)
    for (each in mySettingsProfilesCache!!.profiles) {
      addProfileIfDoesNotExist(each, modelProfiles)
    }
    model.profiles = modelProfiles

    problems.addAll(mySettingsProfilesCache!!.problems)
    alwaysOnProfiles.addAll(mySettingsProfilesCache!!.alwaysOnProfiles)
  }

  private fun applyProfiles(model: MavenModel,
                            projectPomDir: Path,
                            basedir: Path,
                            explicitProfiles: MavenExplicitProfiles,
                            alwaysOnProfiles: Collection<String>): ProfileApplicationResult {
    return MavenServerManager.getInstance().getConnector(myProject, projectPomDir.toAbsolutePath().toString())
      .applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles)
  }

  private suspend fun resolveInheritance(generalSettings: MavenGeneralSettings,
                                         model: MavenModel,
                                         projectPomDir: Path,
                                         file: VirtualFile,
                                         explicitProfiles: MavenExplicitProfiles,
                                         recursionGuard: MutableSet<VirtualFile>,
                                         locator: MavenProjectReaderProjectLocator,
                                         problems: MutableCollection<MavenProjectProblem>): MavenModel {
    var model = model
    if (recursionGuard.contains(file)) {
      problems.add(MavenProjectProblem.createProblem(
        file.path, MavenProjectBundle.message("maven.project.problem.recursiveInheritance"),
        MavenProjectProblem.ProblemType.PARENT,
        false))
      return model
    }
    recursionGuard.add(file)

    try {
      val parentDesc = arrayOfNulls<MavenParentDesc>(1)
      val parent = model.parent
      if (parent != null) {
        if (model.mavenId == parent.mavenId) {
          problems
            .add(MavenProjectProblem.createProblem(
              file.path,
              MavenProjectBundle.message("maven.project.problem.selfInheritance"),
              MavenProjectProblem.ProblemType.PARENT,
              false))
          return model
        }
        parentDesc[0] = MavenParentDesc(parent.mavenId, parent.relativePath)
      }

      val parentModelWithProblems =
        object : MavenParentProjectFileAsyncProcessor<Pair<VirtualFile, RawModelReadResult>>(myProject) {
          override fun findManagedFile(id: MavenId): VirtualFile? {
            return locator.findProjectFile(id)
          }

          override suspend fun processRelativeParent(parentFile: VirtualFile): Pair<VirtualFile, RawModelReadResult>? {
            val parentModel = doReadProjectModel(myProject, parentFile, true).model
            val parentId = parentDesc[0]!!.parentId
            if (parentId != parentModel.mavenId) return null

            return super.processRelativeParent(parentFile)
          }

          override suspend fun processSuperParent(parentFile: VirtualFile): Pair<VirtualFile, RawModelReadResult>? {
            return null // do not process superPom
          }

          override suspend fun doProcessParent(parentFile: VirtualFile): Pair<VirtualFile, RawModelReadResult>? {
            val result = doReadProjectModel(generalSettings, projectPomDir, parentFile, explicitProfiles, recursionGuard, locator).first
            return Pair.create(parentFile, result)
          }
        }.process(generalSettings, file, parentDesc[0])

      if (parentModelWithProblems == null) return model // no parent or parent not found;


      val parentModel = parentModelWithProblems.second!!.model
      if (!parentModelWithProblems.second!!.problems.isEmpty()) {
        problems.add(MavenProjectProblem.createProblem(
          parentModelWithProblems.first!!.path,
          MavenProjectBundle.message("maven.project.problem.parentHasProblems",
                                     parentModel.mavenId),
          MavenProjectProblem.ProblemType.PARENT,
          false))
      }

      model = myReadHelper.assembleInheritance(projectPomDir, parentModel, model)

      // todo: it is a quick-hack here - we add inherited dummy profiles to correctly collect activated profiles in 'applyProfiles'.
      val profiles = model.profiles
      for (each in parentModel.profiles) {
        val copyProfile = MavenProfile(each.id, each.source)
        if (each.activation != null) {
          copyProfile.activation = each.activation.clone()
        }

        addProfileIfDoesNotExist(copyProfile, profiles)
      }
      return model
    }
    finally {
      recursionGuard.remove(file)
    }
  }

  // used in third-party plugins
  @Deprecated("use {@link MavenProjectResolver}")
  @Throws(MavenProcessCanceledException::class)
  fun resolveProject(generalSettings: MavenGeneralSettings,
                     embedder: MavenEmbedderWrapper,
                     files: Collection<VirtualFile>,
                     explicitProfiles: MavenExplicitProfiles,
                     locator: MavenProjectReaderProjectLocator): Collection<MavenProjectReaderResult> {
    val reporter = object : RawProgressReporter {}
    return resolveProjectSync(
      this,
      generalSettings,
      embedder,
      files,
      explicitProfiles,
      locator,
      reporter,
      MavenLogEventHandler,
      null,
      false)
  }

  private class SettingsProfilesCache(val profiles: List<MavenProfile>,
                                      val alwaysOnProfiles: Set<String>,
                                      val problems: Collection<MavenProjectProblem>)

  class RawModelReadResult(var model: MavenModel,
                           var problems: MutableCollection<MavenProjectProblem>,
                           var alwaysOnProfiles: MutableSet<String>)

  private suspend fun doReadProjectModel(project: Project, file: VirtualFile, headerOnly: Boolean): RawModelReadResult {
    val problems = MavenProjectProblem.createProblemsList()
    val alwaysOnProfiles: MutableSet<String> = HashSet()

    val fileExtension = file.extension
    if (!"pom".equals(fileExtension, ignoreCase = true) && !"xml".equals(fileExtension, ignoreCase = true)) {
      return readProjectModelUsingMavenServer(project, file, problems, alwaysOnProfiles)
    }

    return readMavenProjectModel(file, headerOnly, problems, alwaysOnProfiles, isAutomaticVersionFeatureEnabled(file, project))
  }

  private fun readProjectModelUsingMavenServer(project: Project,
                                               file: VirtualFile,
                                               problems: MutableCollection<MavenProjectProblem>,
                                               alwaysOnProfiles: MutableSet<String>): RawModelReadResult {
    var result: MavenModel? = null
    val basedir = MavenUtil.getBaseDir(file).toString()
    val manager = MavenProjectsManager.getInstance(project).embeddersManager
    val embedder = manager.getEmbedder(MavenEmbeddersManager.FOR_MODEL_READ, basedir)
    try {
      result = embedder.readModel(VfsUtilCore.virtualToIoFile(file))
    }
    catch (ignore: MavenProcessCanceledException) {
    }
    finally {
      manager.release(embedder)
    }

    if (result == null) {
      result = MavenModel()
      result.packaging = MavenConstants.TYPE_JAR
    }
    return RawModelReadResult(result, problems, alwaysOnProfiles)
  }

  private suspend fun readMavenProjectModel(file: VirtualFile,
                                            headerOnly: Boolean,
                                            problems: MutableCollection<MavenProjectProblem>,
                                            alwaysOnProfiles: MutableSet<String>,
                                            isAutomaticVersionFeatureEnabled: Boolean): RawModelReadResult {
    val result = MavenModel()
    val xmlProject = readXml(file, problems, MavenProjectProblem.ProblemType.SYNTAX)
    if (xmlProject == null || "project" != xmlProject.name) {
      result.packaging = MavenConstants.TYPE_JAR
      return RawModelReadResult(result, problems, alwaysOnProfiles)
    }

    val parent: MavenParent
    if (hasChildByPath(xmlProject, "parent")) {
      parent = MavenParent(MavenId(findChildValueByPath(xmlProject, "parent.groupId", UNKNOWN),
                                   findChildValueByPath(xmlProject, "parent.artifactId", UNKNOWN),
                                   calculateParentVersion(xmlProject, problems, file, isAutomaticVersionFeatureEnabled)),
                           findChildValueByPath(xmlProject, "parent.relativePath", "../pom.xml"))
      result.parent = parent
    }
    else {
      parent = MavenParent(MavenId(UNKNOWN, UNKNOWN, UNKNOWN), "../pom.xml")
    }

    result.mavenId = MavenId(
      findChildValueByPath(xmlProject, "groupId", parent.mavenId.groupId),
      findChildValueByPath(xmlProject, "artifactId", UNKNOWN),
      findChildValueByPath(xmlProject, "version", parent.mavenId.version))

    if (headerOnly) return RawModelReadResult(result, problems, alwaysOnProfiles)

    result.packaging = findChildValueByPath(xmlProject, "packaging", MavenConstants.TYPE_JAR)
    result.name = findChildValueByPath(xmlProject, "name")

    readModelBody(result, result.build, xmlProject)

    result.profiles = collectProfiles(file, xmlProject, problems, alwaysOnProfiles)
    return RawModelReadResult(result, problems, alwaysOnProfiles)
  }

  private suspend fun calculateParentVersion(
    xmlProject: Element?,
    problems: MutableCollection<MavenProjectProblem>,
    file: VirtualFile,
    isAutomaticVersionFeatureEnabled: Boolean): String {
    var version = findChildValueByPath(xmlProject, "parent.version")
    if (version != null || !isAutomaticVersionFeatureEnabled) {
      return StringUtil.notNullize(version, UNKNOWN)
    }
    val parentGroupId = findChildValueByPath(xmlProject, "parent.groupId")
    val parentArtifactId = findChildValueByPath(xmlProject, "parent.artifactId")
    if (parentGroupId == null || parentArtifactId == null) {
      problems.add(MavenProjectProblem(file.path, MavenProjectBundle.message("consumer.pom.cannot.determine.parent.version"),
                                       MavenProjectProblem.ProblemType.STRUCTURE,
                                       false))
      return UNKNOWN
    }
    val relativePath = findChildValueByPath(xmlProject, "parent.relativePath", "../pom.xml")
    val parentFile = file.parent.findFileByRelativePath(relativePath!!)
    if (parentFile == null) {
      problems.add(MavenProjectProblem(file.path, MavenProjectBundle.message("consumer.pom.cannot.determine.parent.version"),
                                       MavenProjectProblem.ProblemType.STRUCTURE,
                                       false))
      return UNKNOWN
    }

    val parentXmlProject = readXml(parentFile, problems, MavenProjectProblem.ProblemType.SYNTAX)
    version = findChildValueByPath(parentXmlProject, "version")
    if (version != null) {
      return version
    }
    return calculateParentVersion(parentXmlProject, problems, parentFile, isAutomaticVersionFeatureEnabled)
  }

  private fun repairModelBody(model: MavenModel) {
    val build = model.build

    if (build.finalName.isNullOrBlank()) {
      build.finalName = "\${project.artifactId}-\${project.version}"
    }

    if (build.sources.isEmpty()) build.addSource("src/main/java")
    if (build.testSources.isEmpty()) build.addTestSource("src/test/java")

    build.resources = repairResources(build.resources, "src/main/resources")
    build.testResources = repairResources(build.testResources, "src/test/resources")

    build.directory = if (build.directory.isNullOrBlank()) "target" else build.directory
    build.outputDirectory = if (build.outputDirectory.isNullOrBlank()
    ) "\${project.build.directory}/classes"
    else build.outputDirectory
    build.testOutputDirectory = if (build.testOutputDirectory.isNullOrBlank()
    ) "\${project.build.directory}/test-classes"
    else build.testOutputDirectory
  }

  private fun repairResources(resources: List<MavenResource>, defaultDir: String): List<MavenResource> {
    val result: MutableList<MavenResource> = ArrayList()
    if (resources.isEmpty()) {
      result.add(createResource(defaultDir))
      return result
    }

    for (each in resources) {
      if (each.directory.isNullOrBlank()) continue
      result.add(each)
    }
    return result
  }

  private fun createResource(directory: String): MavenResource {
    return MavenResource(directory, false, null, emptyList(), emptyList())
  }

  private suspend fun collectProfiles(projectFile: VirtualFile,
                                      xmlProject: Element,
                                      problems: MutableCollection<MavenProjectProblem>,
                                      alwaysOnProfiles: MutableSet<String>): List<MavenProfile> {
    val result: MutableList<MavenProfile> = ArrayList()
    collectProfiles(findChildrenByPath(xmlProject, "profiles", "profile"), result, MavenConstants.PROFILE_FROM_POM)

    val profilesFile = MavenUtil.findProfilesXmlFile(projectFile)
    if (profilesFile != null) {
      collectProfilesFromSettingsXmlOrProfilesXml(profilesFile,
                                                  "profilesXml",
                                                  true,
                                                  MavenConstants.PROFILE_FROM_PROFILES_XML,
                                                  result,
                                                  alwaysOnProfiles,
                                                  problems)
    }

    return result
  }

  private suspend fun collectProfilesFromSettingsXmlOrProfilesXml(profilesFile: VirtualFile,
                                                                  rootElementName: String,
                                                                  wrapRootIfNecessary: Boolean,
                                                                  profilesSource: String,
                                                                  result: MutableList<MavenProfile>,
                                                                  alwaysOnProfiles: MutableSet<String>,
                                                                  problems: MutableCollection<MavenProjectProblem>) {
    var rootElement = readXml(profilesFile, problems, MavenProjectProblem.ProblemType.SETTINGS_OR_PROFILES)
    if (rootElement == null) return

    if (wrapRootIfNecessary && rootElementName != rootElement.name) {
      val wrapper = Element(rootElementName)
      wrapper.addContent(rootElement)
      rootElement = wrapper
    }

    val xmlProfiles = findChildrenByPath(rootElement, "profiles", "profile")
    collectProfiles(xmlProfiles, result, profilesSource)

    alwaysOnProfiles.addAll(findChildrenValuesByPath(rootElement, "activeProfiles", "activeProfile"))
  }

  private fun collectProfiles(xmlProfiles: List<Element>, result: MutableList<MavenProfile>, source: String) {
    for (each in xmlProfiles) {
      val id = findChildValueByPath(each, "id")
      if (id.isNullOrBlank()) continue

      val profile = MavenProfile(id, source)
      if (!addProfileIfDoesNotExist(profile, result)) continue

      val xmlActivation = findChildByPath(each, "activation")
      if (xmlActivation != null) {
        val activation = MavenProfileActivation()
        activation.isActiveByDefault = "true" == findChildValueByPath(xmlActivation, "activeByDefault")

        val xmlOS = findChildByPath(xmlActivation, "os")
        if (xmlOS != null) {
          activation.os = MavenProfileActivationOS(
            findChildValueByPath(xmlOS, "name"),
            findChildValueByPath(xmlOS, "family"),
            findChildValueByPath(xmlOS, "arch"),
            findChildValueByPath(xmlOS, "version"))
        }

        activation.jdk = findChildValueByPath(xmlActivation, "jdk")

        val xmlProperty = findChildByPath(xmlActivation, "property")
        if (xmlProperty != null) {
          activation.property = MavenProfileActivationProperty(
            findChildValueByPath(xmlProperty, "name"),
            findChildValueByPath(xmlProperty, "value"))
        }

        val xmlFile = findChildByPath(xmlActivation, "file")
        if (xmlFile != null) {
          activation.file = MavenProfileActivationFile(
            findChildValueByPath(xmlFile, "exists"),
            findChildValueByPath(xmlFile, "missing"))
        }

        profile.activation = activation
      }

      readModelBody(profile, profile.build, each)
    }
  }

  private fun addProfileIfDoesNotExist(profile: MavenProfile, result: MutableList<MavenProfile>): Boolean {
    for (each in result) {
      if (each.id == profile.id) return false
    }
    result.add(profile)
    return true
  }

  private fun readModelBody(mavenModelBase: MavenModelBase, mavenBuildBase: MavenBuildBase, xmlModel: Element) {
    mavenModelBase.modules = findChildrenValuesByPath(xmlModel, "modules", "module")
    collectProperties(findChildByPath(xmlModel, "properties"), mavenModelBase)

    val xmlBuild = findChildByPath(xmlModel, "build")

    mavenBuildBase.finalName = findChildValueByPath(xmlBuild, "finalName")
    mavenBuildBase.defaultGoal = findChildValueByPath(xmlBuild, "defaultGoal")
    mavenBuildBase.directory = findChildValueByPath(xmlBuild, "directory")
    mavenBuildBase.resources = collectResources(
      findChildrenByPath(xmlBuild, "resources", "resource"))
    mavenBuildBase.testResources = collectResources(
      findChildrenByPath(xmlBuild, "testResources", "testResource"))
    mavenBuildBase.filters = findChildrenValuesByPath(xmlBuild, "filters", "filter")

    if (mavenBuildBase is MavenBuild) {
      val source = findChildValueByPath(xmlBuild, "sourceDirectory")
      if (!source.isNullOrBlank()) mavenBuildBase.addSource(source)
      val testSource = findChildValueByPath(xmlBuild, "testSourceDirectory")
      if (!testSource.isNullOrBlank()) mavenBuildBase.addTestSource(testSource)

      mavenBuildBase.outputDirectory = findChildValueByPath(xmlBuild, "outputDirectory")
      mavenBuildBase.testOutputDirectory = findChildValueByPath(xmlBuild, "testOutputDirectory")
    }
  }

  private fun collectResources(xmlResources: List<Element>): List<MavenResource> {
    val result: MutableList<MavenResource> = ArrayList()
    for (each in xmlResources) {
      val directory = findChildValueByPath(each, "directory")
      val filtered = "true" == findChildValueByPath(each, "filtering")
      val targetPath = findChildValueByPath(each, "targetPath")
      val includes = findChildrenValuesByPath(each, "includes", "include")
      val excludes = findChildrenValuesByPath(each, "excludes", "exclude")

      if (null == directory) continue

      result.add(MavenResource(directory, filtered, targetPath, includes, excludes))
    }
    return result
  }

  private fun collectProperties(xmlProperties: Element?, mavenModelBase: MavenModelBase) {
    if (xmlProperties == null) return

    val props = mavenModelBase.properties

    for (each in xmlProperties.children) {
      val name = each.name
      val value = each.textTrim
      if (!props.containsKey(name) && !name.isNullOrBlank()) {
        props.setProperty(name, value)
      }
    }
  }

  private suspend fun readXml(file: VirtualFile,
                              problems: MutableCollection<MavenProjectProblem>,
                              type: MavenProjectProblem.ProblemType): Element? {
    ReadStatisticsCollector.getInstance().fileRead(file)

    return MavenJDOMUtil.read(file, object : MavenJDOMUtil.ErrorHandler {
      override fun onReadError(e: IOException?) {
        MavenLog.LOG.warn("Cannot read the pom file: $e")
        problems.add(MavenProjectProblem.createProblem(file.path, e!!.message, type, false))
      }

      override fun onSyntaxError() {
        problems.add(MavenProjectProblem.createSyntaxProblem(file.path, type))
      }
    })
  }
}
