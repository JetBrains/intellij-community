// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.MavenPropertyResolver
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.plugins.api.MavenModelPropertiesPatcher
import org.jetbrains.idea.maven.server.MavenGoalExecutionResult
import org.jetbrains.idea.maven.utils.MavenArtifactUtil.hasArtifactFile
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenPathWrapper
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.*
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

class MavenProject(val file: VirtualFile) {
  enum class ConfigFileKind(val myRelativeFilePath: String, val myValueIfMissing: String) {
    MAVEN_CONFIG(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "true"),
    JVM_CONFIG(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "");

    val CACHE_KEY: Key<Map<String, String>> = Key.create("MavenProject.$name")
  }

  @Volatile
  private var myState: MavenProjectState = MavenProjectState()

  private val cache = ConcurrentHashMap<Key<*>, Any>()
  private val stateCache = ConcurrentHashMap<Key<*>, Any>()

  enum class ProcMode {
    BOTH, ONLY, NONE
  }

  @Throws(IOException::class)
  fun write(out: DataOutputStream) {
    try {
      out.writeUTF(path)

      BufferExposingByteArrayOutputStream().use { bs ->
        ObjectOutputStream(bs).use { os ->
          os.writeObject(myState)
          out.writeInt(bs.size())
          out.write(bs.internalBuffer, 0, bs.size())
        }
      }
    }
    catch (e: IOException) {
      MavenLog.LOG.error("Unable to write project " + file.path, e)
      throw e
    }
  }

  @Internal
  fun updateFromReaderResult(
    readerResult: MavenProjectReaderResult,
    effectiveRepositoryPath: Path,
    keepPreviousArtifacts: Boolean,
  ): MavenProjectChanges {
    val keepPreviousPlugins = keepPreviousArtifacts

    val newState = doUpdateState(
      myState,
      true,
      readerResult.mavenModel,
      emptyList(),
      readerResult.readingProblems,
      readerResult.activatedProfiles,
      setOf(),
      readerResult.nativeModelMap,
      effectiveRepositoryPath,
      keepPreviousArtifacts,
      false,
      keepPreviousPlugins,
      directory,
      file.extension,
      null
    )

    return setState(newState)
  }

  @Internal
  fun updateState(
    model: MavenModel,
    managedDependencies: List<MavenId>,
    dependencyHash: String?,
    readingProblems: Collection<MavenProjectProblem>,
    activatedProfiles: MavenExplicitProfiles,
    unresolvedArtifactIds: Set<MavenId>,
    nativeModelMap: Map<String, String>,
    effectiveRepositoryPath: Path,
    keepPreviousArtifacts: Boolean,
  ): MavenProjectChanges {
    val newState = doUpdateState(
      myState,
      false,
      model,
      managedDependencies,
      readingProblems,
      activatedProfiles,
      unresolvedArtifactIds,
      nativeModelMap,
      effectiveRepositoryPath,
      keepPreviousArtifacts,
      true,
      false,
      directory,
      file.extension,
      dependencyHash
    )

    return setState(newState)
  }

  @Internal
  fun updateState(dependencies: List<MavenArtifact>, properties: Properties, pluginInfos: List<MavenPluginWithArtifact>): MavenProjectChanges {
    val newState = myState.copy(
      dependencies = dependencies,
      properties = properties,
      pluginInfos = pluginInfos
    )
    return setState(newState)
  }

  @Internal
  fun updateState(readingProblems: Collection<MavenProjectProblem>): MavenProjectChanges {
    val newState = myState.copy(readingProblems = readingProblems)
    return setState(newState)
  }

  @Internal
  fun updatePluginArtifacts(pluginIdsToArtifacts: Map<MavenId, MavenArtifact?>) {
    val newPluginInfos = myState.pluginInfos.map { MavenPluginWithArtifact(it.plugin, pluginIdsToArtifacts[it.plugin.mavenId]) }
    val newState = myState.copy(pluginInfos = newPluginInfos)
    setState(newState)
  }

  @Internal
  fun updateMavenId(newMavenId: MavenId) {
    setState(myState.copy(mavenId = newMavenId))
  }

  private fun setState(newState: MavenProjectState): MavenProjectChanges {
    val changes: MavenProjectChanges = myState.getChanges(newState)
    doSetState(newState)
    return changes
  }

  private fun doSetState(state: MavenProjectState) {
    myState = state
    resetStateCache()
  }

  class Snapshot internal constructor(private val myState: MavenProjectState) {
    internal fun getChanges(newState: MavenProjectState): MavenProjectChanges {
      return myState.getChanges(newState)
    }
  }

  val snapshot: Snapshot
    get() = Snapshot(myState)

  fun getChangesSinceSnapshot(snapshot: Snapshot): MavenProjectChanges {
    return snapshot.getChanges(myState)
  }

  @Internal
  fun setFolders(folders: MavenGoalExecutionResult.Folders): MavenProjectChanges {
    val newState = myState.copy(
      sources = folders.sources,
      testSources = folders.testSources,
      resources = folders.resources,
      testResources = folders.testResources,
    )
    return setState(newState)
  }

  val lastReadStamp: Long
    get() = myState.lastReadStamp

  val path: @NonNls String
    get() = file.path

  val directory: @NonNls String
    get() = file.parent.path

  val directoryFile: VirtualFile
    get() = file.parent

  val profilesXmlFile: VirtualFile?
    get() = MavenUtil.findProfilesXmlFile(file)

  val profilesXmlNioFile: Path?
    get() = MavenUtil.getProfilesXmlNioFile(file)

  fun hasReadingErrors(): Boolean {
    return myState.readingProblems.any { it.isError }
  }

  val name: @NlsSafe String?
    get() = myState.name

  val displayName: @NlsSafe String
    get() {
      val state = myState
      if (state.name.isNullOrBlank()) {
        return state.mavenId!!.artifactId ?: ""
      }
      return state.name
    }

  val modelMap: Map<String, String>
    get() = myState.modelMap

  val mavenId: MavenId
    get() = myState.mavenId!!

  val isNew: Boolean
    get() = null == myState.mavenId

  val parentId: MavenId?
    get() = myState.parentId

  val packaging: @NlsSafe String
    get() = myState.packaging ?: ""

  val finalName: @NlsSafe String
    get() = myState.finalName!!

  val defaultGoal: @NlsSafe String?
    get() = myState.defaultGoal

  val buildDirectory: @NlsSafe String
    get() = myState.buildDirectory!!

  fun getGeneratedSourcesDirectory(testSources: Boolean): @NlsSafe String {
    return buildDirectory + (if (testSources) "/generated-test-sources" else "/generated-sources")
  }

  val outputDirectory: @NlsSafe String
    get() = myState.outputDirectory!!

  val testOutputDirectory: @NlsSafe String
    get() = myState.testOutputDirectory!!

  val sources: List<String>
    get() = myState.sources

  val testSources: List<String>
    get() = myState.testSources

  val resources: List<MavenResource>
    get() = myState.resources

  val testResources: List<MavenResource>
    get() = myState.testResources

  val filters: List<String>
    get() = myState.filters

  val filterPropertiesFiles: List<String>
    get() {
      var res = getStateCachedValue(FILTERS_CACHE_KEY)
      if (res == null) {
        val propCfg = getPluginGoalConfiguration("org.codehaus.mojo", "properties-maven-plugin", "read-project-properties")
        if (propCfg != null) {
          val files = propCfg.getChild("files")
          if (files != null) {
            res = ArrayList()

            for (file in files.getChildren("file")) {
              var f = File(file.value)
              if (!f.isAbsolute) {
                f = File(directory, file.value)
              }

              res.add(f.absolutePath)
            }
          }
        }

        if (res == null) {
          res = filters
        }
        else {
          res = res.toMutableList()
          res.addAll(filters)
        }

        res = putStateCachedValue(FILTERS_CACHE_KEY, res)
      }

      return res
    }

  val configFileError: String?
    get() = myState.readingProblems.filter { it.path.endsWith(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH) }
      .map { it.description }
      .firstOrNull()

  private fun resetStateCache() {
    // todo a bit hacky
    stateCache.clear()
  }

  @Internal
  fun resetCache() {
    // todo a bit hacky
    cache.clear()
  }

  fun hasUnresolvedArtifacts(): Boolean {
    return !myState.isParentResolved
           || !getUnresolvedDependencies(null).isEmpty()
           || !unresolvedExtensions.isEmpty()
           || !unresolvedAnnotationProcessors.isEmpty()
  }

  private fun getUnresolvedDependencies(fileExistsPredicate: Predicate<File>?): List<MavenArtifact> {
    var myUnresolvedDependenciesCache = getStateCachedValue(UNRESOLVED_DEPENDENCIES_CACHE_KEY)
    if (myUnresolvedDependenciesCache == null) {
      val result: MutableList<MavenArtifact> = ArrayList()
      for (each in dependencies) {
        val resolved = each.isResolved(fileExistsPredicate)
        each.isFileUnresolved = !resolved
        if (!resolved) result.add(each)
      }
      myUnresolvedDependenciesCache = result
      putStateCachedValue(UNRESOLVED_DEPENDENCIES_CACHE_KEY, myUnresolvedDependenciesCache)
    }
    return myUnresolvedDependenciesCache
  }

  private val unresolvedExtensions: List<MavenArtifact>
    get() {
      var myUnresolvedExtensionsCache = getStateCachedValue(UNRESOLVED_EXTENSIONS_CACHE_KEY)
      if (myUnresolvedExtensionsCache == null) {
        val result: MutableList<MavenArtifact> = ArrayList()
        for (each in myState.extensions) {
          // Collect only extensions that were attempted to be resolved.
          // It is because embedder does not even try to resolve extensions that
          // are not necessary.
          if (myState.unresolvedArtifactIds.contains(each.mavenId) && !pomFileExists(localRepositoryPath, each)) {
            result.add(each)
          }
        }
        myUnresolvedExtensionsCache = result
        putStateCachedValue(UNRESOLVED_EXTENSIONS_CACHE_KEY, myUnresolvedExtensionsCache)
      }
      return myUnresolvedExtensionsCache
    }

  private fun pomFileExists(localRepository: Path, artifact: MavenArtifact): Boolean {
    return hasArtifactFile(localRepository, artifact.mavenId, "pom")
  }

  private val unresolvedAnnotationProcessors: List<MavenArtifact>
    get() {
      var myUnresolvedAnnotationProcessors = getStateCachedValue(UNRESOLVED_ANNOTATION_PROCESSORS_CACHE_KEY)
      if (myUnresolvedAnnotationProcessors == null) {
        val result: MutableList<MavenArtifact> = ArrayList()
        for (each in myState.annotationProcessors) {
          if (!each.isResolved) result.add(each)
        }
        myUnresolvedAnnotationProcessors = result
        putStateCachedValue(UNRESOLVED_ANNOTATION_PROCESSORS_CACHE_KEY, myUnresolvedAnnotationProcessors)
      }
      return myUnresolvedAnnotationProcessors
    }

  private val unresolvedPlugins: List<MavenPlugin>
    get() {
      var myUnresolvedPluginsCache = getStateCachedValue(UNRESOLVED_PLUGINS_CACHE_KEY)
      if (myUnresolvedPluginsCache == null) {
        val result: MutableList<MavenPlugin> = ArrayList()
        for (each in declaredPluginInfos) {
          if (each.artifact?.isResolved != true) {
            result.add(each.plugin)
          }
        }
        myUnresolvedPluginsCache = result
        putStateCachedValue(UNRESOLVED_PLUGINS_CACHE_KEY, myUnresolvedPluginsCache)
      }
      return myUnresolvedPluginsCache
    }

  val isAggregator: Boolean
    get() {
      return "pom" == packaging || !modulePaths.isEmpty()
    }

  val problems: List<MavenProjectProblem>
    get() {
      return collectProblems(null)
    }

  @Internal
  fun collectProblems(fileExistsPredicate: Predicate<File>?): List<MavenProjectProblem> {
    var problemsCache = getStateCachedValue(PROBLEMS_CACHE_KEY)
    if (problemsCache == null) {
      problemsCache = doCollectProblems(file, fileExistsPredicate)
      putStateCachedValue(PROBLEMS_CACHE_KEY, problemsCache)
    }
    return problemsCache
  }

  private fun doCollectProblems(file: VirtualFile, fileExistsPredicate: Predicate<File>?): List<MavenProjectProblem> {
    val result: MutableList<MavenProjectProblem> = ArrayList()

    validateParent(file, result)
    val readingProblems = myState.readingProblems
    result.addAll(readingProblems)

    for ((key, value) in modulesPathsAndNames) {
      if (LocalFileSystem.getInstance().findFileByPath(key) == null) {
        result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.moduleNotFound", value)))
      }
    }

    validateDependencies(file, result, fileExistsPredicate)
    validateExtensions(file, result)

    if (readingProblems.isEmpty()) {
      validatePlugins(file, result)
    }

    return result
  }

  private fun createDependencyProblem(file: VirtualFile, description: String): MavenProjectProblem {
    return MavenProjectProblem(file.path, description, MavenProjectProblem.ProblemType.DEPENDENCY, true)
  }

  private fun validateParent(file: VirtualFile, result: MutableList<MavenProjectProblem>) {
    if (!myState.isParentResolved) {
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

  val existingModuleFiles: List<VirtualFile>
    get() {
      val fs: LocalFileSystem = LocalFileSystem.getInstance()

      val result: MutableList<VirtualFile> = ArrayList()
      val pathsInStack: Set<String> = modulePaths
      for (each: String in pathsInStack) {
        val f: VirtualFile? = fs.findFileByPath(each)
        if (f != null) result.add(f)
      }
      return result
    }

  val modulePaths: Set<String>
    get() {
      return modulesPathsAndNames.keys
    }

  val modulesPathsAndNames: Map<String, String>
    get() {
      return myState.modulesPathsAndNames
    }

  val profilesIds: Collection<String>
    get() {
      return myState.profilesIds
    }

  val activatedProfilesIds: MavenExplicitProfiles
    get() {
      return myState.activatedProfilesIds
    }

  val dependencyHash: String?
    get() {
      return myState.dependencyHash
    }

  val dependencies: List<MavenArtifact>
    get() {
      return myState.dependencies
    }

  val externalAnnotationProcessors: List<MavenArtifact>
    get() {
      return myState.annotationProcessors
    }

  val dependencyTree: List<MavenArtifactNode>
    get() {
      return myState.dependencyTree
    }

  @Suppress("SpellCheckingInspection")
  val supportedPackagings: Set<String>
    get() {
      val result = mutableSetOf(MavenConstants.TYPE_POM, MavenConstants.TYPE_JAR, "ejb", "ejb-client", "war", "ear", "bundle", "maven-plugin")
      for (each: MavenImporter in MavenImporter.getSuitableImporters(this)) {
        each.getSupportedPackagings(result)
      }
      return result
    }

  fun getDependencyTypesFromImporters(type: SupportedRequestType): Set<String> {
    val res: MutableSet<String> = HashSet()

    for (each: MavenImporter in MavenImporter.getSuitableImporters(this)) {
      each.getSupportedDependencyTypes(res, type)
    }

    return res
  }

  val supportedDependencyScopes: Set<String>
    get() {
      val result: MutableSet<String> = ContainerUtil.newHashSet(MavenConstants.SCOPE_COMPILE,
                                                                MavenConstants.SCOPE_PROVIDED,
                                                                MavenConstants.SCOPE_RUNTIME,
                                                                MavenConstants.SCOPE_TEST,
                                                                MavenConstants.SCOPE_SYSTEM)
      for (each: MavenImporter in MavenImporter.getSuitableImporters(this)) {
        each.getSupportedDependencyScopes(result)
      }
      return result
    }

  @Internal
  @Deprecated("Do not add dependencies to Maven project. Instead, add dependencies to [com.intellij.platform.workspace.jps.entities.ModuleEntity]")
  fun addDependency(dependency: MavenArtifact) {
    addDependencies(listOf(dependency))
  }

  @Internal
  @Deprecated("Do not add dependencies to Maven project. Instead, add dependencies to [com.intellij.platform.workspace.jps.entities.ModuleEntity]")
  fun addDependencies(dependencies: Collection<MavenArtifact>) {
    val newDependencies = myState.dependencies + dependencies
    val newState = myState.copy(
      dependencies = newDependencies,
    )
    setState(newState)
  }

  fun addAnnotationProcessors(annotationProcessors: Collection<MavenArtifact>) {
    val newAnnotationProcessors = myState.annotationProcessors + annotationProcessors
    val newState = myState.copy(
      annotationProcessors = newAnnotationProcessors,
    )
    setState(newState)
  }

  fun findManagedDependencyVersion(groupId: String, artifactId: String): String? = myState.managedDependencies[GroupAndArtifact(groupId, artifactId)]

  fun findDependencies(depProject: MavenProject): List<MavenArtifact> {
    return findDependencies(depProject.mavenId)
  }

  fun findDependencies(id: MavenId): List<MavenArtifact> {
    return dependencyArtifactIndex.findArtifacts(id)
  }

  fun findDependencies(groupId: @NonNls String?, artifactId: @NonNls String?): List<MavenArtifact> {
    return dependencyArtifactIndex.findArtifacts(groupId, artifactId)
  }

  fun hasDependency(groupId: @NonNls String?, artifactId: @NonNls String?): Boolean {
    return dependencyArtifactIndex.hasArtifact(groupId, artifactId)
  }

  fun hasUnresolvedPlugins(): Boolean {
    return !unresolvedPlugins.isEmpty()
  }

  val plugins: List<MavenPlugin>
    get() {
      return myState.plugins
    }

  @get:ApiStatus.Experimental
  val pluginInfos: List<MavenPluginWithArtifact>
    get() {
      return myState.pluginInfos
    }

  val declaredPlugins: List<MavenPlugin>
    get() {
      return myState.declaredPlugins
    }

  @get:ApiStatus.Experimental
  val declaredPluginInfos: List<MavenPluginWithArtifact>
    get() {
      return myState.declaredPluginInfos
    }

  fun getPluginConfiguration(groupId: String?, artifactId: String?): Element? {
    return getPluginGoalConfiguration(groupId, artifactId, null)
  }

  fun getPluginGoalConfiguration(groupId: String?, artifactId: String?, goal: String?): Element? {
    return getPluginGoalConfiguration(findPlugin(groupId, artifactId), goal)
  }

  fun getPluginGoalConfiguration(plugin: MavenPlugin?, goal: String?): Element? {
    if (plugin == null) return null
    return if (goal == null) plugin.configurationElement else plugin.getGoalConfiguration(goal)
  }

  @JvmOverloads
  fun findPlugin(groupId: String?, artifactId: String?, explicitlyDeclaredOnly: Boolean = false): MavenPlugin? {
    val plugins: List<MavenPlugin> = if (explicitlyDeclaredOnly) declaredPlugins else plugins
    for (each: MavenPlugin in plugins) {
      if (each.mavenId.equals(groupId, artifactId)) return each
    }
    return null
  }

  val sourceEncoding: String?
    get() {
      return myState.properties!!.getProperty("project.build.sourceEncoding")
    }

  fun getResourceEncoding(project: Project): String? {
    val pluginConfiguration: Element? = getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin")
    if (pluginConfiguration != null) {
      val encoding: String? = pluginConfiguration.getChildTextTrim("encoding")
      if (encoding == null) {
        return null
      }

      if (encoding.startsWith("$")) {
        val domModel: MavenDomProjectModel? = MavenDomUtil.getMavenDomProjectModel(project, this.file)
        if (domModel == null) {
          MavenLog.LOG.warn("cannot get MavenDomProjectModel to find encoding")
          return sourceEncoding
        }
        else {
          MavenPropertyResolver.resolve(encoding, domModel)
        }
      }
      else {
        return encoding
      }
    }
    return sourceEncoding
  }

  val properties: Properties
    get() {
      return myState.properties!!
    }

  val mavenConfig: Map<String, String>
    get() {
      return getPropertiesFromConfig(ConfigFileKind.MAVEN_CONFIG)
    }

  private fun getPropertiesFromConfig(kind: ConfigFileKind): Map<String, String> {
    var mavenConfig: Map<String, String>? = getStateCachedValue(kind.CACHE_KEY)
    if (mavenConfig == null) {
      mavenConfig = readConfigFile(MavenUtil.getBaseDir(directoryFile).toFile(), kind)
      putStateCachedValue(kind.CACHE_KEY, mavenConfig)
    }

    return mavenConfig
  }

  val jvmConfig: Map<String, String>
    get() {
      return getPropertiesFromConfig(ConfigFileKind.JVM_CONFIG)
    }

  @Deprecated("Use localRepositoryPath")
  val localRepository: File
    get() {
      return localRepositoryPath.toFile()
    }

  val localRepositoryPath: Path
    get() {
      return myState.localRepository!!.toPath()
    }

  val remoteRepositories: List<MavenRemoteRepository>
    get() {
      return myState.remoteRepositories
    }

  val remotePluginRepositories: List<MavenRemoteRepository>
    get() {
      return myState.remotePluginRepositories
    }

  fun getClassifierAndExtension(artifact: MavenArtifact, type: MavenExtraArtifactType): Pair<String, String> {
    for (each: MavenImporter in MavenImporter.getSuitableImporters(this)) {
      val result: Pair<String, String>? = each.getExtraArtifactClassifierAndExtension(artifact, type)
      if (result != null) return result
    }
    return Pair.create(type.defaultClassifier, type.defaultExtension)
  }

  val dependencyArtifactIndex: MavenArtifactIndex
    get() {
      var res: MavenArtifactIndex? = getStateCachedValue(DEPENDENCIES_CACHE_KEY)
      if (res == null) {
        res = MavenArtifactIndex.build(dependencies)
        res = putStateCachedValue(DEPENDENCIES_CACHE_KEY, res)
      }

      return res!!
    }

  @Internal
  fun <V> getCachedValue(key: Key<V>): V? {
    return cache[key] as V?
  }

  @Internal
  fun <V> putCachedValue(key: Key<V>, value: V): V {
    val oldValue = cache.putIfAbsent(key, value as Any)
    if (oldValue != null) {
      return oldValue as V
    }
    return value
  }

  private fun <V> getStateCachedValue(key: Key<V>): V? {
    return stateCache[key] as V?
  }

  // resets with every state change
  private fun <V> putStateCachedValue(key: Key<V>, value: V): V {
    val oldValue = stateCache.putIfAbsent(key, value as Any)
    if (oldValue != null) {
      return oldValue as V
    }
    return value
  }

  override fun toString(): String {
    return if (null == myState.mavenId) file.path else mavenId.toString()
  }

  companion object {
    private val DEPENDENCIES_CACHE_KEY: Key<MavenArtifactIndex?> = Key.create("MavenProject.DEPENDENCIES_CACHE_KEY")
    private val FILTERS_CACHE_KEY: Key<List<String>> = Key.create("MavenProject.FILTERS_CACHE_KEY")
    private val PROBLEMS_CACHE_KEY: Key<List<MavenProjectProblem>> = Key.create("MavenProject.PROBLEMS_CACHE_KEY")
    private val UNRESOLVED_DEPENDENCIES_CACHE_KEY: Key<List<MavenArtifact>> = Key.create("MavenProject.UNRESOLVED_DEPENDENCIES_CACHE_KEY")
    private val UNRESOLVED_PLUGINS_CACHE_KEY: Key<List<MavenPlugin>> = Key.create("MavenProject.UNRESOLVED_PLUGINS_CACHE_KEY")
    private val UNRESOLVED_EXTENSIONS_CACHE_KEY: Key<List<MavenArtifact>> = Key.create("MavenProject.UNRESOLVED_EXTENSIONS_CACHE_KEY")
    private val UNRESOLVED_ANNOTATION_PROCESSORS_CACHE_KEY: Key<List<MavenArtifact>> = Key.create("MavenProject.UNRESOLVED_AP_CACHE_KEY")

    @Throws(IOException::class)
    fun read(`in`: DataInputStream): MavenProject? {
      val path: String = `in`.readUTF()
      val length: Int = `in`.readInt()
      val bytes: ByteArray = ByteArray(length)
      `in`.readFully(bytes)

      // should read full byte content first!!!
      val file: VirtualFile? = LocalFileSystem.getInstance().findFileByPath(path)
      if (file == null) return null

      try {
        ByteArrayInputStream(bytes).use { bs ->
          ObjectInputStream(bs).use { os ->
            val result = MavenProject(file)
            result.myState = os.readObject() as MavenProjectState
            return result
          }
        }
      }
      catch (e: ClassNotFoundException) {
        throw IOException(e)
      }
    }

    @JvmStatic
    fun readConfigFile(baseDir: File?, kind: ConfigFileKind): Map<String, String> {
      val configFile = File(baseDir, FileUtil.toSystemDependentName(kind.myRelativeFilePath))

      val parametersList = ParametersList()
      if (configFile.isFile) {
        try {
          parametersList.addParametersString(FileUtil.loadFile(configFile, CharsetToolkit.UTF8))
        }
        catch (ignore: IOException) {
        }
      }
      val config = parametersList.getProperties(kind.myValueIfMissing)
      return config.ifEmpty { emptyMap() }
    }

    private fun doUpdateState(
      state: MavenProjectState,
      incLastReadStamp: Boolean,
      model: MavenModel,
      managedDependencies: List<MavenId>,
      readingProblems: Collection<MavenProjectProblem>,
      activatedProfiles: MavenExplicitProfiles,
      unresolvedArtifactIds: Set<MavenId>,
      nativeModelMap: Map<String, String>,
      effectiveRepositoryPath: Path,
      keepPreviousArtifacts: Boolean,
      keepPreviousProfiles: Boolean,
      keepPreviousPlugins: Boolean,
      directory: String,
      fileExtension: String?,
      dependencyHash: String?,
    ): MavenProjectState {
      val build = model.build

      val newUnresolvedArtifacts: MutableSet<MavenId> = HashSet()
      val newRepositories = LinkedHashSet<MavenRemoteRepository>()
      val newPluginRepositories = LinkedHashSet<MavenRemoteRepository>()
      val newDependencies = LinkedHashSet<MavenArtifact>()
      val newDependencyTree = LinkedHashSet<MavenArtifactNode>()
      val newPluginInfos = LinkedHashSet<MavenPluginWithArtifact>()
      val newExtensions = LinkedHashSet<MavenArtifact>()
      val newAnnotationProcessors = LinkedHashSet<MavenArtifact>()
      val newManagedDeps = HashMap<GroupAndArtifact, String>(managedDependencies.size)

      if (keepPreviousArtifacts) {
        newUnresolvedArtifacts.addAll(state.unresolvedArtifactIds)
        newRepositories.addAll(state.remoteRepositories)
        newPluginRepositories.addAll(state.remotePluginRepositories)
        newDependencies.addAll(state.dependencies)
        newDependencyTree.addAll(state.dependencyTree)
        newExtensions.addAll(state.extensions)
        newAnnotationProcessors.addAll(state.annotationProcessors)
        newManagedDeps.putAll(state.managedDependencies)
      }

      // either keep all previous plugins or only those that are present in the new list
      if (keepPreviousPlugins) {
        newPluginInfos.addAll(state.pluginInfos)
        newPluginInfos.addAll(model.plugins.map { MavenPluginWithArtifact(it, null) })
      }
      else {
        model.plugins.forEach { newPlugin ->
          newPluginInfos.add(state.pluginInfos.firstOrNull { it.plugin == newPlugin } ?: MavenPluginWithArtifact(newPlugin, null))
        }
      }

      newUnresolvedArtifacts.addAll(unresolvedArtifactIds)
      newRepositories.addAll(model.remoteRepositories)
      newPluginRepositories.addAll(model.remotePluginRepositories)
      newDependencyTree.addAll(model.dependencyTree)
      newDependencies.addAll(model.dependencies)
      newExtensions.addAll(model.extensions)

      for (md in managedDependencies) {
        newManagedDeps.put(GroupAndArtifact(md.groupId ?: "", md.artifactId ?: ""), md.version ?: "")
      }

      val remoteRepositories = ArrayList(newRepositories)
      val remotePluginRepositories = ArrayList(newPluginRepositories)
      val dependencies = ArrayList(newDependencies)
      val dependencyTree = ArrayList(newDependencyTree)
      val pluginInfos = ArrayList(newPluginInfos)
      val extensions = ArrayList(newExtensions)
      val annotationProcessors = ArrayList(newAnnotationProcessors)
      val managedDependenciesMap = LinkedHashMap(newManagedDeps)

      val newDependencyHash = dependencyHash ?: state.dependencyHash
      val lastReadStamp = state.lastReadStamp + if (incLastReadStamp) 1 else 0

      MavenModelPropertiesPatcher.patch(model.properties, pluginInfos.map { it.plugin })

      return state.copy(
        lastReadStamp = lastReadStamp,
        readingProblems = readingProblems,
        localRepository = effectiveRepositoryPath.toFile(),
        activatedProfilesIds = activatedProfiles,
        mavenId = model.mavenId,
        parentId = model.parent?.mavenId,
        packaging = model.packaging,
        name = model.name,
        finalName = build.finalName,
        defaultGoal = build.defaultGoal,
        buildDirectory = build.directory,
        outputDirectory = build.outputDirectory,
        testOutputDirectory = build.testOutputDirectory,
        filters = build.filters,
        properties = model.properties,
        modulesPathsAndNames = collectModulePathsAndNames(model, directory, fileExtension),
        profilesIds = collectProfilesIds(model.profiles) + if (keepPreviousProfiles) state.profilesIds else emptySet(),
        modelMap = nativeModelMap,
        sources = build.sources,
        testSources = build.testSources,
        resources = build.resources,
        testResources = build.testResources,
        unresolvedArtifactIds = newUnresolvedArtifacts,
        remoteRepositories = remoteRepositories,
        remotePluginRepositories = remotePluginRepositories,
        dependencies = dependencies,
        dependencyTree = dependencyTree,
        pluginInfos = pluginInfos,
        extensions = extensions,
        annotationProcessors = annotationProcessors,
        managedDependencies = managedDependenciesMap,
        dependencyHash = newDependencyHash,
      )
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
      val modules = mavenModel.modules
      if (null == modules) return result
      for (module in modules) {
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

    private fun collectProfilesIds(profiles: Collection<MavenProfile>?): Set<String> {
      if (profiles == null) return emptySet()

      val result = HashSet<String>(profiles.size)
      for (each in profiles) {
        result.add(each.id)
      }
      return result
    }
  }
}
