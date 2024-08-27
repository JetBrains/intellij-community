// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.plugins.api.MavenModelPropertiesPatcher
import org.jetbrains.idea.maven.utils.MavenArtifactUtil.hasArtifactFile
import org.jetbrains.idea.maven.utils.MavenPathWrapper
import java.io.File
import java.io.Serializable
import java.util.*
import java.util.function.Predicate
import kotlin.concurrent.Volatile

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

  var sources: List<String>? = null
    private set

  var testSources: List<String>? = null
    private set

  var resources: List<MavenResource>? = null
    private set

  var testResources: List<MavenResource>? = null
    private set

  var filters: List<String>? = null
    private set

  var properties: Properties? = null
    private set

  private var myPlugins: MutableList<MavenPlugin>? = null
  private var myExtensions: List<MavenArtifact>? = null

  var dependencies: List<MavenArtifact>? = null
    private set

  var dependencyTree: List<MavenArtifactNode>? = null
    private set

  var remoteRepositories: List<MavenRemoteRepository>? = null
    private set

  var annotationProcessors: List<MavenArtifact>? = null
    private set

  var modulesPathsAndNames: Map<String, String>? = null
    private set

  var modelMap: Map<String, String?>? = null
    private set

  var profilesIds: Collection<String> = emptySet()
    private set

  var activatedProfilesIds: MavenExplicitProfiles? = null
    private set

  var dependencyHash: String? = null

  private var myReadingProblems: Collection<MavenProjectProblem>? = null
  private var myUnresolvedArtifactIds: Set<MavenId?>? = null

  var localRepository: File? = null
    private set

  @Volatile
  var problemsCache: List<MavenProjectProblem>? = null
    private set

  @Volatile
  private var myUnresolvedDependenciesCache: List<MavenArtifact>? = null

  @Volatile
  private var myUnresolvedPluginsCache: List<MavenPlugin>? = null

  @Volatile
  private var myUnresolvedExtensionsCache: List<MavenArtifact>? = null

  @Volatile
  private var myUnresolvedAnnotationProcessors: List<MavenArtifact>? = null

  val plugins: List<MavenPlugin>?
    get() = myPlugins

  var readingProblems: Collection<MavenProjectProblem>?
    get() = myReadingProblems
    set(readingProblems) {
      this.myReadingProblems = readingProblems
    }

  public override fun clone(): MavenProjectState {
    try {
      val result = super.clone() as MavenProjectState
      result.resetCache()
      return result
    }
    catch (e: CloneNotSupportedException) {
      throw RuntimeException(e)
    }
  }

  fun resetCache() {
    problemsCache = null
    myUnresolvedDependenciesCache = null
    myUnresolvedPluginsCache = null
    myUnresolvedExtensionsCache = null
    myUnresolvedAnnotationProcessors = null
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

    result.setHasDependencyChanges(repositoryChanged || !Comparing.equal(
      dependencies, newState.dependencies))
    result.setHasPluginChanges(repositoryChanged || !Comparing.equal<List<MavenPlugin>?>(myPlugins, newState.myPlugins))
    result.setHasPropertyChanges(!Comparing.equal(properties, newState.properties))
    return result
  }

  fun doUpdateState(
    model: MavenModel,
    readingProblems: Collection<MavenProjectProblem>,
    activatedProfiles: MavenExplicitProfiles,
    unresolvedArtifactIds: Set<MavenId?>,
    nativeModelMap: Map<String, String?>,
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
    unresolvedArtifactIds: Set<MavenId?>,
    keepPreviousArtifacts: Boolean,
    keepPreviousPlugins: Boolean,
  ) {
    val newUnresolvedArtifacts: MutableSet<MavenId?> = HashSet()
    val newRepositories = LinkedHashSet<MavenRemoteRepository>()
    val newDependencies = LinkedHashSet<MavenArtifact>()
    val newDependencyTree = LinkedHashSet<MavenArtifactNode>()
    val newPlugins = LinkedHashSet<MavenPlugin>()
    val newExtensions = LinkedHashSet<MavenArtifact>()
    val newAnnotationProcessors = LinkedHashSet<MavenArtifact>()

    if (keepPreviousArtifacts) {
      if (myUnresolvedArtifactIds != null) newUnresolvedArtifacts.addAll(myUnresolvedArtifactIds!!)
      if (remoteRepositories != null) newRepositories.addAll(remoteRepositories!!)
      if (dependencies != null) newDependencies.addAll(dependencies!!)
      if (dependencyTree != null) newDependencyTree.addAll(dependencyTree!!)
      if (myExtensions != null) newExtensions.addAll(myExtensions!!)
      if (annotationProcessors != null) newAnnotationProcessors.addAll(annotationProcessors!!)
    }

    if (keepPreviousPlugins) {
      if (myPlugins != null) newPlugins.addAll(myPlugins!!)
    }

    newUnresolvedArtifacts.addAll(unresolvedArtifactIds)
    newRepositories.addAll(model.remoteRepositories)
    newDependencyTree.addAll(model.dependencyTree)
    newDependencies.addAll(model.dependencies)
    newPlugins.addAll(model.plugins)
    newExtensions.addAll(model.extensions)

    myUnresolvedArtifactIds = newUnresolvedArtifacts
    remoteRepositories = ArrayList(newRepositories)
    dependencies = ArrayList(newDependencies)
    dependencyTree = ArrayList(newDependencyTree)
    myPlugins = ArrayList(newPlugins)
    myExtensions = ArrayList(newExtensions)
    annotationProcessors = ArrayList(newAnnotationProcessors)
  }

  fun incLastReadStamp() {
    lastReadStamp++
  }

  private fun doSetFolders(build: MavenBuild) {
    doSetFolders(build.sources, build.testSources, build.resources, build.testResources)
  }

  fun doSetFolders(
    sources: List<String>?,
    testSources: List<String>?,
    resources: List<MavenResource>?,
    testResources: List<MavenResource>?,
  ) {
    this.sources = sources
    this.testSources = testSources

    this.resources = resources
    this.testResources = testResources
  }

  private fun doCollectProblems(file: VirtualFile, fileExistsPredicate: Predicate<File>?): List<MavenProjectProblem> {
    val result: MutableList<MavenProjectProblem> = ArrayList()

    validateParent(file, result)
    result.addAll(myReadingProblems!!)

    for ((key, value) in modulesPathsAndNames!!) {
      if (LocalFileSystem.getInstance().findFileByPath(key) == null) {
        result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.moduleNotFound",
                                                                            value)))
      }
    }

    validateDependencies(file, result, fileExistsPredicate)
    validateExtensions(file, result)
    validatePlugins(file, result)

    return result
  }

  private fun validateParent(file: VirtualFile, result: MutableList<MavenProjectProblem>) {
    if (!isParentResolved) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.parentNotFound",
                                                                          parentId)))
    }
  }

  private fun validateDependencies(
    file: VirtualFile,
    result: MutableList<MavenProjectProblem>,
    fileExistsPredicate: Predicate<File>?,
  ) {
    for (each in getUnresolvedDependencies(fileExistsPredicate)) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedDependency",
                                                                          each.displayStringWithType)))
    }
  }

  private fun validateExtensions(file: VirtualFile, result: MutableList<MavenProjectProblem>) {
    for (each in unresolvedExtensions) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedExtension",
                                                                          each.displayStringSimple)))
    }
  }

  private fun validatePlugins(file: VirtualFile, result: MutableList<MavenProjectProblem>) {
    for (each in unresolvedPlugins) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedPlugin", each)))
    }
  }

  private val isParentResolved: Boolean
    get() = !myUnresolvedArtifactIds!!.contains(parentId)

  fun hasUnresolvedArtifacts(): Boolean {
    return !isParentResolved
           || !getUnresolvedDependencies(null).isEmpty()
           || !unresolvedExtensions.isEmpty()
           || !unresolvedAnnotationProcessors.isEmpty()
  }

  private fun getUnresolvedDependencies(fileExistsPredicate: Predicate<File>?): List<MavenArtifact> {
    synchronized(this) {
      if (myUnresolvedDependenciesCache == null) {
        val result: MutableList<MavenArtifact> = ArrayList()
        for (each in dependencies!!) {
          val resolved = each.isResolved(fileExistsPredicate)
          each.isFileUnresolved = !resolved
          if (!resolved) result.add(each)
        }
        myUnresolvedDependenciesCache = result
      }
      return myUnresolvedDependenciesCache!!
    }
  }

  private val unresolvedExtensions: List<MavenArtifact>
    get() {
      synchronized(this) {
        if (myUnresolvedExtensionsCache == null) {
          val result: MutableList<MavenArtifact> = ArrayList()
          for (each in myExtensions!!) {
            // Collect only extensions that were attempted to be resolved.
            // It is because embedder does not even try to resolve extensions that
            // are not necessary.
            if (myUnresolvedArtifactIds!!.contains(each.mavenId)
                && !pomFileExists(localRepository!!, each)
            ) {
              result.add(each)
            }
          }
          myUnresolvedExtensionsCache = result
        }
        return myUnresolvedExtensionsCache!!
      }
    }

  private val unresolvedAnnotationProcessors: List<MavenArtifact>
    get() {
      synchronized(this) {
        if (myUnresolvedAnnotationProcessors == null) {
          val result: MutableList<MavenArtifact> = ArrayList()
          for (each in annotationProcessors!!) {
            if (!each.isResolved) result.add(each)
          }
          myUnresolvedAnnotationProcessors = result
        }
        return myUnresolvedAnnotationProcessors!!
      }
    }

  val unresolvedPlugins: List<MavenPlugin>
    get() {
      synchronized(this) {
        if (myUnresolvedPluginsCache == null) {
          val result: MutableList<MavenPlugin> = ArrayList()
          for (each in declaredPlugins) {
            if (!hasArtifactFile(localRepository!!, each.mavenId)) {
              result.add(each)
            }
          }
          myUnresolvedPluginsCache = result
        }
        return myUnresolvedPluginsCache!!
      }
    }

  val declaredPlugins: List<MavenPlugin> get() = myPlugins?.filter { !it.isDefault } ?: emptyList()

  fun collectProblems(file: VirtualFile, fileExistsPredicate: Predicate<File>?): List<MavenProjectProblem> {
    synchronized(this) {
      if (problemsCache == null) {
        problemsCache = doCollectProblems(file, fileExistsPredicate)
      }
      return problemsCache!!
    }
  }

  fun doUpdateState(
    dependencies: List<MavenArtifact>,
    properties: Properties,
    plugins: List<MavenPlugin>,
  ) {
    this.dependencies = dependencies
    this.properties = properties
    myPlugins!!.clear()
    myPlugins!!.addAll(plugins)
  }

  private fun collectProfilesIds(profiles: Collection<MavenProfile>?): Collection<String> {
    if (profiles == null) return emptyList()

    val result: MutableSet<String> = HashSet(profiles.size)
    for (each in profiles) {
      result.add(each.id)
    }
    return result
  }

  private fun createDependencyProblem(file: VirtualFile, description: String): MavenProjectProblem {
    return MavenProjectProblem(file.path, description, MavenProjectProblem.ProblemType.DEPENDENCY, false)
  }

  private fun pomFileExists(localRepository: File, artifact: MavenArtifact): Boolean {
    return hasArtifactFile(localRepository, artifact.mavenId, "pom")
  }
}
