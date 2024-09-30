// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.preimport

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector.PREIMPORT_ACTIVITY
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.util.text.nullize
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jdom.Content
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap


@Service(Service.Level.PROJECT)
@Internal
class MavenProjectStaticImporter(val project: Project, val coroutineScope: CoroutineScope) {
  private val localRepo = MavenProjectsManager.getInstance(project).reposirotyPath

  suspend fun syncStatic(
    rootProjectFiles: List<VirtualFile>,
    optionalModelsProvider: IdeModifiableModelsProvider?,
    importingSettings: MavenImportingSettings,
    generalSettings: MavenGeneralSettings,
    reimportExistingFiles: Boolean,
    visitor: MavenStructureProjectVisitor,
    parentActivity: StructuredIdeActivity,
    commit: Boolean,
  ): PreimportResult {
    val activity = PREIMPORT_ACTIVITY.startedWithParent(project, parentActivity)
    val statisticsData = StatisticsData(project, rootProjectFiles.size)
    try {
      val scope = coroutineScope.childScope(Dispatchers.IO, false)

      val forest = rootProjectFiles.map {
        scope.syncStatic(it)
      }.awaitAll().filterNotNull()
      if (forest.isEmpty()) return PreimportResult.empty(project)


      val projectTree = MavenProjectsTree(project)
      val roots = ArrayList<MavenProject>()
      val mavenProjectMappings = HashMap<MavenProject, List<MavenProject>>()
      val allProjects = forest.flatMap { it.projects() }.toList()
      visitor.map(allProjects)
      val projectChanges = HashMap<MavenProject, MavenProjectChanges>()
      val existingTree = if (!commit) null else MavenProjectsManager.getInstance(project).let { if (it.isMavenizedProject) it.projectsTree else null }

      forest.forEach { tree ->
        mavenProjectMappings.putAll(tree.mavenProjectMappings())
        tree.root?.let(roots::add)

        if (existingTree == null || reimportExistingFiles) {
          projectChanges.putAll(tree.projects().associateWith { MavenProjectChanges.ALL })
        }
        else {
          projectChanges.putAll(
            tree.projects().filter { existingTree.findProject(it.file) == null }.associateWith { MavenProjectChanges.ALL })
        }
      }

      statisticsData.add(forest, allProjects)

      if (!commit) return PreimportResult.empty(project)

      if (existingTree != null && allProjects.all { existingTree.findProject(it.mavenId) != null }) {
        return PreimportResult.empty(project)
      }


      projectTree.updater()
        .setRootProjects(roots)
        .setManagedFiles(roots.map { it.file.path })
        .setAggregatorMappings(mavenProjectMappings)
        .setMavenIdMappings(allProjects)

      val modelsProvider = optionalModelsProvider ?: ProjectDataManager.getInstance().createModifiableModelsProvider(project)
      // MavenProjectsManager.getInstance(project).projectsTree = projectTree
      return PreimportResult(withBackgroundProgress(project, MavenProjectBundle.message("maven.project.importing"), false) {
        blockingContext {
          val importer = MavenProjectImporter.createStaticImporter(project,
                                                                   projectTree,
                                                                   projectChanges,
                                                                   modelsProvider,
                                                                   importingSettings,
                                                                   parentActivity)

          importer.importProject()
          return@blockingContext importer.createdModules()

        }
      }, projectTree)
    }
    catch (e: Throwable) {
      MavenLog.LOG.error(e)
      return PreimportResult.empty(project)
    }
    finally {
      activity.finished {
        listOf(
          ProjectImportCollector.SUBMODULES_COUNT.with(statisticsData.totalProjects),
          ProjectImportCollector.ROOT_PROJECTS.with(statisticsData.rootProjects),
          ProjectImportCollector.LINKED_PROJECTS.with(statisticsData.linkedProject),
          ProjectImportCollector.RESOLVED_DEPENDENCIES.with(statisticsData.resolvedDependencies),
          ProjectImportCollector.RESOLVED_DEPS_PERCENT.with(
            if (statisticsData.totalDependencies == 0) 0.0f else (statisticsData.resolvedDependencies.toFloat() / statisticsData.totalDependencies.toFloat())),
          ProjectImportCollector.ADDED_MODULES.with(statisticsData.addedModules))
      }
    }
  }

  private fun CoroutineScope.syncStatic(rootProjectFileOrDir: VirtualFile): Deferred<ProjectTree?> = async {

    val tree = ProjectTree()

    try {
      val rootProjectFile = if (rootProjectFileOrDir.isFile) rootProjectFileOrDir else rootProjectFileOrDir.findChild("pom.xml")
      if (rootProjectFile == null) {
        return@async null
      }
      val rootModel = MavenJDOMUtil.read(rootProjectFile, null) ?: return@async null

      // reading
      val rootProjectData = readProject(rootModel, rootProjectFile)
      tree.addRoot(rootProjectData)

      val readPomsJob = launch {
        readRecursively(rootModel, rootProjectFile, rootProjectData, tree)
      }
      readPomsJob.join()

      val interpolatedCache = ConcurrentHashMap<VirtualFile, Deferred<MavenProjectData>>()

      val interpolationJob = launch {
        tree.forEachProject {
          interpolate(it, tree, interpolatedCache)
        }
      }
      interpolationJob.join()

      val resolvedPluginsLockCache = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<MavenPlugin, Boolean>()))
      val meditationJob = launch {
        tree.forEachProject {
          launch {
            resolveBuildModel(it)
            resolveDependencies(it)
            resolveDirectories(it)
            resolvePluginConfigurations(it, resolvedPluginsLockCache)
            applyChangesToProject(it, tree)
          }
        }
      }
      meditationJob.join()
      return@async tree
    }
    catch (e: Exception) {
      MavenLog.LOG.warn(e)
    }
    finally {
      listOf(ProjectImportCollector.LINKED_PROJECTS.with(1),
             ProjectImportCollector.SUBMODULES_COUNT.with(tree.projects().size))
    }
    return@async null


  }

  private fun resolvePluginConfigurations(data: MavenProjectData, resolvedPluginsLockCache: MutableSet<MavenPlugin>) {
    data.plugins.forEach { (_, plugin) ->
      if (resolvedPluginsLockCache.add(plugin)) {
        val config = plugin.configurationElement
        if (config != null) {
          resolveConfiguration(config, data)
        }

        plugin.executions.forEach { exec ->
          val execConfig = exec.configurationElement
          if (execConfig != null) {
            resolveConfiguration(execConfig, data)
          }
        }
      }
    }
  }

  private fun resolveConfiguration(element: Element, data: MavenProjectData) {
    element.children.forEach() { child ->
      if (child.content.size == 1 && child.content[0].cType == Content.CType.Text) {
        child.setText(resolveProperty(data, child.text))
      }
      else if (child.content.size > 0) {
        resolveConfiguration(child, data)
      }
    }
  }

  private fun resolveBuildModel(projectData: MavenProjectData) {
    val template = projectData.mavenModel.build.finalName ?: "${'$'}{project.artifactId}-${'$'}{project.version}"
    projectData.mavenModel.build.finalName = resolveProperty(projectData, template)
                                             ?: "${projectData.mavenId.artifactId}-${projectData.mavenId.version}" // fallback if we cannnot resolve property by any reason
  }


  /**
   * Applies the changes to the given Maven project.
   *
   * @param projectData The Maven project data containing the project, model, and root model.
   */
  private fun applyChangesToProject(projectData: MavenProjectData, tree: ProjectTree) {
    val dependencies = projectData.resolvedDependencies.map {
      val file = if (tree.project(it.id) == null) {
        MavenUtil.makeLocalRepositoryFile(it.id, localRepo, MavenConstants.TYPE_JAR, it.classifier)
      }
      else {
        null
      }

      MavenArtifact(it.id.groupId, it.id.artifactId, it.id.version, it.id.version, MavenConstants.TYPE_JAR, it.classifier, it.scope, false, MavenConstants.TYPE_JAR,
                    file?.toFile(), localRepo.toFile(), true, false)


    }

    applyReadStateToMavenProject(projectData.mavenModel, projectData.mavenProject)

    projectData.mavenProject.updateState(
      dependencies.toList(),
      Properties().apply {
        putAll(projectData.properties)
      },
      projectData.plugins.values.map { MavenPluginInfo(it, null) }.toList(),
    )
  }

  private fun CoroutineScope.interpolate(
    project: MavenProjectData,
    tree: ProjectTree,
    interpolatedCache: ConcurrentHashMap<VirtualFile, Deferred<MavenProjectData>>,
  ): Deferred<MavenProjectData> = async {

    val myDeferred = CompletableDeferred<MavenProjectData>()
    try {
      val concurrentDeferred = interpolatedCache.putIfAbsent(project.file, myDeferred)
      if (concurrentDeferred != null) {
        return@async concurrentDeferred.await()
      }

      val parentId = project.parentId
      if (parentId == null || parentId.isUnknown()) {
        myDeferred.complete(project)
        return@async project
      }
      val parentInReactor = tree.project(parentId)
      if (parentInReactor == null) {
        myDeferred.complete(project)
        return@async project
      }
      else {
        val mavenId = project.mavenId
        var ancestorId = project.parentId
        while (ancestorId != null && !ancestorId.isUnknown()) {
          if (ancestorId == mavenId) {
            throw RuntimeException("Cyclic dependency found: $mavenId")
          }
          ancestorId = tree.project(ancestorId)?.parentId
        }
      }


      val parentInterpolated = interpolate(parentInReactor, tree, interpolatedCache).await()
      if (project.mavenModel.build.finalName == null) {
        project.mavenModel.build.finalName = StringUtil.nullize(parentInterpolated.mavenModel.build.finalName)
      }
      project.declaredDependencies.addAll(parentInterpolated.declaredDependencies)
      project.resolvedDependencyManagement.putAll(parentInterpolated.resolvedDependencyManagement)
      parentInterpolated.plugins.forEach { (id, plugin) ->
        project.plugins.putIfAbsent(id, plugin)
      }



      applyParentProperties(parentInterpolated, project)



      project.dependencyManagement.forEach {
        val version = it.version
        if (version == null) return@forEach
        if (version.startsWith("$")) {
          val versionResolved = resolveProperty(project, version)
          if (!versionResolved.isNullOrBlank()) {
            project.resolvedDependencyManagement[trimVersion(it)] = DependencyData(it.groupId, it.artifactId, versionResolved, it.scope, it.classifier)
          }
        }
        else {
          project.resolvedDependencyManagement[trimVersion(it)] = it
        }
      }

    }
    catch (e: Throwable) {
      MavenLog.LOG.warn(e)

    }
    myDeferred.complete(project)
    return@async project


  }

  private fun applyParentProperties(
    parentInterpolated: MavenProjectData,
    project: MavenProjectData,
  ) {

    parentInterpolated.properties.forEach {
      project.properties.putIfAbsent(it.key, it.value)
    }
    parentInterpolated.mavenId.groupId?.let { project.properties.put("project.parent.groupId", it) }
    parentInterpolated.mavenId.artifactId?.let { project.properties.put("project.parent.artifactId", it) }
    parentInterpolated.mavenId.version?.let { project.properties.put("project.parent.version", it) }
    parentInterpolated.mavenId.version?.let { project.properties.put("project.parent.version", it) }
    parentInterpolated.properties["project.basedir"]?.let { project.properties.put("project.parent.basedir", it) }
    parentInterpolated.properties["project.baseUri"]?.let { project.properties.put("project.parent.baseUri", it) }
  }

  private fun resolveDependencies(project: MavenProjectData) {
    project.declaredDependencies.forEach {
      val version = it.version
      if (version == null) {
        val id = project.resolvedDependencyManagement[trimVersion(it)]
        if (id != null && id.version != null) {
          project.resolvedDependencies.add(id)
        }
      }
      else if (version.startsWith("$")) {
        val versionResolved = resolveProperty(project, version)
        if (!versionResolved.isNullOrBlank()) {
          project.resolvedDependencies.add(DependencyData(it.groupId, it.artifactId, versionResolved, it.scope, it.classifier))
        }
      }
      else {
        project.resolvedDependencies.add(it)
      }

    }
  }

  /**
   * Resolves properties recursively. If any resolution fails, it returns null.
   * This helps avoid a situation where the root directory is added as a source root
   * in complex cases involving variables and properties
   */
  private fun resolveProperty(project: MavenProjectData, propertyValue: String): String? {

    var value = propertyValue
    val recursionProtector = HashSet<String>()
    while (recursionProtector.add(value)) {
      val start = value.indexOf("${'$'}{")
      if (start == -1) return value
      val end = value.indexOf("}")
      if (start + 2 >= end) return null // some syntax error probably
      val variable = value.substring(start + 2, end)
      val resolvedValue = doResolveVariable(project.properties, variable) ?: return null
      if (start == 0 && end == value.length - 1) {
        value = resolvedValue
        continue
      }
      val tail = if (end == value.length - 1) {
        ""
      }
      else {
        value.substring(end + 1, value.length)
      }
      value = value.substring(0, start) + resolvedValue + tail
    }
    return value
  }

  private fun doResolveVariable(properties: HashMap<String, String>, variable: String): String? {
    properties[variable]?.let { return it }
    if (variable.startsWith("env.")) {
      val env = variable.substring(4)
      return when {
        env.isNotBlank() -> System.getenv(env).nullize(true)
        else -> null
      }
    }
    return System.getProperty(variable).nullize(true)
  }


  private fun CoroutineScope.readRecursively(
    parentModel: Element,
    aggregatorProjectFile: VirtualFile,
    aggregatorProject: MavenProjectData,
    tree: ProjectTree,
  ): Job = this.launch Read@{
    val modulesList = parentModel.getChildrenText("modules", "module")
    modulesList.forEach {
      this.launch {
        try {
          val file = aggregatorProjectFile.parent.findFileOrDirectory(it)?.let { fod ->
            if (fod.isDirectory) fod.findChild(MavenConstants.POM_XML) else fod
          }
          if (file == null) return@launch
          val rootModel = MavenJDOMUtil.read(file, null) ?: return@launch
          val mavenProjectData = readProject(rootModel, file)
          tree.addChild(aggregatorProject, mavenProjectData)
          readRecursively(rootModel, file, mavenProjectData, tree)
        }
        catch (e: Throwable) {
          MavenLog.LOG.error(e)
        }

      }
    }
  }

  private fun readProject(rootModel: Element, file: VirtualFile): MavenProjectData {
    val mavenModel = MavenModel()
    val mavenProject = MavenProject(file)

    val dependencyManagement = ArrayList<DependencyData>()
    val declaredDependencies = ArrayList<DependencyData>()
    val properties = HashMap<String, String>()

    rootModel.getChild("properties")?.children?.forEach {
      mavenModel.properties.setProperty(it.name, it.textTrim)
      properties[it.name] = it.textTrim
    }


    val parent = MavenParent(MavenId(MavenJDOMUtil.findChildValueByPath(rootModel, "parent.groupId", null),
                                     MavenJDOMUtil.findChildValueByPath(rootModel, "parent.artifactId", null),
                                     MavenJDOMUtil.findChildValueByPath(rootModel, "parent.version", null)),
                             MavenJDOMUtil.findChildValueByPath(rootModel, "parent.relativePath", "../pom.xml"))
    mavenModel.parent = parent

    val id = MavenId(MavenJDOMUtil.findChildValueByPath(rootModel, "groupId", parent.mavenId.groupId),
                     MavenJDOMUtil.findChildValueByPath(rootModel, "artifactId", null),
                     MavenJDOMUtil.findChildValueByPath(rootModel, "version", parent.mavenId.version))
    val parentFolder = file.parent.toNioPath()
    mavenModel.mavenId = id
    mavenModel.name = MavenJDOMUtil.findChildValueByPath(rootModel, "name", id.artifactId)
    mavenModel.build.finalName = MavenJDOMUtil.findChildValueByPath(rootModel, "build.finalName")

    mavenModel.modules = rootModel.getChildrenText("modules", "module")
    mavenModel.packaging = rootModel.getChildTextTrim("packaging") ?: "jar"

    mavenModel.build.directory = parentFolder.resolve("target").toString()
    mavenModel.build.outputDirectory = parentFolder.resolve("target/classes").toString()
    mavenModel.build.testOutputDirectory = parentFolder.resolve("target/test-classes").toString()

    declaredDependencies.addAll(
      MavenJDOMUtil.findChildrenByPath(rootModel, "dependencies", "dependency").map {
        DependencyData(extractId(it), it.getChildTextTrim("scope"), it.getChildTextTrim("classifier"))
      }
    )

    dependencyManagement.addAll(
      MavenJDOMUtil.findChildrenByPath(rootModel.getChild("dependencyManagement"), "dependencies", "dependency")
        .map { DependencyData(extractId(it), it.getChildTextTrim("scope"), it.getChildTextTrim("classifier")) }
    )

    applyReadStateToMavenProject(mavenModel, mavenProject)

    val plugins = readPlugins(rootModel)


    return MavenProjectData(mavenProject, mavenModel, rootModel).apply {
      val mavenProjectData = this
      this.plugins.putAll(plugins)
      this.dependencyManagement.addAll(dependencyManagement)
      this.declaredDependencies.addAll(declaredDependencies)
      this.properties.apply {
        val basedir = parentFolder.toString()
        val baseUri = parentFolder.toUri().toString()
        this["basedir"] = basedir
        this["baseUri"] = baseUri

        this["project.basedir"] = basedir
        this["project.baseUri"] = baseUri
        this["project.artifactId"] = mavenProjectData.mavenId.artifactId ?: ""
        this["project.groupId"] = mavenProjectData.mavenId.groupId ?: ""
        this["project.version"] = mavenProjectData.mavenId.version ?: ""
        this["project.name"] = mavenProjectData.mavenModel.name ?: ""
        putAll(properties)
      }
    }

  }

  private fun applyReadStateToMavenProject(mavenModel: MavenModel, mavenProject: MavenProject) {
    val modelMap = HashMap<String, String>()
    mavenModel.mavenId.groupId?.let { modelMap.put("groupId", it) }
    mavenModel.mavenId.artifactId?.let { modelMap.put("artifactId", it) }
    mavenModel.mavenId.version?.let { modelMap.put("version", it) }
    modelMap["build.outputDirectory"] = mavenModel.build.outputDirectory
    modelMap["build.testOutputDirectory"] = mavenModel.build.testOutputDirectory
    //modelMap["build.finalName"] = mavenModel.build.finalName
    modelMap["build.directory"] = mavenModel.build.directory
    val result = MavenProjectReaderResult(mavenModel, modelMap, MavenExplicitProfiles.NONE, mutableListOf())
    mavenProject.updateFromReaderResult(result, MavenProjectsManager.getInstance(project).generalSettings, false)
  }

  private fun resolveDirectories(mavenProjectData: MavenProjectData) {

    val sources = ArrayList<String>()
    val testSources = ArrayList<String>()

    val sourceDirectory = MavenJDOMUtil.findChildValueByPath(mavenProjectData.rootModel, "build.sourceDirectory", "src/main/java")!!
    val testSourceDirectory = MavenJDOMUtil.findChildValueByPath(mavenProjectData.rootModel, "build.testSourceDirectory", "src/test/java")!!


    sources.add(sourceDirectory)
    testSources.add(testSourceDirectory)

    resolveKotlinPlugin(mavenProjectData, sources, testSources)
    resolveBuildHelperPlugin(mavenProjectData, sources, testSources)

    val parentFolder = mavenProjectData.file.parent.toNioPath()


    mavenProjectData.mavenModel.build.directory = parentFolder.resolve("target").toString()
    mavenProjectData.mavenModel.build.outputDirectory = parentFolder.resolve("target/classes").toString()
    mavenProjectData.mavenModel.build.testOutputDirectory = parentFolder.resolve("target/test-classes").toString()
    mavenProjectData.mavenModel.build.sources = sources
      .mapNotNull { resolveProperty(mavenProjectData, it) }
      .map(parentFolder::resolve)
      .map(Path::toString)
    mavenProjectData.mavenModel.build.testSources = testSources
      .mapNotNull { resolveProperty(mavenProjectData, it) }
      .map(parentFolder::resolve)
      .map(Path::toString)
  }

  private fun resolveKotlinPlugin(
    mavenProjectData: MavenProjectData,
    sources: ArrayList<String>,
    testSources: ArrayList<String>,
  ) {
    val kotlinPlugin = findPlugin(mavenProjectData, "org.jetbrains.kotlin", "kotlin-maven-plugin")
    if (kotlinPlugin != null) {
      sources.add("src/main/kotlin")
      testSources.add("src/test/kotlin")
    }
  }

  private fun resolveBuildHelperPlugin(
    mavenProjectData: MavenProjectData,
    sources: ArrayList<String>,
    testSources: ArrayList<String>,
  ) {
    val executions = findPlugin(mavenProjectData, "org.codehaus.mojo", "build-helper-maven-plugin")?.executions
    if (executions.isNullOrEmpty()) return

    executions.filter { it.goals.contains("add-source") }
      .mapNotNull { it.configurationElement }
      .flatMap { it.getChildrenText("sources", "source") }
      .forEach { sources.add(it) }

    executions.filter { it.goals.contains("add-test-source") }
      .mapNotNull { it.configurationElement }
      .flatMap { it.getChildrenText("sources", "source") }
      .forEach { testSources.add(it) }
  }

  private fun findPlugin(mavenProjectData: MavenProjectData, groupId: String, artifactId: String) =
    mavenProjectData.plugins[MavenId(groupId, artifactId, null)]


  private fun readPlugins(rootModel: Element): Map<MavenId, MavenPlugin> {
    val plugins = HashMap<MavenId, MavenPlugin>()
    MavenJDOMUtil.findChildrenByPath(rootModel, "build.plugins", "plugin").forEach {
      val executions = it.getChild("executions")
                         ?.getChildren("execution")
                         ?.map { execution ->
                           MavenPlugin.Execution(
                             execution.getChildText("id"),
                             execution.getChildText("phase"),
                             execution.getChildrenText("goals", "goal"),
                             execution.getChild("configuration")
                           )
                         } ?: emptyList()

      val id = extractId(it)
      val groupId = id.groupId.nullize(true) ?: "org.apache.maven.plugins"
      val plugin = MavenPlugin(groupId, id.artifactId, id.version, false, false, it.getChild("configuration"), executions, emptyList())
      plugins[trimVersion(id)] = plugin
    }
    return plugins
  }


  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenProjectStaticImporter = project.service()

    @JvmStatic
    fun setPreimport(value: Boolean) {
      Registry.get("maven.preimport.project").setValue(value)
    }
  }
}

@OptIn(EntityStorageInstrumentationApi::class)
private class StatisticsData(val project: Project, val rootProjects: Int) {

  val modulesBefore = (WorkspaceModel.getInstance(project).currentSnapshot as ImmutableEntityStorageInstrumentation)
    .entityCount(ModuleEntity::class.java)

  fun add(forest: List<ProjectTree>, allProjects: List<MavenProject>) {
    val time = System.currentTimeMillis()
    try {
      linkedProject = forest.size
      resolvedDependencies = forest.flatMap { it.projectsData() }.sumOf { it.resolvedDependencies.size }
      totalDependencies = forest.flatMap { it.projectsData() }.sumOf { it.declaredDependencies.size }
      addedModules = (WorkspaceModel.getInstance(project).currentSnapshot as ImmutableEntityStorageInstrumentation)
                       .entityCount(ModuleEntity::class.java) - modulesBefore
      totalProjects = allProjects.size

    }
    finally {
      MavenLog.LOG.info("preimport statistics: " +
                        "linked: $linkedProject of $rootProjects with total $totalProjects modules " +
                        "interpolated $resolvedDependencies of $totalDependencies, " +
                        "$addedModules modules added. This statistics calculated for ${System.currentTimeMillis() - time} millis")
    }
  }

  var linkedProject = 0
  var resolvedDependencies = 0
  var totalDependencies = 0
  var totalProjects = 0
  var addedModules = 0


}

private fun Element.getChildrenText(s: String, c: String): List<String> {
  return this.getChild(s)?.getChildren(c)?.map { it.textTrim } ?: emptyList()

}

private class ProjectTree {
  var root: MavenProject? = null
  private val tree = HashMap<VirtualFile, MutableList<MavenProjectData>>()
  private val allProjects = HashMap<VirtualFile, MavenProjectData>()
  private val fullMavenIds = HashMap<MavenId, MavenProjectData>()
  private val managedMavenIds = HashMap<MavenId, MavenProjectData>()

  fun projects() = allProjects.values.map { it.mavenProject }
  fun projectsData() = allProjects.values

  private val mutex = Mutex(false)

  suspend fun addRoot(root: MavenProjectData) {
    mutex.withLock {
      this.root = root.mavenProject
      allProjects[root.file] = root
      fullMavenIds[root.mavenId] = root
      managedMavenIds[trimVersion(root.mavenId)] = root
    }
  }

  suspend fun addChild(aggregator: MavenProjectData, child: MavenProjectData) {
    mutex.withLock {
      tree.compute(aggregator.file) { _, v ->
        val arr = v ?: ArrayList()
        arr.also { it.add(child) }
      }
      allProjects[child.file] = child
      fullMavenIds[child.mavenId] = child
      managedMavenIds[trimVersion(child.mavenId)] = child
    }
  }


  fun project(id: MavenId): MavenProjectData? {
    fullMavenIds[id]?.let { return it }
    return managedMavenIds[trimVersion(id)]
  }

  fun forEachProject(action: (MavenProjectData) -> Unit) = allProjects.values.forEach(action)
  fun mavenProjectMappings(): Map<MavenProject, List<MavenProject>> {
    return allProjects.mapNotNull { (file, data) ->
      tree[file]?.map { it.mavenProject }?.let { data.mavenProject to it }
    }.toMap()
  }
}

private class MavenProjectData(val mavenProject: MavenProject, val mavenModel: MavenModel, val rootModel: Element) {
  val dependencyManagement = ArrayList<DependencyData>()
  val declaredDependencies = ArrayList<DependencyData>()
  val resolvedDependencyManagement = HashMap<MavenId, DependencyData>()
  val properties = HashMap<String, String>()
  val resolvedDependencies = ArrayList<DependencyData>()

  val plugins = HashMap<MavenId, MavenPlugin>()

  val mavenId by lazy { mavenProject.mavenId }
  val parentId by lazy { mavenProject.parentId }
  val file = mavenProject.file
}


private data class DependencyData(val id: MavenId, val scope: String?, val classifier: String?) {

  constructor(groupId: String?, artifactId: String?, version: String?, scope: String?, classifier: String?) : this(MavenId(groupId, artifactId, version), scope, classifier)

  val groupId = id.groupId
  val artifactId = id.artifactId
  val version = id.version
}

private fun trimVersion(id: MavenId) = MavenId(id.groupId, id.artifactId, null)
private fun trimVersion(data: DependencyData) = trimVersion(data.id)

private fun extractId(e: Element): MavenId = MavenId(e.getChildTextTrim("groupId"),
                                                     e.getChildTextTrim("artifactId"),
                                                     e.getChildTextTrim("version"))

private fun MavenId.isUnknown() = artifactId == null && version == null && groupId == null

