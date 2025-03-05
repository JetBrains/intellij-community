// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporter
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenGoalExecutionRequest
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.Remapper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.io.copyTo
import kotlin.io.extension
import kotlin.io.path.pathString
import kotlin.io.relativeTo
import kotlin.io.walkTopDown
import kotlin.use

internal class MavenShadePluginConfigurator : MavenWorkspaceConfigurator {
  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(WorkspaceModuleImporter.EXTERNAL_SOURCE_ID)

  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    if (!Registry.`is`("maven.shade.plugin.create.uber.jar.dependency")) return

    val mavenProjectToModules = context.mavenProjectsWithModules.map { it.mavenProject to it.modules.map { m -> m.module } }.toMap()

    // find all poms with maven-shade-plugin that use class relocation
    val shadedProjectsToRelocations = context.mavenProjectsWithModules.mapNotNull { p ->
      val mavenProject = p.mavenProject
      val relocationMap = mavenProject.getRelocationMap()
      if (relocationMap.isEmpty()) null
      else {
        val includes = mavenProject.getIncludes()
        val excludes = mavenProject.getExcludes()
        mavenProject to RelocationData(relocationMap, includes, excludes)
      }
    }.toMap()

    val shadedProjects = shadedProjectsToRelocations.keys

    if (shadedProjects.isEmpty()) return

    val shadedMavenProjectsToBuildUberJar = mutableMapOf<MavenProject, MavenProjectShadingData>()

    val moduleIdToMavenProject = HashMap<ModuleId, MavenProject>()
    for (mavenProjectWithModules in context.mavenProjectsWithModules) {
      for (module in mavenProjectWithModules.modules) {
        moduleIdToMavenProject[module.module.symbolicId] = mavenProjectWithModules.mavenProject
      }
    }
    val shadedModuleIds = moduleIdToMavenProject.keys.filter { shadedProjects.contains(moduleIdToMavenProject[it]) }

    // process dependent modules
    for (module in context.storage.entities(ModuleEntity::class.java)) {
      for (dependency in module.dependencies.filterIsInstance<ModuleDependency>()) {
        val dependencyModuleId = dependency.module
        if (shadedModuleIds.contains(dependencyModuleId)) {
          val mavenProject = moduleIdToMavenProject[dependencyModuleId]!!
          val uberJarPath = getUberJarPath(mavenProject)
          addJarDependency(context.storage, context.project, module, mavenProject.mavenId, uberJarPath)

          if (shadedMavenProjectsToBuildUberJar.contains(mavenProject)) continue

          val relocationData = shadedProjectsToRelocations[mavenProject]!!
          val dependentMavenProjects = mavenProject.collectDependentMavenProjects(mavenProjectToModules, moduleIdToMavenProject)
          shadedMavenProjectsToBuildUberJar[mavenProject] = MavenProjectShadingData(relocationData, uberJarPath, dependentMavenProjects)
        }
      }
    }

    context.putUserDataIfAbsent(SHADED_MAVEN_PROJECTS, shadedMavenProjectsToBuildUberJar)
  }

  private fun MavenProject.collectDependentMavenProjects(
    mavenProjectToModules: Map<MavenProject, List<ModuleEntity>>,
    moduleIdToMavenProject: HashMap<ModuleId, MavenProject>
  ): Collection<MavenProject> {
    val dependentMavenProjects = mutableSetOf<MavenProject>()
    val queue = ArrayDeque<MavenProject>()

    queue.add(this)

    while (queue.isNotEmpty()) {
      val currentProject = queue.removeFirst()

      val modules = mavenProjectToModules[currentProject] ?: continue
      for (module in modules) {
        val moduleDependencies = module.dependencies.filterIsInstance<ModuleDependency>()
        for (moduleDependency in moduleDependencies) {
          val mavenProject = moduleIdToMavenProject[moduleDependency.module] ?: continue
          if (dependentMavenProjects.add(mavenProject)) {
            queue.add(mavenProject)
          }
        }
      }
    }

    return dependentMavenProjects
  }

  private fun MavenProject.getRelocationMap(): Map<String, String> {
    return this
             .findPlugin("org.apache.maven.plugins", "maven-shade-plugin")
             ?.executions
             ?.asSequence()
             ?.mapNotNull { it.configurationElement?.getChild("relocations") }
             ?.flatMap { it.getChildren("relocation").asSequence() }
             ?.mapNotNull {
               val pattern = it.getChildText("pattern")
               val shadedPattern = it.getChildText("shadedPattern")
               if (pattern != null && shadedPattern != null) pattern.replaceDots() to shadedPattern.replaceDots() else null
             }
             ?.toMap() ?: emptyMap()
  }

  private fun MavenProject.getIncludes(): Set<String> {
    return this
             .findPlugin("org.apache.maven.plugins", "maven-shade-plugin")
             ?.executions
             ?.asSequence()
             ?.mapNotNull { it.configurationElement?.getChild("artifactSet")?.getChild("includes") }
             ?.flatMap { it.getChildren("include").asSequence() }
             ?.mapNotNull { it.text }
             ?.toSet() ?: emptySet()
  }

  private fun MavenProject.getExcludes(): Set<String> {
    return this
             .findPlugin("org.apache.maven.plugins", "maven-shade-plugin")
             ?.executions
             ?.asSequence()
             ?.mapNotNull { it.configurationElement?.getChild("artifactSet")?.getChild("excludes") }
             ?.flatMap { it.getChildren("exclude").asSequence() }
             ?.mapNotNull { it.text }
             ?.toSet() ?: emptySet()
  }

  private fun String.replaceDots(): String {
    return this.replace('.', '/')
  }

  private fun getUberJarPath(mavenProject: MavenProject): String {
    val mavenId = mavenProject.mavenId
    val fileName = "${mavenId.artifactId}-${mavenId.version}.jar"
    return Path.of(mavenProject.buildDirectory, fileName).pathString
  }

  private fun addJarDependency(builder: MutableEntityStorage,
                               project: Project,
                               module: ModuleEntity,
                               dependencyMavenId: MavenId,
                               dependencyJarPath: String) {
    val libraryName = SHADED_MAVEN_LIBRARY_NAME_PREFIX + dependencyMavenId.displayString
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)

    val jarUrl = WorkspaceModel.getInstance(project).getVirtualFileUrlManager().getOrCreateFromUrl("jar://$dependencyJarPath!/")

    addLibraryEntity(
      builder,
      project,
      libraryId
    ) {
      listOf(LibraryRoot(jarUrl, LibraryRootTypeId.COMPILED))
    }

    val scope = DependencyScope.COMPILE
    val libraryDependency = LibraryDependency(libraryId, false, scope)

    builder.modifyModuleEntity(module) {
      this.dependencies.add(libraryDependency)
    }
  }

  private fun addLibraryEntity(
    builder: MutableEntityStorage,
    project: Project,
    libraryId: LibraryId,
    // lazy provider to avoid roots creation for already added libraries
    libraryRootsProvider: () -> List<LibraryRoot>) {
    if (libraryId in builder) return

    val librarySource = LegacyBridgeJpsEntitySourceFactory.getInstance(project).createEntitySourceForProjectLibrary(externalSource)

    builder addEntity LibraryEntity(libraryId.name, libraryId.tableId, libraryRootsProvider(), librarySource)
  }
}

private data class RelocationData(
  val relocationMap: Map<String, String>,
  val includes: Set<String>,
  val excludes: Set<String>,
)

private data class MavenProjectShadingData(
  val relocationData: RelocationData,
  val uberJarPath: String,
  val dependentMavenProjects: Collection<MavenProject>,
)

@ApiStatus.Internal
internal val SHADED_MAVEN_LIBRARY_NAME_PREFIX = "Maven Shade: "
private val SHADED_MAVEN_PROJECTS = Key.create<Map<MavenProject, MavenProjectShadingData>>("SHADED_MAVEN_PROJECTS")

internal class MavenShadeFacetGeneratePostTaskConfigurator : MavenAfterImportConfigurator {
  override fun afterImport(context: MavenAfterImportConfigurator.Context) {
    if (!Registry.`is`("maven.shade.plugin.generate.uber.jar")) return

    val shadedMavenProjects = context.getUserData(SHADED_MAVEN_PROJECTS)?.filter { (_, shadingData) ->
      // only generate non-existing uber jars
      val uberJarPath = shadingData.uberJarPath
      val uberJarFile = File(uberJarPath)
      !uberJarFile.exists()
    }
    if (shadedMavenProjects.isNullOrEmpty()) return

    val project = context.project
    packageJars(project, shadedMavenProjects)

    val filesToRefresh = shadedMavenProjects.map { Path.of(it.key.buildDirectory) }
    LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh, true, false, null)
  }

  private fun packageJars(project: Project, shadedMavenProjects: Map<MavenProject, MavenProjectShadingData>) {
    val projectsManager = MavenProjectsManager.getInstance(project)
    val projectsTree = projectsManager.projectsTree
    val projectRoot = projectsTree.findRootProject(shadedMavenProjects.keys.first()) // assume there's only one root project
    val baseDir = MavenUtil.getBaseDir(projectRoot.directoryFile).toString()
    val syncConsole = projectsManager.syncConsole

    val userProperties = Properties()
    userProperties["skipTests"] = "true"
    userProperties["checkstyle.skip"] = "true"
    //userProperties["maven.test.skip"] = "true"

    val mavenProjects = shadedMavenProjects.flatMap { it.value.dependentMavenProjects + it.key }
    val selectedProjects = mavenProjects.mapNotNull { it.mavenId.artifactId }.map { ":$it" }.distinct().toList()
    val request = MavenGoalExecutionRequest(File(projectRoot.path), MavenExplicitProfiles.NONE, selectedProjects, userProperties)

    val names = shadedMavenProjects.map { it.key.displayName }
    val text = StringUtil.shortenPathWithEllipsis(StringUtil.join(names, ", "), 200)

    runBlockingMaybeCancellable {
      withBackgroundProgress(project, MavenProjectBundle.message("maven.generating.uber.jars", text), true) {
        reportRawProgress { reporter ->
          val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
          mavenEmbedderWrappers.use {
            val embedder = mavenEmbedderWrappers.getEmbedder(baseDir)
            embedder.executeGoal(listOf(request), "package", reporter, syncConsole)
          }
        }
      }
    }
  }
}

internal class MavenShadeFacetRemapPostTaskConfigurator : MavenAfterImportConfigurator {
  override fun afterImport(context: MavenAfterImportConfigurator.Context) {
    if (!Registry.`is`("maven.shade.plugin.remap.uber.jar")) return

    val shadedMavenProjects = context.getUserData(SHADED_MAVEN_PROJECTS)?.filter { (_, shadingData) ->
      // only remap classes for non-existing uber jars
      val uberJarPath = shadingData.uberJarPath
      val uberJarFile = File(uberJarPath)
      !uberJarFile.exists()
    }
    if (shadedMavenProjects.isNullOrEmpty()) return

    val project = context.project
    compileDependencies(project, shadedMavenProjects)

    shadedMavenProjects.forEach { (mavenProject, shadingData) ->
      try {
        runBlockingMaybeCancellable {
          val text = mavenProject.path
          withBackgroundProgress(project, MavenProjectBundle.message("maven.remapping.uber.jars.dependencies", text), true) {
            remapUberJar(mavenProject, shadingData)
          }
        }
      }
      catch (e: Exception) {
        MavenLog.LOG.warn(e)
      }
    }

    val filesToRefresh = shadedMavenProjects.keys.map { Path.of(it.buildDirectory) }
    LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh, true, false, null)
  }

  private fun compileDependencies(project: Project, shadedMavenProjects: Map<MavenProject, MavenProjectShadingData>) {
    // compile all project roots
    val shadedMavenProjectsAndDependencies = mutableSetOf<MavenProject>()
    shadedMavenProjects.forEach {
      shadedMavenProjectsAndDependencies.add(it.key)
      shadedMavenProjectsAndDependencies.addAll(it.value.dependentMavenProjects)
    }
    val projectsManager = MavenProjectsManager.getInstance(project)
    val projectsTree = projectsManager.projectsTree

    val projectRoots = shadedMavenProjectsAndDependencies.map { projectsTree.findRootProject(it) }.toSet()

    val baseDirsToMavenProjects = MavenUtil.groupByBasedir(projectRoots, projectsTree)

    for (baseDir in baseDirsToMavenProjects.keySet()) {
      doCompile(project, baseDirsToMavenProjects[baseDir], baseDir, projectsManager.syncConsole)
    }
  }

  private fun doCompile(project: Project,
                        mavenProjects: Collection<MavenProject>,
                        baseDir: String, mavenEventHandler: MavenEventHandler) {
    val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    mavenEmbedderWrappers.use {
      val embedder = runBlockingMaybeCancellable {
        mavenEmbedderWrappers.getEmbedder(baseDir)
      }

      val requests = mavenProjects.map { MavenGoalExecutionRequest(File(it.path), MavenExplicitProfiles.NONE) }.toList()
      val names = mavenProjects.map { it.displayName }
      val text = StringUtil.shortenPathWithEllipsis(StringUtil.join(names, ", "), 200)
      runBlockingMaybeCancellable {
        withBackgroundProgress(project, MavenProjectBundle.message("maven.compiling.uber.jars.dependencies", text), true) {
          reportRawProgress { reporter ->
            embedder.executeGoal(requests, "compile", reporter, mavenEventHandler)
          }
        }
      }
    }
  }

  private fun remapUberJar(mavenProject: MavenProject, shadingData: MavenProjectShadingData) {
    val uberJarPath = shadingData.uberJarPath
    val uberJarFile = File(uberJarPath)
    uberJarFile.parentFile.mkdirs()

    val relocationData = shadingData.relocationData
    val mavenProjects = shadingData.dependentMavenProjects + mavenProject
    val dependencyJarPaths = mavenProjects.flatMap { it.dependencies }
      .filter { relocationData.shouldRelocate(it.groupId, it.artifactId) }
      .map { it.path }
      .filter { it.endsWith("jar") && File(it).exists() }
    val targetFolders = mavenProjects
      .filter { relocationData.shouldRelocate(it.mavenId.groupId, it.mavenId.artifactId) }
      .map { it.outputDirectory }

    val relocationMap = relocationData.relocationMap
    val remapper = object : Remapper() {
      override fun map(typeName: String): String {
        for ((oldPackage, newPackage) in relocationMap) {
          if (typeName.startsWith(oldPackage)) {
            return typeName.replaceFirst(oldPackage, newPackage)
          }
        }
        return typeName
      }
    }

    val addedEntries = mutableSetOf<String>()

    JarOutputStream(FileOutputStream(uberJarPath)).use { jarOut ->
      dependencyJarPaths.forEach { dependencyJarPath ->
        relocateJar(dependencyJarPath, addedEntries, jarOut, remapper)
      }
      targetFolders.forEach { folderPath ->
        relocateFolder(folderPath, addedEntries, jarOut, remapper)
      }
    }
  }

  private fun RelocationData.shouldRelocate(groupId: String?, artifactId: String?): Boolean {
    if (null == groupId || null == artifactId) return false
    val groupAndArtifact = "${groupId}:${artifactId}"
    return (includes.isEmpty() || includes.any { include ->
      include.startsWith(groupAndArtifact)
    })
    && !excludes.any { exclude -> exclude.startsWith(groupAndArtifact) }
  }

  private fun relocateFolder(
    folderPath: @NlsSafe String,
    addedEntries: MutableSet<String>,
    jarOut: JarOutputStream,
    remapper: Remapper,
  ) {
    File(folderPath).walkTopDown().filter { it.isFile && it.extension == "class" }.forEach classEntry@{ classFile ->
      FileInputStream(classFile).use { inputStream ->
        val entryName = classFile.relativeTo(File(folderPath)).path.replace(File.separatorChar, '/')
        if (!addedEntries.add(entryName)) return@classEntry
        processClassEntry(inputStream, entryName, jarOut, remapper)
      }
    }
  }

  private fun relocateJar(
    dependencyJarPath: String?,
    addedEntries: MutableSet<String>,
    jarOut: JarOutputStream,
    remapper: Remapper,
  ) {
    JarFile(File(dependencyJarPath)).use { jarFile ->
      jarFile.entries().asSequence().forEach jarFileEntry@{ entry ->
        val inputStream = jarFile.getInputStream(entry)
        val entryName = entry.name

        // only add each entry once
        if (!addedEntries.add(entryName)) return@jarFileEntry

        if (entryName.endsWith(".class")) {
          processClassEntry(inputStream, entryName, jarOut, remapper)
        }
        else {
          jarOut.putNextEntry(entry)
          inputStream.copyTo(jarOut)
          jarOut.closeEntry()
        }
        inputStream.close()
      }
    }
  }

  private fun processClassEntry(inputStream: InputStream, entryName: String, jarOut: JarOutputStream, remapper: Remapper) {
    val classReader = ClassReader(inputStream)
    val classWriter = ClassWriter(classReader, 0)
    val classRemapper = ClassRemapper(classWriter, remapper)
    classReader.accept(classRemapper, 0)
    val newEntry = JarEntry(entryName)
    jarOut.putNextEntry(newEntry)
    jarOut.write(classWriter.toByteArray())
    jarOut.closeEntry()
  }
}