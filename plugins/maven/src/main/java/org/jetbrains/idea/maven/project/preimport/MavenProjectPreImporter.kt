// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.preimport

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector.PREIMPORT_ACTIVITY
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class MavenProjectPreImporter(val project: Project, val coroutineScope: CoroutineScope) {
  private val localRepo = MavenProjectsManager.getInstance(project).localRepository

  suspend fun preimport(rootProjectFiles: List<VirtualFile>,
                        optionalModelsProvider: IdeModifiableModelsProvider?,
                        importingSettings: MavenImportingSettings,
                        generalSettings: MavenGeneralSettings,
                        parentActivity: StructuredIdeActivity): List<Module> {


    val activity = PREIMPORT_ACTIVITY.startedWithParent(project, parentActivity)
    val statisticsData = StatisticsData(project, rootProjectFiles.size)
    try {
      val scope = coroutineScope.childScope(Dispatchers.IO, false)

      val forest = rootProjectFiles.map {
        scope.preimport(it)
      }.awaitAll().filterNotNull()
      if (forest.isEmpty()) return emptyList()


      val projectTree = MavenProjectsTree(project)
      val roots = ArrayList<MavenProject>()
      val mavenProjectMappings = HashMap<MavenProject, List<MavenProject>>()
      val allProjects = ArrayList<MavenProject>()
      val projectChanges = HashMap<MavenProject, MavenProjectChanges>()
      val existingTree = MavenProjectsManager.getInstance(project).let { if (it.isMavenizedProject) it.projectsTree else null }

      forest.forEach { tree ->
        mavenProjectMappings.putAll(tree.mavenProjectMappings())
        allProjects.addAll(tree.projects())
        tree.root?.let(roots::add)

        if (existingTree == null) {
          projectChanges.putAll(tree.projects().associateWith { MavenProjectChanges.ALL })
        }
        else {
          projectChanges.putAll(
            tree.projects().filter { existingTree.findProject(it.file) == null }.associateWith { MavenProjectChanges.ALL })
        }
      }


      projectTree.updater()
        .setRootProjects(roots)
        .setManagedFiles(roots.map { it.file.path })
        .setAggregatorMappings(mavenProjectMappings)
        .setMavenIdMappings(allProjects)

      val modelsProvider = optionalModelsProvider ?: ProjectDataManager.getInstance().createModifiableModelsProvider(project)
      // MavenProjectsManager.getInstance(project).projectsTree = projectTree
      return withBackgroundProgress(project, MavenProjectBundle.message("maven.project.importing"), false) {
        blockingContext {
          val importer = MavenProjectImporter.createImporter(project, projectTree,
                                                             projectChanges,
                                                             modelsProvider,
                                                             importingSettings,
                                                             null,
                                                             parentActivity)

          importer.importProject()
          statisticsData.add(forest, allProjects)
          return@blockingContext importer.createdModules()

        }
      }
    }
    catch (e: Throwable) {
      MavenLog.LOG.error(e)
      return emptyList()
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

  private fun CoroutineScope.preimport(rootProjectFileOrDir: VirtualFile): Deferred<ProjectTree?> = async {

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

      val meditationJob = launch {
        tree.forEachProject {
          launch {
            resolveDependencies(it)
            resolveDirectories(it)
            applyChangesToProject(it)
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


  private fun applyChangesToProject(projectData: MavenProjectData) {
    val dependencies = projectData.resolvedDependencies.map {
      val file = MavenUtil.makeLocalRepositoryFile(it, localRepo, MavenConstants.TYPE_JAR, null)
      MavenArtifact(it.groupId, it.artifactId, it.version, null, MavenConstants.TYPE_JAR, null, null, false, MavenConstants.TYPE_JAR,
                    file, localRepo, true, false);

    }

    applyReadStateToMavenProject(projectData.mavenModel, projectData.mavenProject)

    projectData.mavenProject.updater()
      .setDependencies(dependencies)
      .setPlugins(projectData.plugins.values.toList())
      .setProperties(Properties().apply { this.putAll(projectData.properties) })
  }

  private fun CoroutineScope.interpolate(project: MavenProjectData,
                                         tree: ProjectTree,
                                         interpolatedCache: ConcurrentHashMap<VirtualFile, Deferred<MavenProjectData>>): Deferred<MavenProjectData> = async {

    val myDeferred = CompletableDeferred<MavenProjectData>()
    try {
      val concurrentDeferred = interpolatedCache.putIfAbsent(project.file, myDeferred)
      if (concurrentDeferred != null) {
        return@async concurrentDeferred.await()
      }

      val parentId = project.parentId
      if (parentId == null) {
        myDeferred.complete(project)
        return@async project
      }
      val parentInReactor = tree.project(parentId)
      if (parentInReactor == null) {
        myDeferred.complete(project)
        return@async project
      }


      val parentInterpolated = interpolate(parentInReactor, tree, interpolatedCache).await()
      project.resolvedDependencyManagement.putAll(parentInterpolated.resolvedDependencyManagement)
      parentInterpolated.plugins.forEach { (id, plugin) ->
        project.plugins.putIfAbsent(id, plugin)
      }
      project.properties.putAll(parentInterpolated.properties)
      project.dependencyManagement.forEach {
        val version = it.version
        if (version == null) return@forEach
        if (version.startsWith("$")) {
          val versionResolved = resolveProperty(project, version)
          if (versionResolved.isNotBlank()) {
            project.resolvedDependencyManagement[trimVersion(it)] = MavenId(it.groupId, it.artifactId, versionResolved)
          }
        }
        else {
          project.resolvedDependencyManagement[trimVersion(it)] = it
        }
      }

    }
    catch (e: Throwable) {
      MavenLog.LOG.error(e)

    }
    myDeferred.complete(project)
    return@async project


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
        if (versionResolved.isNotBlank()) {
          project.resolvedDependencies.add(MavenId(it.groupId, it.artifactId, versionResolved))
        }
      }
      else {
        project.resolvedDependencies.add(it)
      }

    }
  }

  private fun resolveProperty(project: MavenProjectData, value: String): String {
    val start = value.indexOf("${'$'}{")
    if (start == -1) return value
    val end = value.indexOf("}")
    if (start + 2 >= end) return value
    val variable = value.substring(start + 2, end)
    val resolvedValue = project.properties[variable] ?: ""
    if (start == 0 && end == value.length - 1) {
      return resolveProperty(project, resolvedValue)
    }
    val tail = if (end == value.length - 1) {
      ""
    }
    else {
      value.substring(end + 1, value.length)
    }
    return resolveProperty(project, value.substring(0, start) + resolvedValue + tail)
  }


  private fun CoroutineScope.readRecursively(parentModel: Element,
                                             aggregatorProjectFile: VirtualFile,
                                             aggregatorProject: MavenProjectData,
                                             tree: ProjectTree): Job = this.launch Read@{
    val modulesList = parentModel.getChildrenText("modules", "module")
    modulesList.forEach {
      this.launch {
        try {
          val file = aggregatorProjectFile.parent.findFile("$it/pom.xml")
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

    val dependencyManagement = ArrayList<MavenId>()
    val declaredDependencies = ArrayList<MavenId>()
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
    mavenModel.name = file.parent.name
    mavenModel.build.finalName = file.parent.name
    mavenModel.modules = rootModel.getChildrenText("modules", "module")
    mavenModel.packaging = rootModel.getChildTextTrim("packaging") ?: "jar"

    mavenModel.build.directory = parentFolder.resolve("target").toString()
    mavenModel.build.outputDirectory = parentFolder.resolve("target/classes").toString()
    mavenModel.build.testOutputDirectory = parentFolder.resolve("target/test-classes").toString()

    MavenJDOMUtil.findChildrenByPath(rootModel, "dependencies", "dependency")?.map { extractId(it) }
    declaredDependencies.addAll(
      MavenJDOMUtil.findChildrenByPath(rootModel, "dependencies", "dependency").map { extractId(it) }
    )

    dependencyManagement.addAll(
      MavenJDOMUtil.findChildrenByPath(rootModel.getChild("dependencyManagement"), "dependencies", "dependency")
        .map { extractId(it) }
    )

    applyReadStateToMavenProject(mavenModel, mavenProject)

    val plugins = readPlugins(rootModel)


    return MavenProjectData(mavenProject, mavenModel, rootModel).apply {
      this.plugins.putAll(plugins)
      this.dependencyManagement.addAll(dependencyManagement)
      this.declaredDependencies.addAll(declaredDependencies)
      this.properties.putAll(properties)
    }

  }

  private fun applyReadStateToMavenProject(mavenModel: MavenModel, mavenProject: MavenProject) {
    val modelMap = HashMap<String, String>()
    mavenModel.mavenId.groupId?.let { modelMap.put("groupId", it) }
    mavenModel.mavenId.artifactId?.let { modelMap.put("artifactId", it) }
    mavenModel.mavenId.version?.let { modelMap.put("version", it) }
    modelMap["build.outputDirectory"] = mavenModel.build.outputDirectory
    modelMap["build.testOutputDirectory"] = mavenModel.build.testOutputDirectory
    modelMap["build.finalName"] = mavenModel.build.finalName
    modelMap["build.directory"] = mavenModel.build.directory
    val result = MavenProjectReaderResult(mavenModel, modelMap, MavenExplicitProfiles.NONE, null, emptyList(), emptySet())
    mavenProject.set(result, MavenProjectsManager.getInstance(project).generalSettings, true, true, true)
  }

  private fun resolveDirectories(mavenProjectData: MavenProjectData) {

    val sources = ArrayList<String>()
    val testSources = ArrayList<String>()

    val sourceDirectory = MavenJDOMUtil.findChildValueByPath(mavenProjectData.rootModel, "build.sourceDirectory", "src/main/java")
    val testSourceDirectory = MavenJDOMUtil.findChildValueByPath(mavenProjectData.rootModel, "build.testSourceDirectory", "src/test/java")


    sources.add(sourceDirectory)
    testSources.add(testSourceDirectory)

    resolveKotlinPlugin(mavenProjectData, sources, testSources)
    resolveBuildHelperPlugin(mavenProjectData, sources, testSources)

    val parentFolder = mavenProjectData.file.parent.toNioPath()


    mavenProjectData.mavenModel.build.directory = parentFolder.resolve("target").toString()
    mavenProjectData.mavenModel.build.outputDirectory = parentFolder.resolve("target/classes").toString()
    mavenProjectData.mavenModel.build.testOutputDirectory = parentFolder.resolve("target/test-classes").toString()
    mavenProjectData.mavenModel.build.sources = sources
      .map { resolveProperty(mavenProjectData, it) }
      .map(parentFolder::resolve)
      .map(Path::toString)
    mavenProjectData.mavenModel.build.testSources = testSources
      .map { resolveProperty(mavenProjectData, it) }
      .map(parentFolder::resolve)
      .map(Path::toString)
  }

  private fun resolveKotlinPlugin(mavenProjectData: MavenProjectData,
                                  sources: ArrayList<String>,
                                  testSources: ArrayList<String>) {
    val kotlinPlugin = findPlugin(mavenProjectData, "org.jetbrains.kotlin", "kotlin-maven-plugin")
    if (kotlinPlugin != null) {
      sources.add("src/main/kotlin")
      testSources.add("src/test/kotlin")
    }
  }

  private fun resolveBuildHelperPlugin(mavenProjectData: MavenProjectData,
                                       sources: ArrayList<String>,
                                       testSources: ArrayList<String>) {
    val executions = findPlugin(mavenProjectData, "org.codehaus.mojo", "build-helper-maven-plugin")?.executions
    if (executions.isNullOrEmpty()) return;

    executions.filter { it.goals.contains("add-source") }
      .mapNotNull { it.configurationElement }
      .flatMap { it.getChildrenText("sources", "source") }
      .forEach { sources.add(it) }

    executions.filter { it.goals.contains("add-test-source") }
      .mapNotNull { it.configurationElement }
      .flatMap { it.getChildrenText("sources", "source") }
      .forEach { sources.add(it) }
  }

  private fun findPlugin(mavenProjectData: MavenProjectData, groupId: String, artifactId: String) =
    mavenProjectData.plugins[MavenId(groupId, artifactId, null)]


  private fun readPlugins(rootModel: Element): Map<MavenId, MavenPlugin> {
    val plugins = HashMap<MavenId, MavenPlugin>()
    MavenJDOMUtil.findChildrenByPath(rootModel, "build.plugins", "plugin")?.forEach {
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
      val plugin = MavenPlugin(id.groupId, id.artifactId, id.version, false, false, it.getChild("configuration"), executions, emptyList())
      plugins[trimVersion(id)] = plugin
    }
    return plugins
  }


  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenProjectPreImporter = project.service()

    @JvmStatic
    fun setPreimport(value: Boolean) {
      Registry.get("maven.preimport.project").setValue(value)
    }
  }
}

private class StatisticsData(val project: Project, val rootProjects: Int) {

  val modulesBefore = WorkspaceModel.getInstance(project).currentSnapshot.entitiesAmount(ModuleEntity::class.java)
  fun add(forest: List<ProjectTree>, allProjects: ArrayList<MavenProject>) {
    val time = System.currentTimeMillis()
    try {
      linkedProject = forest.size
      resolvedDependencies = forest.flatMap { it.projectsData() }.sumOf { it.resolvedDependencies.size }
      totalDependencies = forest.flatMap { it.projectsData() }.sumOf { it.declaredDependencies.size }
      addedModules = WorkspaceModel.getInstance(project).currentSnapshot.entitiesAmount(ModuleEntity::class.java) - modulesBefore
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

class ProjectTree {
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

class MavenProjectData(val mavenProject: MavenProject, val mavenModel: MavenModel, val rootModel: Element) {
  val dependencyManagement = ArrayList<MavenId>()
  val declaredDependencies = ArrayList<MavenId>()
  val resolvedDependencyManagement = HashMap<MavenId, MavenId>()
  val properties = HashMap<String, String>()
  val resolvedDependencies = ArrayList<MavenId>()

  val plugins = HashMap<MavenId, MavenPlugin>()

  val mavenId by lazy { mavenProject.mavenId }
  val parentId by lazy { mavenProject.parentId }
  val file = mavenProject.file
}

private fun trimVersion(id: MavenId) = MavenId(id.groupId, id.artifactId, null)

private fun extractId(e: Element): MavenId = MavenId(e.getChildTextTrim("groupId"),
                                                     e.getChildTextTrim("artifactId"),
                                                     e.getChildTextTrim("version"))

