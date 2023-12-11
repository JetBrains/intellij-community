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
      val rootProjectData = readProject(rootModel, rootProjectFile);
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
          }
        }
      }
      meditationJob.join()
      return@async tree;
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
      val projectInReactor = tree.project(parentId)
      if (projectInReactor == null) {
        myDeferred.complete(project)
        return@async project
      }


      val parentInterpolated = interpolate(projectInReactor, tree, interpolatedCache).await()
      project.resolvedDependencyManagement.putAll(parentInterpolated.resolvedDependencyManagement)
      project.allPlugins.putAll(parentInterpolated.allPlugins)
      project.allPlugins.putAll(project.declaredPlugins)
      project.properties.putAll(parentInterpolated.properties)
      project.dependencyManagement.forEach {
        val version = it.version
        if (version == null) return@forEach
        if (version.startsWith("$")) {
          val versionResolved = project.properties[version.substring(2, version.length - 1)]
          if (versionResolved != null) {
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
        val versionResolved = project.properties[version.substring(2, version.length - 1)]
        if (versionResolved != null) {
          project.resolvedDependencies.add(MavenId(it.groupId, it.artifactId, versionResolved));
        }
      }
      else {
        project.resolvedDependencies.add(it)
      }

    }

    project.mavenProject.updater().setDependencies(
      project.resolvedDependencies.map {
        val file = MavenUtil.makeLocalRepositoryFile(it, localRepo, MavenConstants.TYPE_JAR, null)
        MavenArtifact(it.groupId, it.artifactId, it.version, null, MavenConstants.TYPE_JAR, null, null, false, MavenConstants.TYPE_JAR,
                      file, localRepo, true, false)
      })
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
    val parentFolder = file.parent.toNioPath()
    val mavenModel = MavenModel()
    val mavenProject = MavenProject(file)
    val mavenProjectData = MavenProjectData(mavenProject)

    rootModel.getChild("properties")?.getChildren()?.forEach {
      mavenModel.properties.setProperty(it.name, it.textTrim)
      mavenProjectData.properties[it.name] = it.textTrim
    }


    val parent = MavenParent(MavenId(MavenJDOMUtil.findChildValueByPath(rootModel, "parent.groupId", null),
                                     MavenJDOMUtil.findChildValueByPath(rootModel, "parent.artifactId", null),
                                     MavenJDOMUtil.findChildValueByPath(rootModel, "parent.version", null)),
                             MavenJDOMUtil.findChildValueByPath(rootModel, "parent.relativePath", "../pom.xml"))
    mavenModel.parent = parent

    val id = MavenId(MavenJDOMUtil.findChildValueByPath(rootModel, "groupId", parent.mavenId.groupId),
                     MavenJDOMUtil.findChildValueByPath(rootModel, "artifactId", null),
                     MavenJDOMUtil.findChildValueByPath(rootModel, "version", parent.mavenId.version))
    mavenModel.mavenId = id
    mavenModel.name = file.parent.name
    mavenModel.build.finalName = file.parent.name
    mavenModel.modules = rootModel.getChildrenText("modules", "module")
    mavenModel.packaging = rootModel.getChildTextTrim("packaging") ?: "jar"

    MavenJDOMUtil.findChildrenByPath(rootModel, "dependencies", "dependency")?.map { extractId(it) }
    mavenProjectData.declaredDependencies.addAll(
      MavenJDOMUtil.findChildrenByPath(rootModel, "dependencies", "dependency").map { extractId(it) }
    )

    mavenProjectData.dependencyManagement.addAll(
      MavenJDOMUtil.findChildrenByPath(rootModel.getChild("dependencyManagement"), "dependencies", "dependency")
        .map { extractId(it) }
    )

    val modelMap = HashMap<String, String>()
    mavenModel.mavenId.groupId?.let { modelMap.put("groupId", it) }
    mavenModel.mavenId.artifactId?.let { modelMap.put("artifactId", it) }
    mavenModel.mavenId.version?.let { modelMap.put("version", it) }

    readPlugins(mavenProjectData, rootModel)

    resolveDirectories(mavenModel, parentFolder)

    modelMap.put("build.outputDirectory", mavenModel.build.outputDirectory)
    modelMap.put("build.testOutputDirectory", mavenModel.build.testOutputDirectory)
    modelMap.put("build.finalName", mavenModel.build.finalName)
    modelMap.put("build.directory", mavenModel.build.directory)

    val result = MavenProjectReaderResult(mavenModel, modelMap, MavenExplicitProfiles.NONE, null, emptyList(), emptySet())
    mavenProject.set(result, MavenProjectsManager.getInstance(project).generalSettings, true, true, true);
    return mavenProjectData;

  }

  private fun resolveDirectories(mavenModel: MavenModel, parentFolder: Path) {
    mavenModel.build.directory = parentFolder.resolve("target").toString()
    mavenModel.build.outputDirectory = parentFolder.resolve("target/classes").toString()
    mavenModel.build.testOutputDirectory = parentFolder.resolve("target/test-classes").toString()
    mavenModel.build.sources = listOf(parentFolder.resolve("src/main/java").toString(), parentFolder.resolve("src/main/kotlin").toString())
    mavenModel.build.testSources = listOf(parentFolder.resolve("src/test/java").toString(),
                                          parentFolder.resolve("src/test/kotlin").toString());
  }

  private fun readPlugins(mavenProjectData: MavenProjectData, rootModel: Element) {
    MavenJDOMUtil.findChildrenByPath(rootModel, "build.plugins", "plugin")?.forEach {
      val id = extractId(it)
      val plugin = MavenPlugin(id.groupId, id.artifactId, id.version, false, false, it.getChild("configuration"), emptyList(), emptyList())
      mavenProjectData.declaredPlugins[trimVersion(id)] = plugin
    }
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
      tree.compute(aggregator.file) { k, v ->
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

class MavenProjectData(val mavenProject: MavenProject) {
  val dependencyManagement = ArrayList<MavenId>()
  val declaredDependencies = ArrayList<MavenId>()
  val resolvedDependencyManagement = HashMap<MavenId, MavenId>()
  val properties = HashMap<String, String>()
  val resolvedDependencies = ArrayList<MavenId>()

  val allPlugins = HashMap<MavenId, MavenPlugin>()
  val declaredPlugins = HashMap<MavenId, MavenPlugin>()

  val mavenId by lazy { mavenProject.mavenId }
  val parentId by lazy { mavenProject.parentId }
  val file = mavenProject.file
}

private fun trimVersion(id: MavenId) = MavenId(id.groupId, id.artifactId, null)

private fun extractId(e: Element): MavenId = MavenId(e.getChildTextTrim("groupId"),
                                                     e.getChildTextTrim("artifactId"),
                                                     e.getChildTextTrim("version"))

