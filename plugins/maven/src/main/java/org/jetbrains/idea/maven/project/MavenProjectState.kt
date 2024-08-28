// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.plugins.api.MavenModelPropertiesPatcher
import org.jetbrains.idea.maven.utils.MavenPathWrapper
import java.io.File
import java.io.Serializable
import java.util.*

internal class MavenProjectState : Cloneable, Serializable {
  var lastReadStamp: Long = 0
    private set

  var mavenId: MavenId? = null
    private set

  var parentId: MavenId? = null
    private set

  var packaging: String? = null
    private set

  var name: String? = null
    private set

  var finalName: String? = null
    private set

  var defaultGoal: String? = null
    private set

  var buildDirectory: String? = null
    private set

  var outputDirectory: String? = null
    private set

  var testOutputDirectory: String? = null
    private set

  var sources: List<String> = emptyList()
    private set

  var testSources: List<String> = emptyList()
    private set

  var resources: List<MavenResource> = emptyList()
    private set

  var testResources: List<MavenResource> = emptyList()
    private set

  var filters: List<String> = emptyList()
    private set

  var properties: Properties? = null
    private set

  private var myPlugins: List<MavenPlugin> = emptyList()

  var extensions: List<MavenArtifact> = emptyList()
    private set

  var dependencies: List<MavenArtifact> = emptyList()
    private set

  var dependencyTree: List<MavenArtifactNode> = emptyList()
    private set

  var remoteRepositories: List<MavenRemoteRepository> = emptyList()
    private set

  var annotationProcessors: List<MavenArtifact> = emptyList()
    private set

  var modulesPathsAndNames: Map<String, String> = emptyMap()
    private set

  var modelMap: Map<String, String> = emptyMap()
    private set

  var profilesIds: Collection<String> = emptySet()
    private set

  var activatedProfilesIds: MavenExplicitProfiles = MavenExplicitProfiles.NONE
    private set

  var dependencyHash: String? = null

  private var myReadingProblems: Collection<MavenProjectProblem> = emptySet()

  var unresolvedArtifactIds: Set<MavenId> = emptySet()
    private set

  var localRepository: File? = null
    private set

  val plugins: List<MavenPlugin>
    get() = myPlugins

  var readingProblems: Collection<MavenProjectProblem>
    get() = myReadingProblems
    set(readingProblems) {
      this.myReadingProblems = readingProblems
    }

  public override fun clone(): MavenProjectState {
    try {
      val result = super.clone() as MavenProjectState
      return result
    }
    catch (e: CloneNotSupportedException) {
      throw RuntimeException(e)
    }
  }

  fun getChanges(newState: MavenProjectState): MavenProjectChanges {
    if (lastReadStamp == 0L) return MavenProjectChanges.ALL

    val result = MavenProjectChangesBuilder()

    result.setHasPackagingChanges(packaging != newState.packaging)

    result.setHasOutputChanges(
      finalName != newState.finalName || buildDirectory != newState.buildDirectory || outputDirectory != newState.outputDirectory || testOutputDirectory != newState.testOutputDirectory)

    result.setHasSourceChanges(!Comparing.equal(sources, newState.sources)
                               || !Comparing.equal(testSources, newState.testSources)
                               || !Comparing.equal(resources, newState.resources)
                               || !Comparing.equal(testResources, newState.testResources))

    val repositoryChanged = !Comparing.equal(localRepository, newState.localRepository)

    result.setHasDependencyChanges(repositoryChanged || !Comparing.equal(dependencies, newState.dependencies))
    result.setHasPluginChanges(repositoryChanged || !Comparing.equal<List<MavenPlugin>?>(myPlugins, newState.myPlugins))
    result.setHasPropertyChanges(!Comparing.equal(properties, newState.properties))
    return result
  }

  fun doUpdateState(
    model: MavenModel,
    readingProblems: Collection<MavenProjectProblem>,
    activatedProfiles: MavenExplicitProfiles,
    unresolvedArtifactIds: Set<MavenId>,
    nativeModelMap: Map<String, String>,
    settings: MavenGeneralSettings,
    keepPreviousArtifacts: Boolean,
    keepPreviousProfiles: Boolean,
    keepPreviousPlugins: Boolean,
    directory: String,
    fileExtension: String?,
  ) {
    myReadingProblems = readingProblems
    localRepository = settings.effectiveLocalRepository
    activatedProfilesIds = activatedProfiles

    mavenId = model.mavenId
    if (model.parent != null) {
      parentId = model.parent.mavenId
    }

    packaging = model.packaging
    name = model.name

    finalName = model.build.finalName
    defaultGoal = model.build.defaultGoal

    buildDirectory = model.build.directory
    outputDirectory = model.build.outputDirectory
    testOutputDirectory = model.build.testOutputDirectory

    doSetFolders(model.build)

    filters = model.build.filters
    properties = model.properties

    doSetResolvedAttributes(model, unresolvedArtifactIds, keepPreviousArtifacts, keepPreviousPlugins)

    MavenModelPropertiesPatcher.patch(properties, myPlugins)

    modulesPathsAndNames = collectModulePathsAndNames(model, directory, fileExtension)
    profilesIds = collectProfilesIds(model.profiles) + if (keepPreviousProfiles) profilesIds else emptySet()

    modelMap = nativeModelMap
  }

  private fun collectModulePathsAndNames(mavenModel: MavenModel, baseDir: String, fileExtension: String?): Map<String, String> {
    val basePath = "$baseDir/"
    val result: MutableMap<String, String> = LinkedHashMap()
    for ((key, value) in collectModulesRelativePathsAndNames(mavenModel, basePath, fileExtension)) {
      result[MavenPathWrapper(basePath + key).path] = value
    }
    return result
  }

  private fun collectModulesRelativePathsAndNames(mavenModel: MavenModel, basePath: String, fileExtension: String?): Map<String, String> {
    val extension = fileExtension ?: ""
    val result = LinkedHashMap<String, String>()
    for (module in mavenModel.modules) {
      var name = module
      name = name.trim { it <= ' ' }

      if (name.isEmpty()) continue

      val originalName = name

      // module name can be relative and contain either / of \\ separators
      name = FileUtil.toSystemIndependentName(name)

      val finalName = name
      val fullPathInModuleName = MavenConstants.POM_EXTENSIONS.any { finalName.endsWith(".$it") }
      if (!fullPathInModuleName) {
        if (!name.endsWith("/")) name += "/"
        name += MavenConstants.POM_EXTENSION + '.' + extension
      }
      else {
        val systemDependentName = FileUtil.toSystemDependentName(basePath + name)
        if (File(systemDependentName).isDirectory) {
          name += "/" + MavenConstants.POM_XML
        }
      }

      result[name] = originalName
    }
    return result
  }

  private fun doSetResolvedAttributes(
    model: MavenModel,
    unresolvedArtifactIds: Set<MavenId>,
    keepPreviousArtifacts: Boolean,
    keepPreviousPlugins: Boolean,
  ) {
    val newUnresolvedArtifacts: MutableSet<MavenId> = HashSet()
    val newRepositories = LinkedHashSet<MavenRemoteRepository>()
    val newDependencies = LinkedHashSet<MavenArtifact>()
    val newDependencyTree = LinkedHashSet<MavenArtifactNode>()
    val newPlugins = LinkedHashSet<MavenPlugin>()
    val newExtensions = LinkedHashSet<MavenArtifact>()
    val newAnnotationProcessors = LinkedHashSet<MavenArtifact>()

    if (keepPreviousArtifacts) {
      newUnresolvedArtifacts.addAll(this.unresolvedArtifactIds)
      newRepositories.addAll(remoteRepositories)
      newDependencies.addAll(dependencies)
      newDependencyTree.addAll(dependencyTree)
      newExtensions.addAll(extensions)
      newAnnotationProcessors.addAll(annotationProcessors)
    }

    if (keepPreviousPlugins) {
      newPlugins.addAll(myPlugins)
    }

    newUnresolvedArtifacts.addAll(unresolvedArtifactIds)
    newRepositories.addAll(model.remoteRepositories)
    newDependencyTree.addAll(model.dependencyTree)
    newDependencies.addAll(model.dependencies)
    newPlugins.addAll(model.plugins)
    newExtensions.addAll(model.extensions)

    this.unresolvedArtifactIds = newUnresolvedArtifacts
    remoteRepositories = ArrayList(newRepositories)
    dependencies = ArrayList(newDependencies)
    dependencyTree = ArrayList(newDependencyTree)
    myPlugins = ArrayList(newPlugins)
    extensions = ArrayList(newExtensions)
    annotationProcessors = ArrayList(newAnnotationProcessors)
  }

  fun incLastReadStamp() {
    lastReadStamp++
  }

  private fun doSetFolders(build: MavenBuild) {
    doSetFolders(build.sources, build.testSources, build.resources, build.testResources)
  }

  fun doSetFolders(
    sources: List<String>,
    testSources: List<String>,
    resources: List<MavenResource>,
    testResources: List<MavenResource>,
  ) {
    this.sources = sources
    this.testSources = testSources

    this.resources = resources
    this.testResources = testResources
  }

  val isParentResolved: Boolean
    get() = !unresolvedArtifactIds.contains(parentId)

  val declaredPlugins: List<MavenPlugin> get() = myPlugins.filter { !it.isDefault }

  fun doUpdateState(
    dependencies: List<MavenArtifact>,
    properties: Properties,
    plugins: List<MavenPlugin>,
  ) {
    this.dependencies = dependencies
    this.properties = properties
    myPlugins = plugins
  }

  private fun collectProfilesIds(profiles: Collection<MavenProfile>?): Collection<String> {
    if (profiles == null) return emptyList()

    val result: MutableSet<String> = HashSet(profiles.size)
    for (each in profiles) {
      result.add(each.id)
    }
    return result
  }

  fun addDependencies(dependencies: Collection<MavenArtifact>) {
    this.dependencies += dependencies
  }

  fun addAnnotationProcessors(annotationProcessors: Collection<MavenArtifact>) {
    this.annotationProcessors += annotationProcessors
  }
}