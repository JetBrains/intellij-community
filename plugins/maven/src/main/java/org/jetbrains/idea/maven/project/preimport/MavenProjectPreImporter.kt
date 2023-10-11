// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.preimport

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jdom.Element
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.ConcurrentHashMap


@Service(Service.Level.PROJECT)
class MavenProjectPreImporter(val project: Project, val coroutineScope: CoroutineScope) {
  private val localRepo = project.service<MavenProjectsManager>().localRepository

  suspend fun preimport(rootProjectFile: VirtualFile,
                        optionalModelsProvider: IdeModifiableModelsProvider?,
                        importingSettings: MavenImportingSettings,
                        generalSettings: MavenGeneralSettings) {

    val time = System.currentTimeMillis();
    println("preimport started on $time")
    val activity = ProjectImportCollector.IMPORT_ACTIVITY.started(project)
    val tree = ProjectTree()
    try {
      val rootModel = MavenJDOMUtil.read(rootProjectFile, null) ?: return

      // reading
      val scope = coroutineScope.childScope(Dispatchers.IO, false)
      val rootProjectData = readProject(rootModel, rootProjectFile);
      tree.addRoot(rootProjectData)

      val readPomsJob = scope.launch {
        readRecursively(rootModel, rootProjectFile, rootProjectData, tree)
      }
      readPomsJob.join()

      val interpolatedCache = ConcurrentHashMap<VirtualFile, Deferred<MavenProjectData>>()

      val interpolationJob = scope.launch {
        tree.forEachProject {
          interpolate(it, tree, interpolatedCache)
        }
      }
      interpolationJob.join()

      val meditationJob = scope.launch {
        tree.forEachProject {
          launch {
            resolveDependencies(it)
          }
        }
      }
      meditationJob.join()

      val projectTree = MavenProjectsTree(project)
      projectTree.updater()
        .setRootProject(rootProjectData.mavenProject)
        .setManagedFiles(listOf(rootProjectFile.path))
        .setAggregatorMappings(tree.mavenProjectMappings())
        .setMavenIdMappings(tree.projects())

      val modelsProvider = optionalModelsProvider ?: ProjectDataManager.getInstance().createModifiableModelsProvider(project)
      // MavenProjectsManager.getInstance(project).projectsTree = projectTree


      MavenProjectImporter.createImporter(project, projectTree,
                                          tree.withAllChanges(),
                                          modelsProvider,
                                          importingSettings,
                                          null,
                                          activity).importProject()
    }
    catch (e: Exception) {
      MavenLog.LOG.warn(e)
    }
    finally {
      val len = System.currentTimeMillis() - time;
      println("preimport finished. Took $len milliseconds")
      listOf(ProjectImportCollector.LINKED_PROJECTS.with(1),
             ProjectImportCollector.SUBMODULES_COUNT.with(tree.projects().size))
    }


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
    mavenModel.build.directory = parentFolder.resolve("target").toString()
    mavenModel.build.outputDirectory = parentFolder.resolve("target/classes").toString()
    mavenModel.build.testOutputDirectory = parentFolder.resolve("target/test-classes").toString()
    mavenModel.build.sources = listOf(parentFolder.resolve("src/main/java").toString(), parentFolder.resolve("src/main/kotlin").toString())
    mavenModel.build.testSources = listOf(parentFolder.resolve("src/test/java").toString(),
                                          parentFolder.resolve("src/test/kotlin").toString());
    mavenModel.modules = rootModel.getChildrenText("modules", "module")
    mavenModel.packaging = rootModel.getChildTextTrim("packaging") ?: "jar"

    MavenJDOMUtil.findChildrenByPath(rootModel, "dependencies", "dependency")?.map {
      MavenId(it.getChildTextTrim("groupId"),
              it.getChildTextTrim("artifactId"),
              it.getChildTextTrim("version"))
    }
    mavenProjectData.declaredDependencies.addAll(
      MavenJDOMUtil.findChildrenByPath(rootModel, "dependencies", "dependency")
        .map { MavenId(it.getChildTextTrim("groupId"), it.getChildTextTrim("artifactId"), it.getChildTextTrim("version")) }

    )

    mavenProjectData.dependencyManagement.addAll(
      MavenJDOMUtil.findChildrenByPath(rootModel.getChild("dependencyManagement"), "dependencies", "dependency")
        .map { MavenId(it.getChildTextTrim("groupId"), it.getChildTextTrim("artifactId"), it.getChildTextTrim("version")) }

    )

    val modelMap = HashMap<String, String>()
    mavenModel.mavenId.groupId?.let { modelMap.put("groupId", it) }
    mavenModel.mavenId.artifactId?.let { modelMap.put("artifactId", it) }
    mavenModel.mavenId.version?.let { modelMap.put("version", it) }

    modelMap.put("build.outputDirectory", mavenModel.build.outputDirectory)
    modelMap.put("build.testOutputDirectory", mavenModel.build.testOutputDirectory)
    modelMap.put("build.finalName", mavenModel.build.finalName)
    modelMap.put("build.directory", mavenModel.build.directory)

    val result = MavenProjectReaderResult(mavenModel, modelMap, MavenExplicitProfiles.NONE, null, emptyList(), emptySet())
    mavenProject.set(result, MavenProjectsManager.getInstance(project).generalSettings, true, true, true);
    return mavenProjectData;

  }
}

private fun Element.getChildrenText(s: String, c: String): List<String> {
  return this.getChild(s)?.getChildren(c)?.map { it.textTrim } ?: emptyList()

}

class ProjectTree {
  private val tree = HashMap<VirtualFile, MutableList<MavenProjectData>>()
  private val allProjects = HashMap<VirtualFile, MavenProjectData>()
  private val fullMavenIds = HashMap<MavenId, MavenProjectData>()
  private val managedMavenIds = HashMap<MavenId, MavenProjectData>()

  fun projects() = allProjects.values.map { it.mavenProject }

  private val mutex = Mutex(false)

  suspend fun addRoot(root: MavenProjectData) {
    mutex.withLock {
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

  fun withAllChanges(): Map<MavenProject, MavenProjectChanges> {
    return allProjects.map { it.value.mavenProject to MavenProjectChanges.ALL }.toMap()
  }
}

class MavenProjectData(val mavenProject: MavenProject) {
  val dependencyManagement = ArrayList<MavenId>()
  val declaredDependencies = ArrayList<MavenId>()
  val resolvedDependencyManagement = HashMap<MavenId, MavenId>()
  val properties = HashMap<String, String>()
  val resolvedDependencies = ArrayList<MavenId>()

  val mavenId by lazy { mavenProject.mavenId }
  val parentId by lazy { mavenProject.parentId }
  val file = mavenProject.file
}

private fun trimVersion(id: MavenId) = MavenId(id.groupId, id.artifactId, null)

