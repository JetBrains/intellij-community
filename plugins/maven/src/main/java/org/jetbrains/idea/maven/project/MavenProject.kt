// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.MavenPropertyResolver
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.server.MavenGoalExecutionResult
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildValueByPath
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.*
import java.util.*
import java.util.function.Predicate
import kotlin.concurrent.Volatile

class MavenProject(val file: VirtualFile) {
  enum class ConfigFileKind(val myRelativeFilePath: String, val myValueIfMissing: String) {
    MAVEN_CONFIG(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "true"),
    JVM_CONFIG(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "");

    val CACHE_KEY: Key<Map<String, String>> = Key.create(
      "MavenProject.$name")
  }

  @Volatile
  private var myState: MavenProjectState = MavenProjectState()

  enum class ProcMode {
    BOTH, ONLY, NONE
  }

  @Throws(IOException::class)
  fun write(out: DataOutputStream) {
    out.writeUTF(path)

    BufferExposingByteArrayOutputStream().use { bs ->
      ObjectOutputStream(bs).use { os ->
        os.writeObject(myState)
        out.writeInt(bs.size())
        out.write(bs.internalBuffer, 0, bs.size())
      }
    }
  }

  @ApiStatus.Internal
  fun updateFromReaderResult(
    readerResult: MavenProjectReaderResult,
    settings: MavenGeneralSettings,
    keepPreviousArtifacts: Boolean,
  ): MavenProjectChanges {
    val newState: MavenProjectState = myState.clone()

    newState.incLastReadStamp()

    val keepPreviousPlugins: Boolean = keepPreviousArtifacts

    newState.doUpdateState(
      readerResult.mavenModel,
      readerResult.readingProblems,
      readerResult.activatedProfiles,
      setOf(),
      readerResult.nativeModelMap,
      settings,
      keepPreviousArtifacts,
      false,
      keepPreviousPlugins,
      directory,
      file.extension
    )

    return setState(newState)
  }

  @ApiStatus.Internal
  fun updateState(
    model: MavenModel,
    dependencyHash: String?,
    readingProblems: Collection<MavenProjectProblem>,
    activatedProfiles: MavenExplicitProfiles,
    unresolvedArtifactIds: Set<MavenId>,
    nativeModelMap: Map<String, String>,
    settings: MavenGeneralSettings,
    keepPreviousArtifacts: Boolean,
    keepPreviousPlugins: Boolean,
  ): MavenProjectChanges {
    val newState: MavenProjectState = myState.clone()

    if (null != dependencyHash) {
      newState.dependencyHash = dependencyHash
    }

    newState.doUpdateState(
      model,
      readingProblems,
      activatedProfiles,
      unresolvedArtifactIds,
      nativeModelMap,
      settings,
      keepPreviousArtifacts,
      true,
      keepPreviousPlugins,
      directory,
      file.extension
    )

    return setState(newState)
  }

  @ApiStatus.Internal
  fun updateState(
    dependencies: List<MavenArtifact>,
    properties: Properties,
    plugins: List<MavenPlugin>,
  ): MavenProjectChanges {
    val newState: MavenProjectState = myState.clone()

    newState.doUpdateState(dependencies, properties, plugins)

    return setState(newState)
  }

  @ApiStatus.Internal
  fun updateState(readingProblems: Collection<MavenProjectProblem>): MavenProjectChanges {
    val newState: MavenProjectState = myState.clone()

    newState.readingProblems = readingProblems

    return setState(newState)
  }

  private fun setState(newState: MavenProjectState): MavenProjectChanges {
    val changes: MavenProjectChanges = myState.getChanges(newState)
    myState = newState
    return changes
  }

  private fun updateState(updater: Consumer<MavenProjectState>) {
    val newState: MavenProjectState = myState.clone()
    updater.consume(newState)
    myState = newState
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

  @ApiStatus.Internal
  fun setFolders(folders: MavenGoalExecutionResult.Folders): MavenProjectChanges {
    val newState: MavenProjectState = myState.clone()
    newState.doSetFolders(folders.sources, folders.testSources, folders.resources, folders.testResources)
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

  val profilesXmlIoFile: File?
    get() = MavenUtil.getProfilesXmlIoFile(file)

  fun hasReadingProblems(): Boolean {
    return !myState.readingProblems.isEmpty()
  }

  val name: @NlsSafe String?
    get() = myState.name

  val displayName: @NlsSafe String
    get() {
      val state: MavenProjectState = myState
      if (StringUtil.isEmptyOrSpaces(state.name)) {
        return StringUtil.notNullize(state.mavenId!!.artifactId)
      }
      return state.name!!
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
    get() = myState.packaging!!

  val finalName: @NlsSafe String
    get() = myState.finalName!!

  val defaultGoal: @NlsSafe String?
    get() = myState.defaultGoal

  val buildDirectory: @NlsSafe String
    get() = myState.buildDirectory!!

  fun getGeneratedSourcesDirectory(testSources: Boolean): @NlsSafe String {
    return buildDirectory + (if (testSources) "/generated-test-sources" else "/generated-sources")
  }

  fun getAnnotationProcessorDirectory(testSources: Boolean): @NlsSafe String {
    if (procMode == ProcMode.NONE) {
      val bscMavenPlugin: MavenPlugin? = findPlugin("org.bsc.maven", "maven-processor-plugin")
      val cfg: Element? = getPluginGoalConfiguration(bscMavenPlugin, if (testSources) "process-test" else "process")
      if (bscMavenPlugin != null && cfg == null) {
        return buildDirectory + (if (testSources) "/generated-sources/apt-test" else "/generated-sources/apt")
      }
      if (cfg != null) {
        var out: String? = findChildValueByPath(cfg, "outputDirectory")
        if (out == null) {
          out = findChildValueByPath(cfg, "defaultOutputDirectory")
          if (out == null) {
            return buildDirectory + (if (testSources) "/generated-sources/apt-test" else "/generated-sources/apt")
          }
        }

        if (!File(out).isAbsolute) {
          out = directory + '/' + out
        }

        return out
      }
    }

    val def: String = getGeneratedSourcesDirectory(testSources) + (if (testSources) "/test-annotations" else "/annotations")
    return findChildValueByPath(
      compilerConfig, if (testSources) "generatedTestSourcesDirectory" else "generatedSourcesDirectory", def)!!
  }

  val procMode: ProcMode
    get() {
      var compilerConfiguration: Element? = getPluginExecutionConfiguration("org.apache.maven.plugins", "maven-compiler-plugin",
                                                                            "default-compile")
      if (compilerConfiguration == null) {
        compilerConfiguration = compilerConfig
      }

      if (compilerConfiguration == null) {
        return ProcMode.BOTH
      }

      val procElement: Element? = compilerConfiguration.getChild("proc")
      if (procElement != null) {
        val procMode: String = procElement.value
        return if (("only".equals(procMode, ignoreCase = true))) ProcMode.ONLY
        else if (("none".equals(procMode, ignoreCase = true))) ProcMode.NONE else ProcMode.BOTH
      }

      val compilerArgument: String = compilerConfiguration.getChildTextTrim("compilerArgument")
      if ("-proc:none" == compilerArgument) {
        return ProcMode.NONE
      }
      if ("-proc:only" == compilerArgument) {
        return ProcMode.ONLY
      }

      val compilerArguments: Element? = compilerConfiguration.getChild("compilerArgs")
      if (compilerArguments != null) {
        for (element: Element in compilerArguments.children) {
          val arg: String = element.value
          if ("-proc:none" == arg) {
            return ProcMode.NONE
          }
          if ("-proc:only" == arg) {
            return ProcMode.ONLY
          }
        }
      }

      return ProcMode.BOTH
    }

  val annotationProcessorOptions: Map<String, String>
    get() {
      val compilerConfig: Element? = compilerConfig
      if (compilerConfig == null) {
        return emptyMap()
      }
      if (procMode != ProcMode.NONE) {
        return getAnnotationProcessorOptionsFromCompilerConfig(compilerConfig)
      }
      val bscMavenPlugin: MavenPlugin? = findPlugin("org.bsc.maven", "maven-processor-plugin")
      if (bscMavenPlugin != null) {
        return getAnnotationProcessorOptionsFromProcessorPlugin(bscMavenPlugin)
      }
      return emptyMap()
    }

  val declaredAnnotationProcessors: List<String>?
    get() {
      val compilerConfig: Element? = compilerConfig
      if (compilerConfig == null) {
        return null
      }

      val result: MutableList<String> = ArrayList()
      if (procMode != ProcMode.NONE) {
        val processors: Element? = compilerConfig.getChild("annotationProcessors")
        if (processors != null) {
          for (element: Element in processors.getChildren("annotationProcessor")) {
            val processorClassName: String = element.textTrim
            if (!processorClassName.isEmpty()) {
              result.add(processorClassName)
            }
          }
        }
      }
      else {
        val bscMavenPlugin: MavenPlugin? = findPlugin("org.bsc.maven", "maven-processor-plugin")
        if (bscMavenPlugin != null) {
          var bscCfg: Element? = bscMavenPlugin.getGoalConfiguration("process")
          if (bscCfg == null) {
            bscCfg = bscMavenPlugin.configurationElement
          }

          if (bscCfg != null) {
            val bscProcessors: Element? = bscCfg.getChild("processors")
            if (bscProcessors != null) {
              for (element: Element in bscProcessors.getChildren("processor")) {
                val processorClassName: String = element.textTrim
                if (!processorClassName.isEmpty()) {
                  result.add(processorClassName)
                }
              }
            }
          }
        }
      }

      return result
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
      var res: List<String>? = getCachedValue<List<String>>(FILTERS_CACHE_KEY)
      if (res == null) {
        val propCfg: Element? = getPluginGoalConfiguration("org.codehaus.mojo", "properties-maven-plugin",
                                                           "read-project-properties")
        if (propCfg != null) {
          val files: Element? = propCfg.getChild("files")
          if (files != null) {
            res = ArrayList()

            for (file: Element in files.getChildren("file")) {
              var f: File = File(file.value)
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

        res = putCachedValue<List<String>>(FILTERS_CACHE_KEY, res)
      }

      return res
    }

  var configFileError: String?
    get() = myState.readingProblems.filter { it.path.endsWith(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH) }
      .map { it.description }
      .firstOrNull()
    set(message) {
      if (message != null) {
        val mavenConfigPath: String = file.path + "/" + MavenConstants.MAVEN_CONFIG_RELATIVE_PATH
        myState.readingProblems.add(MavenProjectProblem(mavenConfigPath, message, MavenProjectProblem.ProblemType.SYNTAX, true))
      }
    }

  fun resetCache() {
    // todo a bit hacky
    synchronized(myState) {
      myState.resetCache()
    }
  }

  val isAggregator: Boolean
    get() {
      return "pom" == packaging || !modulePaths.isEmpty()
    }

  val problems: List<MavenProjectProblem>
    get() {
      val problems: List<MavenProjectProblem>? = myState.problemsCache
      if (null != problems) return problems

      return collectProblems(null)
    }

  @ApiStatus.Internal
  fun collectProblems(fileExistsPredicate: Predicate<File>?): List<MavenProjectProblem> {
    return myState.collectProblems(file, fileExistsPredicate)
  }

  val cacheProblems: List<MavenProjectProblem>
    get() {
      val problemsCache: List<MavenProjectProblem>? = myState.problemsCache
      return if (problemsCache == null) emptyList() else problemsCache
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

  @get:Suppress("SpellCheckingInspection")
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

  fun addDependency(dependency: MavenArtifact) {
    addDependencies(java.util.List.of(dependency))
  }

  fun addDependencies(dependencies: Collection<MavenArtifact>) {
    updateState { newState: MavenProjectState -> newState.dependencies.addAll(dependencies) }
  }

  fun addAnnotationProcessors(annotationProcessors: Collection<MavenArtifact?>) {
    updateState { newState: MavenProjectState -> newState.annotationProcessors.addAll(annotationProcessors) }
  }

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

  fun hasUnresolvedArtifacts(): Boolean {
    return myState.hasUnresolvedArtifacts()
  }

  fun hasUnresolvedPlugins(): Boolean {
    return !myState.unresolvedPlugins.isEmpty()
  }

  val plugins: List<MavenPlugin>
    get() {
      return myState.plugins
    }

  val declaredPlugins: List<MavenPlugin>
    get() {
      return myState.declaredPlugins
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

  private fun getPluginExecutionConfiguration(groupId: String?, artifactId: String?, executionId: String): Element? {
    val plugin: MavenPlugin? = findPlugin(groupId, artifactId)
    if (plugin == null) return null
    return plugin.getExecutionConfiguration(executionId)
  }

  private val compileExecutionConfigurations: List<Element>
    get() {
      val plugin: MavenPlugin? = findPlugin("org.apache.maven.plugins", "maven-compiler-plugin")
      if (plugin == null) return emptyList()
      return plugin.compileExecutionConfigurations
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

  val sourceLevel: String?
    get() {
      return getCompilerLevel("source")
    }

  val targetLevel: String?
    get() {
      return getCompilerLevel("target")
    }

  val releaseLevel: String?
    get() {
      return getCompilerLevel("release")
    }

  val testSourceLevel: String?
    get() {
      return getCompilerLevel("testSource")
    }

  val testTargetLevel: String?
    get() {
      return getCompilerLevel("testTarget")
    }

  val testReleaseLevel: String?
    get() {
      return getCompilerLevel("testRelease")
    }

  private fun getCompilerLevel(level: String): String? {
    val configs: List<Element> = compilerConfigs
    if (configs.size == 1) return getCompilerLevel(level, configs.get(0))

    return configs.stream()
      .map { element: Element? -> findChildValueByPath(element, level) }
      .filter { obj: String? -> Objects.nonNull(obj) }
      .map { propertyValue: String? -> LanguageLevel.parse(propertyValue) }
      .map { languageLevel: LanguageLevel? -> if (languageLevel == null) LanguageLevel.HIGHEST else languageLevel }
      .max(Comparator.naturalOrder())
      .map({ l: LanguageLevel -> l.toJavaVersion().toFeatureString() })
      .orElseGet({ myState.properties!!.getProperty("maven.compiler.$level") })
  }

  private fun getCompilerLevel(level: String, config: Element): String? {
    var result: String? = findChildValueByPath(config, level)
    if (result == null) {
      result = myState.properties!!.getProperty("maven.compiler.$level")
    }
    return result
  }

  private val compilerConfig: Element?
    get() {
      val executionConfiguration: Element? =
        getPluginExecutionConfiguration("org.apache.maven.plugins", "maven-compiler-plugin", "default-compile")
      if (executionConfiguration != null) return executionConfiguration
      return getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin")
    }

  private val compilerConfigs: List<Element>
    get() {
      val configurations: List<Element> = compileExecutionConfigurations
      if (!configurations.isEmpty()) return configurations
      val configuration: Element? = getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin")
      return ContainerUtil.createMaybeSingletonList(configuration)
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
    var mavenConfig: Map<String, String>? = getCachedValue(kind.CACHE_KEY)
    if (mavenConfig == null) {
      mavenConfig = readConfigFile(MavenUtil.getBaseDir(
        directoryFile).toFile(), kind)
      putCachedValue(kind.CACHE_KEY, mavenConfig)
    }

    return mavenConfig
  }

  val jvmConfig: Map<String, String>
    get() {
      return getPropertiesFromConfig(ConfigFileKind.JVM_CONFIG)
    }

  val localRepository: File
    get() {
      return myState.localRepository!!
    }

  val remoteRepositories: List<MavenRemoteRepository>
    get() {
      return myState.remoteRepositories
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
      var res: MavenArtifactIndex? = getCachedValue(
        DEPENDENCIES_CACHE_KEY)
      if (res == null) {
        res = MavenArtifactIndex.build(dependencies)
        res = putCachedValue(DEPENDENCIES_CACHE_KEY, res)
      }

      return res!!
    }

  fun <V> getCachedValue(key: Key<V>): V? {
    val v: V? = myState.cache.get(key) as V?
    return v
  }

  fun <V> putCachedValue(key: Key<V>, value: V): V {
    val oldValue: Any? = myState.cache.putIfAbsent(key, value)
    if (oldValue != null) {
      val v: V = oldValue as V
      return v
    }
    return value
  }

  override fun toString(): String {
    return if (null == myState.mavenId) file.path else mavenId.toString()
  }

  companion object {
    private val DEPENDENCIES_CACHE_KEY: Key<MavenArtifactIndex?> = Key.create("MavenProject.DEPENDENCIES_CACHE_KEY")
    private val FILTERS_CACHE_KEY: Key<List<String>> = Key.create("MavenProject.FILTERS_CACHE_KEY")

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
            val result: MavenProject = MavenProject(file)
            result.myState = os.readObject() as MavenProjectState
            return result
          }
        }
      }
      catch (e: ClassNotFoundException) {
        throw IOException(e)
      }
    }

    private fun getAnnotationProcessorOptionsFromCompilerConfig(compilerConfig: Element): Map<String, String> {
      val res: MutableMap<String, String> = LinkedHashMap()

      val compilerArgument: String = compilerConfig.getChildText("compilerArgument")
      addAnnotationProcessorOptionFromParameterString(compilerArgument, res)

      val compilerArgs: Element? = compilerConfig.getChild("compilerArgs")
      if (compilerArgs != null) {
        for (e: Element in compilerArgs.children) {
          if (!StringUtil.equals(e.name, "arg")) continue
          val arg: String = e.textTrim
          addAnnotationProcessorOption(arg, res)
        }
      }

      val compilerArguments: Element? = compilerConfig.getChild("compilerArguments")
      if (compilerArguments != null) {
        for (e: Element in compilerArguments.children) {
          var name: String = e.name
          name = StringUtil.trimStart(name, "-")

          if (name.length > 1 && name.get(0) == 'A') {
            res[name.substring(1)] = e.textTrim
          }
        }
      }
      return res
    }

    private fun addAnnotationProcessorOptionFromParameterString(compilerArguments: String, res: MutableMap<String, String>) {
      if (!StringUtil.isEmptyOrSpaces(compilerArguments)) {
        val parametersList: ParametersList = ParametersList()
        parametersList.addParametersString(compilerArguments)

        for (param: String in parametersList.parameters) {
          addAnnotationProcessorOption(param, res)
        }
      }
    }

    private fun addAnnotationProcessorOption(compilerArg: String?, optionsMap: MutableMap<String, String>) {
      if (compilerArg == null || compilerArg.trim { it <= ' ' }.isEmpty()) return

      if (compilerArg.startsWith("-A")) {
        val idx: Int = compilerArg.indexOf('=', 3)
        if (idx >= 0) {
          optionsMap[compilerArg.substring(2, idx)] = compilerArg.substring(idx + 1)
        }
        else {
          optionsMap[compilerArg.substring(2)] = ""
        }
      }
    }

    private fun getAnnotationProcessorOptionsFromProcessorPlugin(bscMavenPlugin: MavenPlugin): Map<String, String> {
      var cfg: Element? = bscMavenPlugin.getGoalConfiguration("process")
      if (cfg == null) {
        cfg = bscMavenPlugin.configurationElement
      }
      val res: LinkedHashMap<String, String> = LinkedHashMap()
      if (cfg != null) {
        val compilerArguments: String = cfg.getChildText("compilerArguments")
        addAnnotationProcessorOptionFromParameterString(compilerArguments, res)

        val optionsElement: Element? = cfg.getChild("options")
        if (optionsElement != null) {
          for (option: Element in optionsElement.children) {
            res[option.name] = option.text
          }
        }
      }
      return res
    }

    @JvmStatic
    fun readConfigFile(baseDir: File?, kind: ConfigFileKind): Map<String, String> {
      val configFile: File = File(baseDir, FileUtil.toSystemDependentName(kind.myRelativeFilePath))

      val parametersList: ParametersList = ParametersList()
      if (configFile.isFile) {
        try {
          parametersList.addParametersString(FileUtil.loadFile(configFile, CharsetToolkit.UTF8))
        }
        catch (ignore: IOException) {
        }
      }
      val config: Map<String, String> = parametersList.getProperties(kind.myValueIfMissing)
      return if (config.isEmpty()) emptyMap() else config
    }
  }
}
