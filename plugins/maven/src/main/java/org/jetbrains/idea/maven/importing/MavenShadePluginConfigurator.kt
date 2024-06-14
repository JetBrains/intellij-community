// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporter
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenEmbeddersManager
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenGoalExecutionRequest
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.Remapper
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.io.path.pathString

internal class MavenShadePluginConfigurator : MavenWorkspaceConfigurator {
  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(WorkspaceModuleImporter.EXTERNAL_SOURCE_ID)

  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    if (!Registry.`is`("maven.shade.plugin.create.uber.jar.dependency")) return

    val mavenProjectToModules = context.mavenProjectsWithModules.map { it.mavenProject to it.modules.map { m -> m.module } }.toMap()

    // find all poms with maven-shade-plugin that use class relocation
    val shadedProjectsToRelocations = context.mavenProjectsWithModules.mapNotNull { p ->
      val mavenProject = p.mavenProject
      val relocationMap = mavenProject.getRelocationMap()
      if (relocationMap.isNotEmpty()) mavenProject to relocationMap else null
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

          val relocationMap = shadedProjectsToRelocations[mavenProject]!!
          val dependentMavenProjects = mavenProject.collectDependentMavenProjects(mavenProjectToModules, moduleIdToMavenProject)
          shadedMavenProjectsToBuildUberJar[mavenProject] = MavenProjectShadingData(relocationMap, uberJarPath, dependentMavenProjects)
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

    val modules = mavenProjectToModules[this]!!
    for (module in modules) {
      val moduleDependencies = module.dependencies.filterIsInstance<ModuleDependency>()
      for (moduleDependency in moduleDependencies) {
        val mavenProject = moduleIdToMavenProject[moduleDependency.module]
        dependentMavenProjects.add(mavenProject ?: continue)
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
    val libraryName = "Maven Shade: ${dependencyMavenId.displayString}"
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

    val librarySource = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForProjectLibrary(project, externalSource)

    builder addEntity LibraryEntity(libraryId.name, libraryId.tableId, libraryRootsProvider(), librarySource)
  }
}

private data class MavenProjectShadingData(
  val relocationMap: Map<String, String>,
  val uberJarPath: String,
  val dependentMavenProjects: Collection<MavenProject>)

private val SHADED_MAVEN_PROJECTS = Key.create<Map<MavenProject, MavenProjectShadingData>>("SHADED_MAVEN_PROJECTS")

internal class MavenShadeFacetGeneratePostTaskConfigurator : MavenAfterImportConfigurator {
  override fun afterImport(context: MavenAfterImportConfigurator.Context) {
    if (!Registry.`is`("maven.shade.plugin.generate.uber.jar")) return

    val shadedMavenProjects = context.getUserData(SHADED_MAVEN_PROJECTS)?.keys
    if (shadedMavenProjects.isNullOrEmpty()) return

    val project = context.project
    val projectsManager = MavenProjectsManager.getInstance(project)

    val baseDirsToMavenProjects = MavenUtil.groupByBasedir(shadedMavenProjects, projectsManager.projectsTree)

    val embeddersManager = projectsManager.embeddersManager
    val syncConsole = projectsManager.syncConsole

    for (baseDir in baseDirsToMavenProjects.keySet()) {
      packageJarsForBaseDir(project, embeddersManager, syncConsole, baseDirsToMavenProjects[baseDir], baseDir)
    }

    val filesToRefresh = shadedMavenProjects.map { Path.of(it.buildDirectory) }
    LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh, true, false, null)
  }

  private fun packageJarsForBaseDir(project: Project,
                                    embeddersManager: MavenEmbeddersManager,
                                    mavenEventHandler: MavenEventHandler,
                                    mavenProjects: Collection<MavenProject>,
                                    baseDir: String) {
    val embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_POST_PROCESSING, baseDir)

    val userProperties = Properties()
    userProperties["skipTests"] = "true"
    userProperties["maven.test.skip"] = "true"
    val requests = mavenProjects.map { MavenGoalExecutionRequest(File(it.path), MavenExplicitProfiles.NONE, userProperties) }.toList()

    val names = mavenProjects.map { it.displayName }
    val text = StringUtil.shortenPathWithEllipsis(StringUtil.join(names, ", "), 200)

    runBlockingMaybeCancellable {
      withBackgroundProgress(project, MavenProjectBundle.message("maven.generating.uber.jars", text), true) {
        reportRawProgress { reporter ->
          embedder.executeGoal(requests, "package", reporter, mavenEventHandler)
        }
      }
    }
  }
}

internal class MavenShadeFacetRemapPostTaskConfigurator : MavenAfterImportConfigurator {
  override fun afterImport(context: MavenAfterImportConfigurator.Context) {
    if (!Registry.`is`("maven.shade.plugin.remap.uber.jar")) return

    val shadedMavenProjects = context.getUserData(SHADED_MAVEN_PROJECTS)
    if (shadedMavenProjects.isNullOrEmpty()) return

    shadedMavenProjects.forEach { (mavenProject, shadingData) ->
      try {
        remapUberJar(mavenProject, shadingData)
      }
      catch (e: Exception) {
        MavenLog.LOG.warn(e)
      }
    }

    val filesToRefresh = shadedMavenProjects.keys.map { Path.of(it.buildDirectory) }
    LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh, true, false, null)
  }

  private fun remapUberJar(mavenProject: MavenProject, shadingData: MavenProjectShadingData) {
    // only remap classes if uber jar doesn't exist
    val uberJarPath = shadingData.uberJarPath
    val uberJarFile = File(uberJarPath)
    if (uberJarFile.exists()) return

    uberJarFile.parentFile.mkdirs()

    val mavenProjects = shadingData.dependentMavenProjects + mavenProject
    val dependencyJarPaths = mavenProjects.flatMap { it.dependencies }.map { it.path }.filter { File(it).exists() }

    val relocationMap = shadingData.relocationMap
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
        JarFile(File(dependencyJarPath)).use { jarFile ->
          jarFile.entries().asSequence().forEach jarFileEntry@{ entry ->
            val inputStream = jarFile.getInputStream(entry)
            val entryName = entry.name

            // only add each entry once
            if (!addedEntries.add(entryName)) return@jarFileEntry

            if (entryName.endsWith(".class")) {
              val classReader = ClassReader(inputStream)
              val classWriter = ClassWriter(classReader, 0)
              val classRemapper = ClassRemapper(classWriter, remapper)
              classReader.accept(classRemapper, 0)
              val newEntry = JarEntry(entryName)
              jarOut.putNextEntry(newEntry)
              jarOut.write(classWriter.toByteArray())
              jarOut.closeEntry()
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
    }
  }
}