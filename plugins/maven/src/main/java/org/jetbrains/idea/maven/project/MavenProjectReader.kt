// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils.DEFAULT_RELATIVE_PATH
import org.jetbrains.idea.maven.dom.MavenDomUtil.isAtLeastMaven4
import org.jetbrains.idea.maven.dom.converters.MavenConsumerPomUtil.isAutomaticVersionFeatureEnabled
import org.jetbrains.idea.maven.internal.ReadStatisticsCollector
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.model.MavenConstants.MODEL_VERSION_4_0_0
import org.jetbrains.idea.maven.telemetry.tracer
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildValueByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildrenByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildrenValuesByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.hasChildByPath
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

private const val UNKNOWN = MavenId.UNKNOWN_VALUE

@ApiStatus.Internal
class MavenProjectReader(
  private val myProject: Project,
  private val mavenEmbedderWrappers: MavenEmbedderWrappers,
  val generalSettings: MavenGeneralSettings,
  val explicitProfiles: MavenExplicitProfiles,
  private val locator: MavenProjectReaderProjectLocator,
) {
  private val myCache = MavenReadProjectCache()
  private val myReadHelper: MavenProjectModelReadHelper = MavenUtil.createModelReadHelper(myProject)
  private val mySettingsProfilesCache: AtomicReference<SettingsProfilesCache?> = AtomicReference(null)

  suspend fun readProjectAsync(file: VirtualFile): MavenProjectReaderResult {
    val recursionGuard: MutableSet<VirtualFile> = HashSet()
    val readResult = readProjectModel(file, recursionGuard)
    val model = readResult.first.model

    val modelMap: MutableMap<String, String> = HashMap()
    val mavenId = model.mavenId
    val build = model.build
    mavenId.groupId?.let { modelMap["groupId"] = it }
    mavenId.artifactId?.let { modelMap["artifactId"] = it }
    mavenId.version?.let { modelMap["version"] = it }
    build.outputDirectory?.let { modelMap["build.outputDirectory"] = it }
    build.testOutputDirectory?.let { modelMap["build.testOutputDirectory"] = it }
    build.finalName?.let { modelMap["build.finalName"] = it }
    build.directory?.let { modelMap["build.directory"] = it }

    return MavenProjectReaderResult(model,
                                    modelMap,
                                    readResult.second,
                                    readResult.first.problems)
  }

  private suspend fun readProjectModel(file: VirtualFile, recursionGuard: MutableSet<VirtualFile>): Pair<RawModelReadResult, MavenExplicitProfiles> {
    var cachedModelReadResult = myCache[file]
    if (cachedModelReadResult == null) {
      cachedModelReadResult = doReadProjectModel(myProject, file, false)
      myCache.put(file, cachedModelReadResult)
    }

    // todo modifying cached model and problems here??????
    val modelFromCache = cachedModelReadResult.model
    val alwaysOnProfiles = cachedModelReadResult.alwaysOnProfiles
    val problems = cachedModelReadResult.problems

    val modelWithInheritance = resolveInheritance(
      modelFromCache,
      file,
      problems,
      recursionGuard)

    addSettingsProfiles(file, modelWithInheritance, alwaysOnProfiles, problems)

    repairModelBody(modelWithInheritance)

    return Pair.create(RawModelReadResult(modelWithInheritance, problems, alwaysOnProfiles), MavenExplicitProfiles.NONE)
  }

  private suspend fun doReadProjectModel(project: Project, file: VirtualFile, headerOnly: Boolean): RawModelReadResult {
    val problems = LinkedHashSet<MavenProjectProblem>()
    val alwaysOnProfiles: MutableSet<String> = HashSet()

    val fileExtension = file.extension
    if (!"pom".equals(fileExtension, ignoreCase = true) && !"xml".equals(fileExtension, ignoreCase = true)) {
      return tracer.spanBuilder("readProjectModelUsingMavenServer").useWithScope { readProjectModelUsingMavenServer(file, problems, alwaysOnProfiles) }
    }

    return readMavenProjectModel(file, headerOnly, problems, alwaysOnProfiles, isAutomaticVersionFeatureEnabled(file, project))
  }

  private suspend fun readProjectModelUsingMavenServer(
    file: VirtualFile,
    problems: MutableCollection<MavenProjectProblem>,
    alwaysOnProfiles: MutableSet<String>,
  ): RawModelReadResult {
    var result: MavenModel? = null
    val baseDir = MavenUtil.getBaseDir(file)
    val embedder = mavenEmbedderWrappers.getEmbedder(baseDir)
    result = tracer.spanBuilder("readWithEmbedder").useWithScope { embedder.readModel(VfsUtilCore.virtualToIoFile(file)) }
    if (result == null) {
      result = MavenModel()
      result.packaging = MavenConstants.TYPE_JAR
    }
    return RawModelReadResult(result, problems, alwaysOnProfiles)
  }

  private suspend fun readMavenProjectModel(
    file: VirtualFile,
    headerOnly: Boolean,
    problems: MutableCollection<MavenProjectProblem>,
    alwaysOnProfiles: MutableSet<String>,
    isAutomaticVersionFeatureEnabled: Boolean,
  ): RawModelReadResult {
    val result = MavenModel()
    val xmlProject = readXml(file, problems, MavenProjectProblem.ProblemType.SYNTAX)
    if (xmlProject == null || "project" != xmlProject.name) {
      MavenLog.LOG.warn("Invalid Maven project model: project '$xmlProject', name '${xmlProject?.name}', file ${file.path}")
      result.packaging = MavenConstants.TYPE_JAR
      return RawModelReadResult(result, problems, alwaysOnProfiles)
    }

    val parent: MavenParent
    if (hasChildByPath(xmlProject, "parent")) {
      val parentGroupId = findParentGroupId(file, xmlProject)
      val parentArtifactId = findParentArtifactId(file, xmlProject)
      parent = MavenParent(MavenId(parentGroupId,
                                   parentArtifactId,
                                   calculateParentVersion(xmlProject, problems, file, isAutomaticVersionFeatureEnabled)),
                           findChildValueByPath(xmlProject, "parent.relativePath", DEFAULT_RELATIVE_PATH))
      result.parent = parent
      MavenLog.LOG.trace("Parent maven id for $file: $parent")
    }
    else {
      parent = MavenParent(MavenId(UNKNOWN, UNKNOWN, UNKNOWN), DEFAULT_RELATIVE_PATH)
    }

    result.mavenId = MavenId(
      findChildValueByPath(xmlProject, "groupId", parent.mavenId.groupId),
      findChildValueByPath(xmlProject, "artifactId", UNKNOWN),
      findChildValueByPath(xmlProject, "version", parent.mavenId.version))

    if (headerOnly) return RawModelReadResult(result, problems, alwaysOnProfiles)

    result.packaging = findChildValueByPath(xmlProject, "packaging", MavenConstants.TYPE_JAR)
    result.name = findChildValueByPath(xmlProject, "name")

    readModelBody(result, result.build, xmlProject, file)

    result.profiles = collectProfiles(file, xmlProject)
    return RawModelReadResult(result, problems, alwaysOnProfiles)
  }

  private fun readModelBody(mavenModelBase: MavenModelBase, mavenBuildBase: MavenBuildBase, xmlModel: Element, projectFile: VirtualFile) {
    val modelVersion = xmlModel.getModelVersion()
    val modules = if (isMaven4Model(modelVersion)) findSubprojects(xmlModel, projectFile) else findModules(xmlModel)
    mavenModelBase.modules = myReadHelper.filterModules(modules, projectFile)
    collectProperties(findChildByPath(xmlModel, "properties"), mavenModelBase)

    val xmlBuild = findChildByPath(xmlModel, "build")

    mavenBuildBase.finalName = findChildValueByPath(xmlBuild, "finalName")
    mavenBuildBase.defaultGoal = findChildValueByPath(xmlBuild, "defaultGoal")
    mavenBuildBase.directory = findChildValueByPath(xmlBuild, "directory")

    if (isMaven4Model(modelVersion)) {
      mavenBuildBase.mavenSources = collectMavenSources(xmlBuild)
    }
    else {
      mavenBuildBase.resources = collectResources(
        findChildrenByPath(xmlBuild, "resources", "resource"))
      mavenBuildBase.testResources = collectResources(
        findChildrenByPath(xmlBuild, "testResources", "testResource"))
      if (mavenBuildBase is MavenBuild) {
        val source = findChildValueByPath(xmlBuild, "sourceDirectory")
        if (!source.isNullOrBlank()) mavenBuildBase.sources = listOf(source)
        val testSource = findChildValueByPath(xmlBuild, "testSourceDirectory")
        if (!testSource.isNullOrBlank()) mavenBuildBase.testSources = listOf(testSource)
      }
    }


    mavenBuildBase.filters = findChildrenValuesByPath(xmlBuild, "filters", "filter")

    if (mavenBuildBase is MavenBuild) {
      mavenBuildBase.outputDirectory = findChildValueByPath(xmlBuild, "outputDirectory")
      mavenBuildBase.testOutputDirectory = findChildValueByPath(xmlBuild, "testOutputDirectory")
    }
  }

  private fun collectMavenSources(xmlBuild: Element?): List<MavenSource> {
    if (xmlBuild == null) return emptyList()
    val xmlSources = findChildrenByPath(xmlBuild, "sources", "source")
    val result: MutableList<MavenSource> = ArrayList()
    for (each in xmlSources) {

      val targetPath = findChildValueByPath(each, "targetPath")
      val targetVersion = findChildValueByPath(each, "targetVersion")
      val scope = findChildValueByPath(each, "scope") ?: MavenSource.MAIN_SCOPE
      val lang = findChildValueByPath(each, "lang") ?: MavenSource.JAVA_LANG
      val includes = findChildrenValuesByPath(each, "includes", "include")
      val excludes = findChildrenValuesByPath(each, "excludes", "exclude")
      val filtered = "true" == findChildValueByPath(each, "filtering")
      val enabled = "true" == findChildValueByPath(each, "enabled")
      val directory = findChildValueByPath(each, "directory") ?: "src/${scope}/${lang}"
      result.add(MavenSource.fromSourceTag(
        directory,
        includes,
        excludes,
        scope,
        lang,
        targetPath,
        targetVersion,
        filtered,
        enabled
      ))
    }
    return result
  }

  private fun Element.getModelVersion() = this.getChild("modelVersion")?.value

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

  private fun findSubprojects(xmlModel: Element, projectFile: VirtualFile): List<String> {
    val subprojects = findChildrenValuesByPath(xmlModel, "subprojects", "subproject")
    if (!subprojects.isEmpty()) return subprojects

    val modules = findModules(xmlModel)
    if (!modules.isEmpty()) return modules

    if (MavenConstants.TYPE_POM != xmlModel.getChild("packaging")?.value) return emptyList()

    // subprojects discovery
    // see org.apache.maven.internal.impl.model.DefaultModelBuilder.DefaultModelBuilderSession#doReadFileModel
    return projectFile.parent.children.filter { it.hasPomFile() }.map { it.name }
  }

  private fun isMaven4Model(modelVersion: String?): Boolean = modelVersion != null && StringUtil.compareVersionNumbers(modelVersion, MODEL_VERSION_4_0_0) > 0

  private fun findModules(xmlModel: Element): List<String> = findChildrenValuesByPath(xmlModel, "modules", "module")

  private suspend fun resolveInheritance(
    model: MavenModel,
    file: VirtualFile,
    problems: MutableCollection<MavenProjectProblem>,
    recursionGuard: MutableSet<VirtualFile>,
  ): MavenModel {
    if (recursionGuard.contains(file)) {
      problems.add(MavenProjectProblem.createProblem(
        file.path, MavenProjectBundle.message("maven.project.problem.recursiveInheritance"),
        MavenProjectProblem.ProblemType.PARENT,
        true))
      return model
    }
    recursionGuard.add(file)

    try {
      var parentDesc: MavenParentDesc? = null
      val parent = model.parent
      if (parent != null) {
        if (model.mavenId == parent.mavenId) {
          problems.add(MavenProjectProblem.createProblem(
            file.path,
            MavenProjectBundle.message("maven.project.problem.selfInheritance"),
            MavenProjectProblem.ProblemType.PARENT,
            true))
          return model
        }
        parentDesc = MavenParentDesc(parent.mavenId, parent.relativePath)
      }

      val parentModelWithProblems = readRawResult(file, parentDesc, recursionGuard)

      if (parentModelWithProblems == null) return model // no parent or parent not found;

      val parentModel = parentModelWithProblems.second!!.model
      if (!parentModelWithProblems.second!!.problems.isEmpty()) {
        problems.add(MavenProjectProblem.createProblem(
          parentModelWithProblems.first!!.path,
          MavenProjectBundle.message("maven.project.problem.parentHasProblems",
                                     parentModel.mavenId),
          MavenProjectProblem.ProblemType.PARENT,
          true))
      }

      // todo: it is a quick-hack here - we add inherited dummy profiles to correctly collect activated profiles in 'applyProfiles'.
      val profiles = model.profiles
      val parentProfiles = parentModel.profiles
        .filter { !containsProfileId(profiles, it) }
        .map {
          val copyProfile = MavenProfile(it.id, it.source)
          if (it.activation != null) {
            copyProfile.activation = it.activation.clone()
          }
          copyProfile
        }
      if (parentProfiles.isNotEmpty()) {
        model.profiles = profiles + parentProfiles
      }
      return model
    }
    finally {
      recursionGuard.remove(file)
    }
  }

  private suspend fun doProcessParent(parentFile: VirtualFile, recursionGuard: MutableSet<VirtualFile>): Pair<VirtualFile, RawModelReadResult> {
    val result = readProjectModel(parentFile, recursionGuard).first
    return Pair.create(parentFile, result)
  }

  private suspend fun findInLocalRepository(parentDesc: MavenParentDesc, recursionGuard: MutableSet<VirtualFile>): Pair<VirtualFile, RawModelReadResult>? {
    val parentIoFile = MavenArtifactUtil.getArtifactFile(MavenSettingsCache.getInstance(myProject).getEffectiveUserLocalRepo(), parentDesc.parentId, "pom")
    val parentFile = LocalFileSystem.getInstance().findFileByNioFile(parentIoFile)
    if (parentFile != null) {
      return doProcessParent(parentFile, recursionGuard)
    }
    return null
  }

  private suspend fun readRawResult(
    projectFile: VirtualFile,
    parentDesc: MavenParentDesc?,
    recursionGuard: MutableSet<VirtualFile>,
  ): Pair<VirtualFile, RawModelReadResult>? {
    if (parentDesc == null) {
      return null
    }

    val superPom = MavenUtil.resolveSuperPomFile(myProject, projectFile)
    if (superPom == null || projectFile == superPom) return null

    val locatedParentFile = locator.findProjectFile(parentDesc.parentId)
    if (locatedParentFile != null) {
      return doProcessParent(locatedParentFile, recursionGuard)
    }

    if (Strings.isEmpty(parentDesc.parentRelativePath)) {
      val localRepoResult = findInLocalRepository(parentDesc, recursionGuard)
      if (localRepoResult != null) {
        return localRepoResult
      }
    }

    if (projectFile.parent != null) {
      val parentFileCandidate = projectFile.parent.findFileByRelativePath(parentDesc.parentRelativePath)

      val parentFile = if (parentFileCandidate != null && parentFileCandidate.isDirectory)
        parentFileCandidate.findFileByRelativePath(MavenConstants.POM_XML)
      else parentFileCandidate

      if (parentFile != null) {
        val parentModel = doReadProjectModel(myProject, parentFile, true).model
        val parentId = parentDesc.parentId
        val parentResult = if (parentId != parentModel.mavenId) null else doProcessParent(parentFile, recursionGuard)
        if (null != parentResult) {
          return parentResult
        }
      }
    }

    val defaultParentDesc = MavenParentDesc(parentDesc.parentId, DEFAULT_RELATIVE_PATH)
    return findInLocalRepository(defaultParentDesc, recursionGuard)
  }

  private suspend fun addSettingsProfiles(
    projectFile: VirtualFile,
    model: MavenModel,
    alwaysOnProfiles: MutableSet<String>,
    problems: MutableCollection<MavenProjectProblem>,
  ) {
    if (mySettingsProfilesCache.get() == null) {
      val settingsProfiles: MutableList<MavenProfile> = ArrayList()
      val settingsProblems = LinkedHashSet<MavenProjectProblem>()
      val settingsAlwaysOnProfiles: MutableSet<String> = HashSet()

      for (each in MavenSettingsCache.getInstance(myProject).getEffectiveVirtualSettingsFiles()) {
        collectProfilesFromSettingsXml(each,
                                       projectFile,
                                       "settings",
                                       false,
                                       MavenConstants.PROFILE_FROM_SETTINGS_XML,
                                       settingsProfiles,
                                       settingsAlwaysOnProfiles,
                                       settingsProblems)
      }
      mySettingsProfilesCache.set(SettingsProfilesCache(settingsProfiles, settingsAlwaysOnProfiles, settingsProblems))
    }

    val modelProfiles = model.profiles
    val settingsProfiles = mySettingsProfilesCache.get()!!.profiles
      .filter { !containsProfileId(modelProfiles, it) }
    model.profiles = modelProfiles + settingsProfiles

    problems.addAll(mySettingsProfilesCache.get()!!.problems)
    alwaysOnProfiles.addAll(mySettingsProfilesCache.get()!!.alwaysOnProfiles)
  }

  private fun repairModelBody(model: MavenModel) {
    val build = model.build

    if (build.finalName.isNullOrBlank()) {
      build.finalName = "\${project.artifactId}-\${project.version}"
    }

    if (build.sources.isEmpty()) build.sources = listOf("src/main/java")
    if (build.testSources.isEmpty()) build.testSources = listOf("src/test/java")

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
      if (each.directory.isBlank()) continue
      result.add(each)
    }
    return result
  }

  private fun createResource(directory: String): MavenResource {
    return MavenResource(directory, false, null, emptyList(), emptyList())
  }

  private fun collectProfiles(projectFile: VirtualFile, xmlProject: Element): List<MavenProfile> {
    return collectProfiles(findChildrenByPath(xmlProject, "profiles", "profile"), MavenConstants.PROFILE_FROM_POM, projectFile)
  }

  private suspend fun collectProfilesFromSettingsXml(
    profilesFile: VirtualFile,
    projectsFile: VirtualFile,
    rootElementName: String,
    wrapRootIfNecessary: Boolean,
    profilesSource: String,
    result: MutableList<MavenProfile>,
    alwaysOnProfiles: MutableSet<String>,
    problems: MutableCollection<MavenProjectProblem>,
  ) {
    var rootElement = readXml(profilesFile, problems, MavenProjectProblem.ProblemType.SETTINGS_OR_PROFILES)
    if (rootElement == null) return

    if (wrapRootIfNecessary && rootElementName != rootElement.name) {
      val wrapper = Element(rootElementName)
      wrapper.addContent(rootElement)
      rootElement = wrapper
    }

    val xmlProfiles = findChildrenByPath(rootElement, "profiles", "profile")
    result.addAll(collectProfiles(xmlProfiles, profilesSource, projectsFile))

    alwaysOnProfiles.addAll(findChildrenValuesByPath(rootElement, "activeProfiles", "activeProfile"))
  }

  private fun collectProfiles(xmlProfiles: List<Element>, source: String, projectFile: VirtualFile): List<MavenProfile> {
    val result = mutableListOf<MavenProfile>()
    for (each in xmlProfiles) {
      val id = findChildValueByPath(each, "id")
      if (id.isNullOrBlank()) continue

      val profile = MavenProfile(id, source)
      if (containsProfileId(result, profile)) continue

      result.add(profile)

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

      readModelBody(profile, profile.build, each, projectFile)
    }
    return result
  }

  private suspend fun getVersionFromParentPomRecursively(
    file: VirtualFile,
    relativePath: String,
    problems: MutableCollection<MavenProjectProblem>,
  ): String {
    val parentFile = file.findParentPom(relativePath) ?: return UNKNOWN

    val parentXmlProject = readXml(parentFile, problems, MavenProjectProblem.ProblemType.SYNTAX)
    val version = findChildValueByPath(parentXmlProject, "version")
    if (version != null) {
      return version
    }
    return calculateParentVersion(parentXmlProject, problems, parentFile, isAutomaticVersionFeatureEnabled = true)
  }

  private fun Element.checkParentGroupAndArtefactIdsPresence(file: VirtualFile): Boolean {
    // for Maven 4, <groupId> and <artifactId> are optional if a parent POM is reachable by <relativePath>
    if (isAtLeastMaven4(file, myProject)) return true
    // for Maven 3, these tags are required always
    val parentGroupId = findChildValueByPath(this, "parent.groupId")
    val parentArtifactId = findChildValueByPath(this, "parent.artifactId")
    return parentGroupId != null && parentArtifactId != null
  }

  private fun VirtualFile.findParentPom(relativePath: String): VirtualFile? {
    val parentPath = this.parent.findFileByRelativePath(relativePath) ?: return null
    if (parentPath.isDirectory) return parentPath.findFileByRelativePath(MavenConstants.POM_XML)
    return parentPath
  }

  private fun containsProfileId(result: List<MavenProfile>, profile: MavenProfile): Boolean {
    return result.any { it.id == profile.id }
  }

  private fun VirtualFile.hasPomFile(): Boolean {
    return this.isDirectory && this.children.any { it.name == MavenConstants.POM_XML }
  }

  private suspend fun findParentGroupId(file: VirtualFile, xmlProject: Element) = findParentSubtagValue(file, xmlProject, "groupId")

  private suspend fun findParentArtifactId(file: VirtualFile, xmlProject: Element) = findParentSubtagValue(file, xmlProject, "artifactId")

  private suspend fun findParentSubtagValue(file: VirtualFile, xmlProject: Element, path: String): String {
    val explicitParentValue = findChildValueByPath(xmlProject, "parent.$path", null)
    if (null != explicitParentValue) return explicitParentValue

    if (!hasChildByPath(xmlProject, "parent")) return UNKNOWN

    val parentFile = readAction { file.parent.parent.findChild(MavenConstants.POM_XML) }
    if (null == parentFile) {
      MavenLog.LOG.trace("Parent pom for $file not found")
      return UNKNOWN
    }
    val parentXmlProject = readXml(parentFile, mutableListOf(), MavenProjectProblem.ProblemType.SYNTAX) ?: return UNKNOWN
    val explicitValue = findChildValueByPath(parentXmlProject, path, null)
    if (null != explicitValue) return explicitValue

    return findParentSubtagValue(parentFile, parentXmlProject, path)
  }

  private suspend fun calculateParentVersion(
    xmlProject: Element?,
    problems: MutableCollection<MavenProjectProblem>,
    file: VirtualFile,
    isAutomaticVersionFeatureEnabled: Boolean,
  ): String {
    if (xmlProject == null) return UNKNOWN
    findChildValueByPath(xmlProject, "parent.version")?.let { parentVersion ->
      return parentVersion
    }
    if (!isAutomaticVersionFeatureEnabled) return UNKNOWN

    val parentTag = findChildByPath(xmlProject, "parent") ?: return UNKNOWN
    if (!xmlProject.checkParentGroupAndArtefactIdsPresence(file)) return UNKNOWN

    val relativePath = findChildValueByPath(parentTag, "relativePath", DEFAULT_RELATIVE_PATH)!!
    return getVersionFromParentPomRecursively(file, relativePath, problems)
  }

  private suspend fun readXml(
    file: VirtualFile,
    problems: MutableCollection<MavenProjectProblem>,
    type: MavenProjectProblem.ProblemType,
  ): Element? {
    ReadStatisticsCollector.getInstance().fileRead(file)

    return MavenJDOMUtil.read(file, object : MavenJDOMUtil.ErrorHandler {
      override fun onReadError(e: IOException?) {
        MavenLog.LOG.warn("Cannot read the pom file: $e")
        problems.add(MavenProjectProblem.createProblem(file.path, e!!.message, type, true))
      }

      override fun onSyntaxError(message: String, startOffset: Int, endOffset: Int) {
        problems.add(MavenProjectProblem.createSyntaxProblem(file.path, type))
      }
    })
  }

  private class SettingsProfilesCache(
    val profiles: List<MavenProfile>,
    val alwaysOnProfiles: Set<String>,
    val problems: Collection<MavenProjectProblem>,
  )

  class RawModelReadResult(
    var model: MavenModel,
    var problems: MutableCollection<MavenProjectProblem>,
    var alwaysOnProfiles: MutableSet<String>,
  )
}
