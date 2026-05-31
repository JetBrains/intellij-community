// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.util.containers.ArrayListSet
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.FileCollectionFactory
import com.intellij.util.messages.Topic
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenCoordinate
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenProfileKind
import org.jetbrains.idea.maven.model.MavenWorkspaceMap
import org.jetbrains.idea.maven.project.MavenProjectsTreeUpdater.UpdateSpec
import org.jetbrains.idea.maven.telemetry.tracer
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.Strings
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.EventListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.regex.Pattern

class MavenProjectsTree(val project: Project) {
  private val myStructureLock = ReentrantReadWriteLock()
  private val myStructureReadLock: Lock = myStructureLock.readLock()
  private val myStructureWriteLock: Lock = myStructureLock.writeLock()

  private val myIgnoredFilesPaths: MutableList<String> = ArrayList()

  private val myIgnoredFilesPatterns: MutableList<String> = ArrayList()

  private var myIgnoredFilesPatternsCache: Pattern? = null

  private val myRootProjects = mutableListOf<MavenProject>() //2

  private val myTimestamps: MutableMap<VirtualFile, MavenProjectTimestamp> = HashMap()
  private val myWorkspaceMap = MavenWorkspaceMap()
  private val myMavenIdToProjectMapping: MutableMap<MavenId, MavenProject> = HashMap()
  private val myVirtualFileToProjectMapping: MutableMap<VirtualFile, MavenProject> = HashMap() //2
  private val myAggregatorToModuleMapping: MutableMap<String, MutableList<MavenProject>> = HashMap() //2
  private val myModuleToAggregatorMapping: MutableMap<String, MavenProject> = HashMap() //2


  val projectLocator: MavenProjectReaderProjectLocator = MavenProjectReaderProjectLocator { coordinates ->
    val project = findProject(coordinates)
    project?.file
  }

  @ApiStatus.Internal
  internal fun getTimeStamp(mavenProject: MavenProject): MavenProjectTimestamp? {
    return myTimestamps[mavenProject.file]
  }

  @ApiStatus.Internal
  fun putVirtualFileToProjectMapping(mavenProject: MavenProject, oldProjectId: MavenId?) {
    withWriteLock {
      clearIDMaps(oldProjectId)
      myVirtualFileToProjectMapping[mavenProject.file] = mavenProject
      fillIDMaps(mavenProject)
    }
  }

  @ApiStatus.Internal
  internal fun putTimestamp(mavenProject: MavenProject, timestamp: MavenProjectTimestamp) {
    myTimestamps[mavenProject.file] = timestamp
  }

  @Throws(IOException::class)
  fun save(file: Path) {
    val copy = MavenProjectsTree(project)

    withReadLock {
      copy.updater().copyFrom(this)
    }

    DataOutputStream(BufferedOutputStream(Files.newOutputStream(NioFiles.createParentDirectories(file)))).use { out ->
      out.writeUTF(STORAGE_VERSION)

      // managed file paths
      writeCollection(out, emptyList())
      writeCollection(out, copy.myIgnoredFilesPaths)
      writeCollection(out, copy.myIgnoredFilesPatterns)

      // enabled profiles
      writeCollection(out, emptySet())
      // disabled profiles
      writeCollection(out, emptySet())

      copy.writeProjectsRecursively(out, copy.myRootProjects)
    }
  }

  @Throws(IOException::class)
  private fun writeProjectsRecursively(out: DataOutputStream, mavenProjects: Collection<MavenProject>) {
    out.writeInt(mavenProjects.size)
    for (mavenProject in mavenProjects) {
      mavenProject.write(out)
      val timestamp = myTimestamps.getOrDefault(mavenProject.file, MavenProjectTimestamp.NULL)
      timestamp.write(out)
      writeProjectsRecursively(out, getModules(mavenProject))
    }
  }

  var ignoredFilesPaths: List<String>
    get() = withReadLock {
      ArrayList(myIgnoredFilesPaths)
    }
    set(paths) {
      doChangeIgnoreStatus({
                             myIgnoredFilesPaths.replaceWith(paths)
                           })
    }

  fun removeIgnoredFilesPaths(paths: Collection<String>?) {
    doChangeIgnoreStatus({ myIgnoredFilesPaths.removeAll(paths!!) })
  }

  fun getIgnoredState(project: MavenProject): Boolean =
    withReadLock {
      myIgnoredFilesPaths.contains(project.path)
    }


  fun setIgnoredState(projects: List<MavenProject>, ignored: Boolean) {
    setIgnoredState(projects, ignored, false)
  }

  fun setIgnoredState(projects: List<MavenProject>, ignored: Boolean, fromImport: Boolean) {
    val pomPaths = projects.map { it.path }
    setIgnoredStateForPoms(pomPaths, ignored, fromImport)
  }

  @ApiStatus.Internal
  fun setIgnoredStateForPoms(pomPaths: List<String>, ignored: Boolean) {
    doSetIgnoredState(pomPaths, ignored, false)
  }

  @ApiStatus.Internal
  fun setIgnoredStateForPoms(pomPaths: List<String>, ignored: Boolean, fromImport: Boolean) {
    doSetIgnoredState(pomPaths, ignored, fromImport)
  }

  private fun doSetIgnoredState(pomPaths: List<String>, ignored: Boolean, fromImport: Boolean) {
    doChangeIgnoreStatus({
                           if (ignored) {
                             myIgnoredFilesPaths.addAll(pomPaths)
                           }
                           else {
                             myIgnoredFilesPaths.removeAll(pomPaths)
                           }
                         }, fromImport)
  }

  var ignoredFilesPatterns: List<String>
    get() = withReadLock {
      ArrayList(myIgnoredFilesPatterns)
    }
    set(patterns) {
      doChangeIgnoreStatus({
                             myIgnoredFilesPatternsCache = null
                             myIgnoredFilesPatterns.replaceWith(patterns)
                           })
    }

  private fun doChangeIgnoreStatus(runnable: Runnable, fromImport: Boolean = false) {
    val (ignoredBefore, ignoredAfter) = withWriteLock {
      val before = ignoredProjects
      runnable.run()
      val after = ignoredProjects
      (before to after)
    }

    val ignored: MutableList<MavenProject> = ArrayList(ignoredAfter)
    ignored.removeAll(ignoredBefore)

    val unignored: MutableList<MavenProject> = ArrayList(ignoredBefore)
    unignored.removeAll(ignoredAfter)

    if (ignored.isEmpty() && unignored.isEmpty()) return

    fireProjectsIgnoredStateChanged(ignored, unignored, fromImport)
  }

  private val ignoredProjects: List<MavenProject>
    get() {
      val result: MutableList<MavenProject> = ArrayList()
      for (each in projects) {
        if (isIgnored(each)) result.add(each)
      }
      return result
    }

  fun isIgnored(project: MavenProject): Boolean {
    val path = project.path
    return isIgnored(path)
  }

  @ApiStatus.Internal
  fun isIgnored(projectPath: String) = withReadLock {
    myIgnoredFilesPaths.contains(projectPath) || matchesIgnoredFilesPatterns(projectPath)
  }

  private fun matchesIgnoredFilesPatterns(path: String) = withReadLock {
    if (myIgnoredFilesPatternsCache == null) {
      myIgnoredFilesPatternsCache = Pattern.compile(Strings.translateMasks(myIgnoredFilesPatterns))
    }
    return@withReadLock myIgnoredFilesPatternsCache!!.matcher(path).matches()
  }

  val availableProfiles: Set<String>
    get() {
      val res = HashSet<String>()

      for (each in projects) {
        res.addAll(each.profilesIds)
      }

      return res
    }

  fun getProfilesWithStates(explicitProfiles: MavenExplicitProfiles): Collection<Pair<String, MavenProfileKind>> {
      val result: MutableCollection<Pair<String, MavenProfileKind>> = ArrayListSet()

      val available: MutableCollection<String> = HashSet()
      val active: MutableCollection<String> = HashSet()
      for (each in projects) {
        available.addAll(each.profilesIds)
        active.addAll(each.activatedProfilesIds.enabledProfiles)
      }

      val enabledProfiles = explicitProfiles.enabledProfiles
      val disabledProfiles = explicitProfiles.disabledProfiles

      for (each in available) {
        val state = if (disabledProfiles.contains(each)) {
          MavenProfileKind.NONE
        }
        else if (enabledProfiles.contains(each)) {
          MavenProfileKind.EXPLICIT
        }
        else if (active.contains(each)) {
          MavenProfileKind.IMPLICIT
        }
        else {
          MavenProfileKind.NONE
        }
        result.add(Pair.create(each, state))
      }
      return result
    }

  @ApiStatus.Internal
  suspend fun updateAllFiles(
    managedFiles: List<String>,
    force: Boolean,
    generalSettings: MavenGeneralSettings,
    explicitProfiles: MavenExplicitProfiles,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    progressReporter: RawProgressReporter,
  ): MavenProjectsTreeUpdateResult {
    val files = managedFiles.mapNotNull { VirtualFileManager.getInstance().findFileByNioPath(Path.of(it)) }
    return updateAll(files, force, generalSettings, explicitProfiles, mavenEmbedderWrappers, progressReporter)
  }

  @ApiStatus.Internal
  suspend fun updateAll(
    files: List<VirtualFile>,
    force: Boolean,
    generalSettings: MavenGeneralSettings,
    explicitProfiles: MavenExplicitProfiles,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    progressReporter: RawProgressReporter,
  ): MavenProjectsTreeUpdateResult {
    val projectReader = MavenProjectReader(project, mavenEmbedderWrappers, generalSettings, projectLocator)

    val updated = tracer.spanBuilder("updateProjectTree").useWithScope {
      update(files, true, force, projectReader, explicitProfiles, progressReporter)
    }

    val obsoleteFiles = ContainerUtil.subtract(rootProjectsFiles, files)
    val deleted = tracer.spanBuilder("cleanupProjectTree").useWithScope {
      delete(projectReader, explicitProfiles, obsoleteFiles, progressReporter)
    }

    val updateResult = updated.plus(deleted)
    MavenLog.LOG.debug("Maven tree update result: updated ${updateResult.updated}, deleted ${updateResult.deleted}")
    return updateResult
  }

  @ApiStatus.Internal
  suspend fun update(
    files: Collection<VirtualFile>,
    force: Boolean,
    generalSettings: MavenGeneralSettings,
    explicitProfiles: MavenExplicitProfiles,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    progressReporter: RawProgressReporter,
  ): MavenProjectsTreeUpdateResult {
    val projectReader = MavenProjectReader(project, mavenEmbedderWrappers, generalSettings, projectLocator)
    return update(files, false, force, projectReader, explicitProfiles, progressReporter)
  }

  private suspend fun update(
    files: Collection<VirtualFile>,
    updateModules: Boolean,
    forceRead: Boolean,
    projectReader: MavenProjectReader,
    explicitProfiles: MavenExplicitProfiles,
    progressReporter: RawProgressReporter,
  ): MavenProjectsTreeUpdateResult {
    val updateContext = MavenProjectsTreeUpdateContext(this)

    val updater = MavenProjectsTreeUpdater(
      this,
      updateContext,
      projectReader,
      explicitProfiles,
    progressReporter,
      updateModules)

    val filesToAddModules = HashSet<VirtualFile>()
    for (file in files) {
      if (null == findProject(file)) {
        filesToAddModules.add(file)
      }
      tracer.spanBuilder("updateProjectFile").useWithScope { updater.updateProjects(listOf(UpdateSpec(file, forceRead))) }
    }

    for (aggregator in projects) {
      for (moduleFile in aggregator.existingModuleFiles) {
        if (filesToAddModules.contains(moduleFile)) {
          filesToAddModules.remove(moduleFile)
          val mavenProject = findProject(moduleFile)
          if (null != mavenProject) {
            tracer.spanBuilder("reconnect").use {
              if (reconnect(aggregator, mavenProject)) {
                updateContext.updated(mavenProject, MavenProjectChanges.NONE)
              }
            }
          }
        }
      }
    }

    for (file in filesToAddModules) {
      val mavenProject = findProject(file)
      if (null != mavenProject) {
        addRootModule(mavenProject)
      }
    }

    updateContext.fireUpdatedIfNecessary()

    return updateContext.toUpdateResult()
  }

  private fun toRawProgressReporter(progressIndicator: ProgressIndicator): RawProgressReporter {
    return object : RawProgressReporter {
      override fun text(text: @NlsContexts.ProgressText String?) {
        progressIndicator.text = text
      }
    }
  }

  override fun toString(): String {
    return "MavenProjectsTree{" +
           "myRootProjects=" + myRootProjects +
           ", myProject=" + project +
           '}'
  }

  @ApiStatus.Internal
  suspend fun delete(
    files: List<VirtualFile>,
    generalSettings: MavenGeneralSettings,
    explicitProfiles: MavenExplicitProfiles,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    progressReporter: RawProgressReporter,
  ): MavenProjectsTreeUpdateResult {
    val projectReader = MavenProjectReader(project, mavenEmbedderWrappers, generalSettings, projectLocator)
    return delete(projectReader,explicitProfiles, files, progressReporter)
  }

  private suspend fun delete(
    projectReader: MavenProjectReader,
    explicitProfiles: MavenExplicitProfiles,
    files: Collection<VirtualFile>,
    progressReporter: RawProgressReporter,
  ): MavenProjectsTreeUpdateResult {
    val updateContext = MavenProjectsTreeUpdateContext(this)

    val inheritorsToUpdate: MutableSet<MavenProject> = HashSet()
    for (each in files) {
      val mavenProject = findProject(each)
      if (mavenProject == null) continue

      inheritorsToUpdate.addAll(findInheritors(mavenProject))
      doDelete(findAggregator(mavenProject), mavenProject, updateContext)
    }
    inheritorsToUpdate.removeAll(updateContext.deletedProjects.toSet())

    val updater = MavenProjectsTreeUpdater(
      this,
      updateContext,
      projectReader,
      explicitProfiles,
      progressReporter,
      false)

    val updateSpecs = ArrayList<UpdateSpec>()
    for (mavenProject in inheritorsToUpdate) {
      updateSpecs.add(UpdateSpec(mavenProject.file, false))
    }
    updater.updateProjects(updateSpecs)

    for (mavenProject in inheritorsToUpdate) {
      if (reconnectRoot(mavenProject)) {
        updateContext.updated(mavenProject, MavenProjectChanges.NONE)
      }
    }
    updateContext.fireUpdatedIfNecessary()

    return updateContext.toUpdateResult()
  }

  @ApiStatus.Internal
  internal fun doDelete(aggregator: MavenProject?, project: MavenProject, updateContext: MavenProjectsTreeUpdateContext) {
    for (each in getModules(project)) {
      doDelete(project, each, updateContext)
    }

    withWriteLock {
      if (aggregator != null) {
        removeModule(aggregator, project)
      }
      else {
        myRootProjects.removeProject(project)
      }
      myTimestamps.remove(project.file)
      myVirtualFileToProjectMapping.remove(project.file)
      clearIDMaps(project.mavenId)
      myAggregatorToModuleMapping.remove(project.path)
      myModuleToAggregatorMapping.remove(project.path)
    }
    updateContext.deleted(project)
  }

  private fun fillIDMaps(mavenProject: MavenProject) {
    val id = mavenProject.mavenId
    myWorkspaceMap.register(id, File(mavenProject.file.path))
    myMavenIdToProjectMapping[id] = mavenProject
  }

  private fun clearIDMaps(mavenId: MavenId?) {
    if (null == mavenId) return

    myWorkspaceMap.unregister(mavenId)
    myMavenIdToProjectMapping.remove(mavenId)
  }

  private fun addRootModule(project: MavenProject) {
    withWriteLock {
      myRootProjects.addProject(project)
      myRootProjects.sortWith(Comparator.comparing { mavenProject: MavenProject -> mavenProjectToNioPath(mavenProject) })
    }
  }

  @ApiStatus.Internal
  fun reconnect(newAggregator: MavenProject, project: MavenProject): Boolean {
    val prevAggregator = findAggregator(project)

    if (prevAggregator === newAggregator) return false
    if (newAggregator === project) return false
    withWriteLock {
      if (prevAggregator != null) {
        removeModule(prevAggregator, project)
      }
      else {
        myRootProjects.removeProject(project)
      }
      addModule(newAggregator, project)
    }

    return true
  }

  @ApiStatus.Internal
  fun reconnectRoot(project: MavenProject): Boolean {
    val prevAggregator = findAggregator(project)

    if (prevAggregator == null) return false

    withWriteLock {
      removeModule(prevAggregator, project)
      addRootModule(project)
    }

    return true
  }

  internal fun recalculateMavenIdToProjectMap() {
    withWriteLock {
      myMavenIdToProjectMapping.clear()
      myWorkspaceMap.availableIds.toList().forEach {
        myWorkspaceMap.unregister(it)
      }
      myVirtualFileToProjectMapping.values.forEach {
        myMavenIdToProjectMapping[it.mavenId] = it
        myWorkspaceMap.register(it.mavenId, File(it.file.path))
      }
    }
  }

  fun hasProjects(): Boolean {
    return withReadLock { !myRootProjects.isEmpty() }
  }

  val rootProjects: List<MavenProject>
    get() = withReadLock {
      myRootProjects.toList()
    }

  val rootProjectsFiles: List<VirtualFile>
    get() = MavenUtil.collectFiles(rootProjects)

  val projects: List<MavenProject>
    get() = withReadLock { ArrayList(myVirtualFileToProjectMapping.values) }

  val nonIgnoredProjects: List<MavenProject>
    get() = withReadLock {
      val result: MutableList<MavenProject> = ArrayList()
      for (each in myVirtualFileToProjectMapping.values) {
        if (!isIgnored(each)) result.add(each)
      }
      result
    }

  val projectsFiles: List<VirtualFile>
    get() = withReadLock { ArrayList(myVirtualFileToProjectMapping.keys) }

  fun findProject(f: VirtualFile): MavenProject? {
    return withReadLock { myVirtualFileToProjectMapping[f] }
  }

  fun findProject(id: MavenId?): MavenProject? {
    return withReadLock { myMavenIdToProjectMapping[id] }
  }

  fun findProject(artifact: MavenArtifact): MavenProject? {
    return findProject(artifact.mavenId)
  }

  fun findSingleProjectInReactor(id: MavenId): MavenProject? {
    return withReadLock {
      myMavenIdToProjectMapping.values.firstOrNull {
        StringUtil.equals(it.mavenId.artifactId, id.artifactId) &&
        StringUtil.equals(it.mavenId.groupId, id.groupId)
      }
    }
  }

  val workspaceMap: MavenWorkspaceMap
    get() = withReadLock { myWorkspaceMap.copy() }

  fun findAggregator(project: MavenProject): MavenProject? {
    return withReadLock { myModuleToAggregatorMapping[project.path] }
  }

  fun collectAggregators(mavenProjects: Collection<MavenProject>): Collection<MavenProject> {
    val mavenProjectsToSkip = HashSet<MavenProject>()
    for (mavenProject in mavenProjects) {
      var aggregator: MavenProject? = mavenProject
      while ((findAggregator(aggregator!!).also { aggregator = it }) != null) {
        if (mavenProjects.contains(aggregator)) {
          mavenProjectsToSkip.add(mavenProject)
          break
        }
      }
    }
    return mavenProjects.filter { !mavenProjectsToSkip.contains(it) }
  }

  fun findRootProject(project: MavenProject): MavenProject {
    return withReadLock { doFindRootProject(project) }
  }

  private fun doFindRootProject(project: MavenProject): MavenProject {
    var rootProject = project
    val traversed = LinkedHashSet<MavenProject>().also { it.add(project) }
    while (true) {
      val aggregator = myModuleToAggregatorMapping[rootProject.path]
      if (aggregator == null) {
        return rootProject
      }
      if (!traversed.add(aggregator)) {
        MavenLog.LOG.warn("Recursive aggregator definition: ${traversed.joinToString(" -> ") { it.mavenId.toString() }}")
        return project
      }
      rootProject = aggregator
    }
  }

  fun getModules(aggregator: MavenProject): List<MavenProject> {
    return withReadLock {
      val modules: List<MavenProject>? = myAggregatorToModuleMapping[aggregator.path]
      if (modules == null) emptyList() else ArrayList(modules)
    }
  }

  private fun addModule(aggregator: MavenProject, module: MavenProject) {
    withWriteLock {
      var modules = myAggregatorToModuleMapping[aggregator.path]
      if (modules == null) {
        modules = ArrayList()
        myAggregatorToModuleMapping[aggregator.path] = modules
      }
      modules.add(module)
      myModuleToAggregatorMapping[module.path] = aggregator
    }
  }

  @ApiStatus.Internal
  fun removeModule(aggregator: MavenProject, module: MavenProject) {
    withWriteLock {
      val modules = myAggregatorToModuleMapping[aggregator.path]
      if (modules == null) return@withWriteLock
      modules.remove(module)
      if (modules.isEmpty()) {
        myAggregatorToModuleMapping.remove(aggregator.path)
      }
      myModuleToAggregatorMapping.remove(module.path)
    }
  }

  @ApiStatus.Internal
  fun findParent(project: MavenProject): MavenProject? {
    return findProject(project.parentId)
  }

  fun findInheritors(project: MavenProject): Collection<MavenProject> {
    if (project.isNew) return listOf()

    return withReadLock {
      var result: MutableList<MavenProject>? = null
      val id = project.mavenId

      for (each in myVirtualFileToProjectMapping.values) {
        if (each === project) continue
        if (id == each.parentId) {
          if (result == null) result = ArrayList()
          result.add(each)
        }
      }
      result ?: listOf()
    }
  }

  fun getDependentProjects(projects: Collection<MavenProject>): List<MavenProject> {
    return withReadLock {
      val result = mutableListOf<MavenProject>()
      val projectIds: MutableSet<MavenCoordinate> = ObjectOpenCustomHashSet(projects.size, MavenCoordinateHashCodeStrategy())
      for (project in projects) {
        projectIds.add(project.mavenId)
      }

      val projectPaths = FileCollectionFactory.createCanonicalFileSet()
      for (project in projects) {
        projectPaths.add(File(project.file.path))
      }

      for (project in myVirtualFileToProjectMapping.values) {
        var isDependent = false

        val pathsInStack = project.modulePaths
        for (path in pathsInStack) {
          if (projectPaths.contains(File(path))) {
            isDependent = true
            break
          }
        }

        if (!isDependent) {
          for (dep in project.dependencies) {
            if (projectIds.contains(dep)) {
              isDependent = true
              break
            }
          }
        }

        if (isDependent) {
          result.add(project)
        }
      }
      result
    }
  }

  private fun fireProfilesChanged() {
    project.messageBus.syncPublisher(Listener.TOPIC).profilesChanged()
  }

  private fun fireProjectsIgnoredStateChanged(ignored: List<MavenProject>, unignored: List<MavenProject>, fromImport: Boolean) {
    project.messageBus.syncPublisher(Listener.TOPIC).projectsIgnoredStateChanged(ignored, unignored, fromImport)
  }

  @ApiStatus.Internal
  fun fireProjectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
    project.messageBus.syncPublisher(Listener.TOPIC).projectsUpdated(updated, deleted)
  }

  fun fireProjectsResolved(projects: List<MavenProject>) {
    project.messageBus.syncPublisher(Listener.TOPIC).projectsResolved(projects)
  }

  fun firePluginsResolved(projects: List<MavenProject>) {
    this.project.messageBus.syncPublisher(Listener.TOPIC).pluginsResolved(projects)
  }

  fun fireFoldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
    project.messageBus.syncPublisher(Listener.TOPIC).foldersResolved(projectWithChanges)
  }

  fun fireArtifactsDownloaded(project: MavenProject) {
    this.project.messageBus.syncPublisher(Listener.TOPIC).artifactsDownloaded(project)
  }

  interface Listener : EventListener {

    companion object {
      @Topic.ProjectLevel
      @JvmField
      val TOPIC: Topic<Listener> =
        Topic.create("Maven tree updates", Listener::class.java)
    }

    fun profilesChanged() {
    }

    fun projectsIgnoredStateChanged(
      ignored: List<MavenProject>,
      unignored: List<MavenProject>,
      fromImport: Boolean,
    ) {
    }

    fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
    }

    fun projectsResolved(projects: List<MavenProject>) {
    }

    @Suppress("DEPRECATION")
    @Deprecated("use pluginsResolved(List<MavenProject>)")
    fun pluginsResolved(project: MavenProject) {
    }

    @Suppress("DEPRECATION")
    fun pluginsResolved(projects: List<MavenProject>) {
      for (project in projects) pluginsResolved(project)
    }

    fun foldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
    }

    fun artifactsDownloaded(project: MavenProject) {
    }
  }

  @ApiStatus.Internal
  class MavenCoordinateHashCodeStrategy : Hash.Strategy<MavenCoordinate> {
    override fun hashCode(other: MavenCoordinate?): Int {
      val artifactId = other?.artifactId
      return artifactId?.hashCode() ?: 0
    }

    override fun equals(o1: MavenCoordinate?, o2: MavenCoordinate?): Boolean {
      if (o1 === o2) {
        return true
      }
      if (o1 == null || o2 == null) {
        return false
      }

      return o1.artifactId == o2.artifactId && o1.version == o2.version && o1.groupId == o2.groupId
    }
  }

  private fun <T> withReadLock(action: () -> T): T {
    myStructureReadLock.lock()
    try {
      return action()
    }
    finally {
      myStructureReadLock.unlock()
    }
  }

  private fun <T> withWriteLock(action: () -> T): T {
    myStructureWriteLock.lock()
    try {
      return action()
    }
    finally {
      myStructureWriteLock.unlock()
    }
  }

  fun updater(): Updater {
    return Updater()
  }

  inner class Updater {
    fun setRootProjects(roots: List<MavenProject>): Updater {
      myRootProjects.clear()
      roots.forEach { root ->
        myRootProjects.addProject(root)
        myVirtualFileToProjectMapping[root.file] = root
      }

      return this
    }

    fun setAggregatorMappings(map: Map<MavenProject, List<MavenProject>>): Updater {
      myAggregatorToModuleMapping.clear()
      myModuleToAggregatorMapping.clear()

      for ((key, value) in map) {
        val result: MutableList<MavenProject> = ArrayList(value)
        myAggregatorToModuleMapping[key.path] = result
        for (c in result) {
          myModuleToAggregatorMapping[c.path] = key
          myVirtualFileToProjectMapping[c.file] = c
        }
      }

      return this
    }

    fun setMavenIdMappings(projects: List<MavenProject>): Updater {
      projects.forEach(
        Consumer { it: MavenProject ->
          myMavenIdToProjectMapping[it.mavenId] = it
        })
      return this
    }

    fun copyFrom(projectTree: MavenProjectsTree): Updater {
      projectTree.myRootProjects.forEach {
        myRootProjects.addProject(it)
      }

      addFromMap(projectTree) { it.myMavenIdToProjectMapping }
      addFromMap(projectTree) { it.myVirtualFileToProjectMapping }
      addFromMap(projectTree) { it.myAggregatorToModuleMapping }
      addFromMap(projectTree) { it.myModuleToAggregatorMapping }

      return this
    }

    private fun <T> addFrom(projectTree: MavenProjectsTree, getter: (MavenProjectsTree) -> MutableCollection<T>) {
      val my = getter(this@MavenProjectsTree)
      val theirs = getter(projectTree)
      if (my.isEmpty()) {
        my.addAll(theirs)
      }
      else {
        val set = LinkedHashSet<T>()
        set.addAll(my)
        set.addAll(theirs)
        my.replaceWith(set)
      }
    }

    private fun <K, V> addFromMap(projectTree: MavenProjectsTree, getter: (MavenProjectsTree) -> MutableMap<K, V>) {
      val my = getter(this@MavenProjectsTree)
      val theirs = getter(projectTree)
      my.putAll(theirs)
    }

  }

  internal suspend fun collectProblems(session: MavenSyncSession) {
    val existingFiles = ConcurrentHashMap<File, Boolean>()
    val fileExistsPredicate = Predicate { f: File -> existingFiles.computeIfAbsent(f) { file: File -> Files.exists(file.toPath()) } }

    coroutineScope {
      withContext(Dispatchers.IO) {
        projects.forEach { project ->
          launch(CoroutineName("collecting problems in ${project.name}")) {
            tracer.spanBuilder("collectProblems").useWithScope {
              project.collectProblems(fileExistsPredicate) // fill problem cache
            }
          }
        }
      }
    }
  }

  @ApiStatus.Internal
  fun read(path: Path) {
    if (!Files.exists(path)) return
    DataInputStream(BufferedInputStream(Files.newInputStream(path))).use { inputStream ->
      var storageVersion = ""
      try {
        storageVersion = inputStream.readUTF()

        // managed file paths
        readCollection(inputStream, LinkedHashSet())

        myIgnoredFilesPaths.replaceWith(readCollection(inputStream, ArrayList()))
        myIgnoredFilesPatterns.replaceWith(readCollection(inputStream, ArrayList()))

        // enabled profiles
        readCollection(inputStream, HashSet())
        // disabled profiles
        readCollection(inputStream, HashSet())

        if (STORAGE_VERSION == storageVersion) {
          readProjectsRecursively(inputStream, this).forEach {
            myRootProjects.addProject(it)
          }
        }
      }
      catch (e: IOException) {
        myRootProjects.clear()
        myTimestamps.clear()
        myVirtualFileToProjectMapping.clear()
        myAggregatorToModuleMapping.clear()
        myModuleToAggregatorMapping.clear()
        MavenLog.LOG.warn("Cannot read project tree from storage, storageVersion $storageVersion", e)
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(MavenProjectsTree::class.java)

    private const val STORAGE_VERSION_NUMBER = 19
    val STORAGE_VERSION: String = MavenProjectsTree::class.java.simpleName + "." + STORAGE_VERSION_NUMBER

    private fun String.getStorageVersionNumber(): Int {
      val parts = this.split(".")
      return try {
        parts.last().toInt()
      }
      catch (_: Exception) {
        0
      }
    }

    @Throws(IOException::class)
    private fun <T : MutableCollection<String>> readCollection(inputStream: DataInputStream, result: T): T {
      var count = inputStream.readInt()
      while (count-- > 0) {
        result.add(inputStream.readUTF())
      }
      return result
    }

    @Throws(IOException::class)
    private fun writeCollection(out: DataOutputStream, list: Collection<String>) {
      out.writeInt(list.size)
      for (each in list) {
        out.writeUTF(each)
      }
    }

    @Throws(IOException::class)
    private fun readProjectsRecursively(
      inputStream: DataInputStream,
      tree: MavenProjectsTree,
    ): MutableList<MavenProject> {
      var count = inputStream.readInt()
      val result: MutableList<MavenProject> = ArrayList(count)
      while (count-- > 0) {
        val project = MavenProject.read(inputStream)
        val timestamp = MavenProjectTimestamp.read(inputStream)
        val modules = readProjectsRecursively(inputStream, tree)
        if (project != null) {
          result.add(project)
          tree.myTimestamps[project.file] = timestamp
          tree.myVirtualFileToProjectMapping[project.file] = project
          tree.fillIDMaps(project)
          if (!modules.isEmpty()) {
            tree.myAggregatorToModuleMapping[project.path] = modules
            for (eachModule in modules) {
              tree.myModuleToAggregatorMapping[eachModule.path] = project
            }
          }
        }
      }
      return result
    }

    private fun updateExplicitProfiles(
      explicitProfiles: MutableCollection<String>,
      temporarilyRemovedExplicitProfiles: MutableCollection<String>,
      available: Set<String>,
    ) {
      val removedProfiles = HashSet(explicitProfiles)
      removedProfiles.removeAll(available)
      temporarilyRemovedExplicitProfiles.addAll(removedProfiles)

      val restoredProfiles = HashSet(temporarilyRemovedExplicitProfiles)
      restoredProfiles.retainAll(available)
      temporarilyRemovedExplicitProfiles.removeAll(restoredProfiles)

      explicitProfiles.removeAll(removedProfiles)
      explicitProfiles.addAll(restoredProfiles)
    }

    @JvmStatic
    fun getFilterExclusions(mavenProject: MavenProject): Collection<String> {
      val config = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin")
      if (config == null) {
        return emptySet()
      }
      val customNonFilteredExtensions =
        MavenJDOMUtil.findChildrenValuesByPath(config, "nonFilteredFileExtensions", "nonFilteredFileExtension")
      if (customNonFilteredExtensions.isEmpty()) {
        return emptySet()
      }
      return Collections.unmodifiableList(customNonFilteredExtensions)
    }
  }
}

private fun MutableList<MavenProject>.addProject(project: MavenProject) {
  this.removeIf {
    it === project || (
      it.path == project.path && it.file.isValid)
  }
  this.add(project)
}

private fun MutableList<MavenProject>.removeProject(project: MavenProject) {
  this.removeIf { it === project || it.path == project.path }
}

private fun mavenProjectToNioPath(mavenProject: MavenProject): Path {
  return Path.of(mavenProject.file.parent.path)
}

private fun <T> MutableCollection<T>.replaceWith(new: Collection<T>) {
  this.clear()
  this.addAll(new)
}
