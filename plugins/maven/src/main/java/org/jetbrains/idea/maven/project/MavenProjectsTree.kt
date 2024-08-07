// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.util.containers.ArrayListSet
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.containers.FileCollectionFactory
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.dom.references.MavenFilteredPropertyPsiReferenceProvider
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenProjectsTreeUpdater.UpdateSpec
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.*
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.zip.CRC32
import kotlin.concurrent.Volatile

class MavenProjectsTree(val project: Project) {
  private val myStructureLock = ReentrantReadWriteLock()
  private val myStructureReadLock: Lock = myStructureLock.readLock()
  private val myStructureWriteLock: Lock = myStructureLock.writeLock()

  // TODO replace with sets
  @Volatile
  private var myManagedFilesPaths: MutableSet<String> = LinkedHashSet()

  @Volatile
  private var myIgnoredFilesPaths: MutableList<String> = ArrayList()

  @Volatile
  private var myIgnoredFilesPatterns: List<String> = ArrayList()

  @Volatile
  private var myIgnoredFilesPatternsCache: Pattern? = null

  private var myExplicitProfiles: MavenExplicitProfiles = MavenExplicitProfiles.NONE
  private val myTemporarilyRemovedExplicitProfiles = MavenExplicitProfiles(HashSet(), HashSet())

  private val myRootProjects: MutableList<MavenProject> = ArrayList() //2

  private val myTimestamps: MutableMap<VirtualFile, MavenProjectTimestamp> = HashMap()
  private val myWorkspaceMap = MavenWorkspaceMap()
  private val myMavenIdToProjectMapping: MutableMap<MavenId, MavenProject> = HashMap()
  private val myVirtualFileToProjectMapping: MutableMap<VirtualFile, MavenProject> = HashMap() //2
  private val myAggregatorToModuleMapping: MutableMap<MavenProject, MutableList<MavenProject>> = HashMap() //2
  private val myModuleToAggregatorMapping: MutableMap<MavenProject, MavenProject> = HashMap() //2

  private val myListeners = DisposableWrapperList<Listener>()


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
    withReadLock {
      DataOutputStream(BufferedOutputStream(Files.newOutputStream(NioFiles.createParentDirectories(file)))).use { out ->
        out.writeUTF(STORAGE_VERSION)
        writeCollection(out, myManagedFilesPaths)
        writeCollection(out, myIgnoredFilesPaths)
        writeCollection(out, myIgnoredFilesPatterns)
        writeCollection(out, myExplicitProfiles.enabledProfiles)
        writeCollection(out, myExplicitProfiles.disabledProfiles)
        writeProjectsRecursively(out, myRootProjects)
      }
    }
  }

  @Throws(IOException::class)
  private fun writeProjectsRecursively(out: DataOutputStream, mavenProjects: List<MavenProject>) {
    out.writeInt(mavenProjects.size)
    for (mavenProject in mavenProjects) {
      mavenProject.write(out)
      val timestamp = myTimestamps.getOrDefault(mavenProject.file, MavenProjectTimestamp.NULL)
      timestamp.write(out)
      writeProjectsRecursively(out, getModules(mavenProject))
    }
  }

  val managedFilesPaths: List<String>
    get() = withReadLock {
      ArrayList(myManagedFilesPaths)
    }

  fun resetManagedFilesPathsAndProfiles(paths: List<String>, profiles: MavenExplicitProfiles) {
    withWriteLock {
      myManagedFilesPaths = LinkedHashSet(paths)
      explicitProfiles = profiles
    }
  }

  @TestOnly
  fun resetManagedFilesAndProfiles(files: List<VirtualFile?>?, profiles: MavenExplicitProfiles) {
    resetManagedFilesPathsAndProfiles(MavenUtil.collectPaths(files), profiles)
  }

  fun addManagedFilesWithProfiles(files: List<VirtualFile?>?, profiles: MavenExplicitProfiles) {
    val (newFiles, newProfiles) = withReadLock {
      val newFiles = ArrayList(myManagedFilesPaths)
      newFiles.addAll(MavenUtil.collectPaths(files))

      val newProfiles = myExplicitProfiles.clone()
      newProfiles.enabledProfiles.addAll(profiles.enabledProfiles)
      newProfiles.disabledProfiles.addAll(profiles.disabledProfiles)
      (newFiles to newProfiles)
    }

    resetManagedFilesPathsAndProfiles(newFiles, newProfiles)
  }

  fun removeManagedFiles(files: List<VirtualFile>) {
    val filePaths = files.map { it.path }.toSet()
    withWriteLock { myManagedFilesPaths.removeAll(filePaths) }
  }

  val existingManagedFiles: List<VirtualFile>
    get() {
      val result: MutableList<VirtualFile> = ArrayList()
      for (path in managedFilesPaths) {
        val f = LocalFileSystem.getInstance().findFileByPath(path)
        if (f != null && f.exists()) result.add(f)
      }
      return result
    }

  var ignoredFilesPaths: List<String>
    get() = withReadLock {
      ArrayList(myIgnoredFilesPaths)
    }
    set(paths) {
      doChangeIgnoreStatus({ myIgnoredFilesPaths = ArrayList(paths) })
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
                             myIgnoredFilesPatterns = ArrayList(patterns)
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


  var explicitProfiles: MavenExplicitProfiles
    get() = withReadLock {
      myExplicitProfiles.clone()
    }
    set(explicitProfiles) {
      withWriteLock { myExplicitProfiles = explicitProfiles.clone() }
      fireProfilesChanged()
    }

  private fun updateExplicitProfiles() {
    val available = availableProfiles

    withWriteLock {
      updateExplicitProfiles(myExplicitProfiles.enabledProfiles, myTemporarilyRemovedExplicitProfiles.enabledProfiles,
                             available)
      updateExplicitProfiles(myExplicitProfiles.disabledProfiles, myTemporarilyRemovedExplicitProfiles.disabledProfiles,
                             available)
    }
  }

  val availableProfiles: Set<String>
    get() {
      val res = HashSet<String>()

      for (each in projects) {
        res.addAll(each.profilesIds)
      }

      return res
    }

  val profilesWithStates: Collection<Pair<String, MavenProfileKind>>
    get() {
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


  @Deprecated("use {@link MavenProjectsManager#updateAllMavenProjects(MavenImportSpec)} instead")
  fun updateAll(force: Boolean, generalSettings: MavenGeneralSettings, process: MavenProgressIndicator) {
    runBlockingMaybeCancellable { updateAll(force, generalSettings, process.indicator) }
  }

  @ApiStatus.Internal
  suspend fun updateAll(force: Boolean, generalSettings: MavenGeneralSettings, process: ProgressIndicator): MavenProjectsTreeUpdateResult {
    return updateAll(force, generalSettings, toRawProgressReporter(process))
  }

  @ApiStatus.Internal
  suspend fun updateAll(force: Boolean,
                        generalSettings: MavenGeneralSettings,
                        progressReporter: RawProgressReporter): MavenProjectsTreeUpdateResult {
    val managedFiles = existingManagedFiles
    val explicitProfiles = explicitProfiles

    val projectReader = MavenProjectReader(project)
    val updated = update(managedFiles, true, force, explicitProfiles, projectReader, generalSettings, progressReporter)

    val obsoleteFiles = ContainerUtil.subtract(
      rootProjectsFiles, managedFiles)
    val deleted = delete(projectReader, obsoleteFiles, explicitProfiles, generalSettings, progressReporter)

    val updateResult = updated.plus(deleted)
    MavenLog.LOG.debug("Maven tree update result: updated ${updateResult.updated}, deleted ${updateResult.deleted}")
    return updateResult
  }

  @ApiStatus.Internal
  suspend fun update(files: Collection<VirtualFile>,
                     force: Boolean,
                     generalSettings: MavenGeneralSettings,
                     progressReporter: RawProgressReporter): MavenProjectsTreeUpdateResult {
    return update(files, false, force, explicitProfiles, MavenProjectReader(project), generalSettings, progressReporter)
  }

  private suspend fun update(files: Collection<VirtualFile>,
                             updateModules: Boolean,
                             forceRead: Boolean,
                             explicitProfiles: MavenExplicitProfiles,
                             projectReader: MavenProjectReader,
                             generalSettings: MavenGeneralSettings,
                             progressReporter: RawProgressReporter): MavenProjectsTreeUpdateResult {
    val updateContext = MavenProjectsTreeUpdateContext(this)

    val updater = MavenProjectsTreeUpdater(
      this,
      explicitProfiles,
      updateContext,
      projectReader,
      generalSettings,
      progressReporter,
      updateModules)

    val filesToAddModules = HashSet<VirtualFile>()
    for (file in files) {
      if (null == findProject(file)) {
        filesToAddModules.add(file)
      }
      updater.updateProjects(listOf(UpdateSpec(file, forceRead)))
    }

    for (aggregator in projects) {
      for (moduleFile in aggregator.existingModuleFiles) {
        if (filesToAddModules.contains(moduleFile)) {
          filesToAddModules.remove(moduleFile)
          val mavenProject = findProject(moduleFile)
          if (null != mavenProject) {
            if (reconnect(aggregator, mavenProject)) {
              updateContext.updated(mavenProject, MavenProjectChanges.NONE)
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

    updateExplicitProfiles()
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

  fun isManagedFile(moduleFile: VirtualFile): Boolean {
    return isManagedFile(moduleFile.path)
  }

  private fun isManagedFile(path: String): Boolean = withReadLock {
    for (each in myManagedFilesPaths) {
      if (FileUtil.pathsEqual(each, path)) return@withReadLock true
    }
    return@withReadLock false
  }

  @ApiStatus.Internal
  suspend fun delete(files: List<VirtualFile>,
                     generalSettings: MavenGeneralSettings?,
                     progressReporter: RawProgressReporter): MavenProjectsTreeUpdateResult {
    return delete(MavenProjectReader(project), files, explicitProfiles, generalSettings, progressReporter)
  }

  private suspend fun delete(projectReader: MavenProjectReader,
                             files: Collection<VirtualFile>,
                             explicitProfiles: MavenExplicitProfiles,
                             generalSettings: MavenGeneralSettings?,
                             progressReporter: RawProgressReporter): MavenProjectsTreeUpdateResult {
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
      explicitProfiles,
      updateContext,
      projectReader,
      generalSettings!!,
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
    updateExplicitProfiles()
    updateContext.fireUpdatedIfNecessary()

    return updateContext.toUpdateResult()
  }

  @ApiStatus.Internal
  internal fun doDelete(aggregator: MavenProject?, project: MavenProject, updateContext: MavenProjectsTreeUpdateContext) {
    for (each in getModules(project)) {
      if (isManagedFile(each.path)) {
        if (reconnectRoot(each)) {
          updateContext.updated(each, MavenProjectChanges.NONE)
        }
      }
      else {
        doDelete(project, each, updateContext)
      }
    }

    withWriteLock {
      if (aggregator != null) {
        removeModule(aggregator, project)
      }
      else {
        myRootProjects.remove(project)
      }
      myTimestamps.remove(project.file)
      myVirtualFileToProjectMapping.remove(project.file)
      clearIDMaps(project.mavenId)
      myAggregatorToModuleMapping.remove(project)
      myModuleToAggregatorMapping.remove(project)
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
      myRootProjects.add(project)
      myRootProjects.sortWith(Comparator.comparing { mavenProject: MavenProject -> mavenProjectToNioPath(mavenProject) })
    }
  }

  @ApiStatus.Internal
  fun reconnect(newAggregator: MavenProject, project: MavenProject): Boolean {
    val prevAggregator = findAggregator(project)

    if (prevAggregator === newAggregator) return false

    withWriteLock {
      if (prevAggregator != null) {
        removeModule(prevAggregator, project)
      }
      else {
        myRootProjects.remove(project)
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

  fun hasProjects(): Boolean {
    return withReadLock { !myRootProjects.isEmpty() }
  }

  val rootProjects: List<MavenProject>
    get() = withReadLock { ArrayList(myRootProjects) }

  fun getFilterConfigCrc(fileIndex: ProjectFileIndex): Int {
    ApplicationManager.getApplication().assertReadAccessAllowed()

    return withReadLock {
      val crc = CRC32()
      val profiles = myExplicitProfiles
      updateCrc(crc, profiles.hashCode())

      val allProjects: Collection<MavenProject> = myVirtualFileToProjectMapping.values

      crc.update(allProjects.size and 0xFF)
      for (mavenProject in allProjects) {
        val pomFile = mavenProject.file
        val module = fileIndex.getModuleForFile(pomFile)
        if (module == null) continue

        if (!Comparing.equal(fileIndex.getContentRootForFile(pomFile), pomFile.parent)) continue

        updateCrc(crc, module.name)

        val mavenId = mavenProject.mavenId
        updateCrc(crc, mavenId.groupId)
        updateCrc(crc, mavenId.artifactId)
        updateCrc(crc, mavenId.version)

        val parentId = mavenProject.parentId
        if (parentId != null) {
          updateCrc(crc, parentId.groupId)
          updateCrc(crc, parentId.artifactId)
          updateCrc(crc, parentId.version)
        }

        updateCrc(crc, mavenProject.directory)
        updateCrc(crc, MavenFilteredPropertyPsiReferenceProvider.getDelimitersPattern(mavenProject).pattern())
        updateCrc(crc, mavenProject.modelMap.hashCode())
        updateCrc(crc, mavenProject.resources.hashCode())
        updateCrc(crc, mavenProject.testResources.hashCode())
        updateCrc(crc, getFilterExclusions(mavenProject).hashCode())
        updateCrc(crc, mavenProject.properties.hashCode())

        for (each in mavenProject.filterPropertiesFiles) {
          val file = File(each)
          updateCrc(crc, file.lastModified())
        }

        val outputter = XMLOutputter(Format.getCompactFormat())

        val crcWriter: Writer = object : Writer() {
          override fun write(cbuf: CharArray, off: Int, len: Int) {
            var i = off
            val end = off + len
            while (i < end) {
              crc.update(cbuf[i].code)
              i++
            }
          }

          override fun flush() {
          }

          override fun close() {
          }
        }

        try {
          val resourcePluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin")
          if (resourcePluginCfg != null) {
            outputter.output(resourcePluginCfg, crcWriter)
          }

          val warPluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-war-plugin")
          if (warPluginCfg != null) {
            outputter.output(warPluginCfg, crcWriter)
          }
        }
        catch (e: IOException) {
          LOG.error(e)
        }
      }
      crc.value.toInt()
    }
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
    return withReadLock { myModuleToAggregatorMapping[project] }
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
    while (true) {
      val aggregator = myModuleToAggregatorMapping[rootProject]
      if (aggregator == null) {
        return rootProject
      }
      rootProject = aggregator
    }
  }

  fun getModules(aggregator: MavenProject): List<MavenProject> {
    return withReadLock {
      val modules: List<MavenProject>? = myAggregatorToModuleMapping[aggregator]
      if (modules == null) emptyList() else ArrayList(modules)
    }
  }

  private fun addModule(aggregator: MavenProject, module: MavenProject) {
    withWriteLock {
      var modules = myAggregatorToModuleMapping[aggregator]
      if (modules == null) {
        modules = ArrayList()
        myAggregatorToModuleMapping[aggregator] = modules
      }
      modules.add(module)
      myModuleToAggregatorMapping[module] = aggregator
    }
  }

  @ApiStatus.Internal
  fun removeModule(aggregator: MavenProject, module: MavenProject) {
    withWriteLock {
      val modules = myAggregatorToModuleMapping[aggregator]
      if (modules == null) return@withWriteLock
      modules.remove(module)
      if (modules.isEmpty()) {
        myAggregatorToModuleMapping.remove(aggregator)
      }
      myModuleToAggregatorMapping.remove(module)
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

  fun addListener(l: Listener, disposable: Disposable) {
    if (!myListeners.contains(l)) {
      myListeners.add(l, disposable)
    }
    else {
      MavenLog.LOG.warn("Trying to add the same listener twice")
    }
  }

  @ApiStatus.Internal
  fun addListenersFrom(other: MavenProjectsTree) {
    myListeners.addAll(other.myListeners)
  }

  private fun fireProfilesChanged() {
    for (each in myListeners) {
      each.profilesChanged()
    }
  }

  private fun fireProjectsIgnoredStateChanged(ignored: List<MavenProject>, unignored: List<MavenProject>, fromImport: Boolean) {
    for (each in myListeners) {
      each.projectsIgnoredStateChanged(ignored, unignored, fromImport)
    }
  }

  @ApiStatus.Internal
  fun fireProjectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
    for (each in myListeners) {
      each.projectsUpdated(updated, deleted)
    }
  }

  fun fireProjectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>,
                          nativeMavenProject: NativeMavenProjectHolder?) {
    for (each in myListeners) {
      each.projectResolved(projectWithChanges, nativeMavenProject)
    }
  }

  fun firePluginsResolved(project: MavenProject) {
    for (each in myListeners) {
      each.pluginsResolved(project)
    }
  }

  fun fireFoldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
    for (each in myListeners) {
      each.foldersResolved(projectWithChanges)
    }
  }

  fun fireArtifactsDownloaded(project: MavenProject) {
    for (each in myListeners) {
      each.artifactsDownloaded(project)
    }
  }

  interface Listener : EventListener {
    fun profilesChanged() {
    }

    fun projectsIgnoredStateChanged(ignored: List<MavenProject>,
                                    unignored: List<MavenProject>,
                                    fromImport: Boolean) {
    }

    fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
    }

    fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>,
                        nativeMavenProject: NativeMavenProjectHolder?) {
    }

    fun pluginsResolved(project: MavenProject) {
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
    fun setManagedFiles(paths: List<String>): Updater {
      myManagedFilesPaths.clear()
      myManagedFilesPaths.addAll(paths)
      return this
    }

    fun setRootProjects(roots: List<MavenProject>): Updater {
      myRootProjects.clear()
      myRootProjects.addAll(roots)
      roots.forEach(
        Consumer { root: MavenProject ->
          myVirtualFileToProjectMapping[root.file] = root
        })

      return this
    }

    fun setAggregatorMappings(map: Map<MavenProject, List<MavenProject>>): Updater {
      myAggregatorToModuleMapping.clear()
      myModuleToAggregatorMapping.clear()

      for ((key, value) in map) {
        val result: MutableList<MavenProject> = ArrayList(value)
        myAggregatorToModuleMapping[key] = result
        for (c in result) {
          myModuleToAggregatorMapping[c] = key
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

      addFrom(projectTree) { it.myManagedFilesPaths }
      addFrom(projectTree) { it.myRootProjects }

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
        my.clear()
        my.addAll(set.toList())
      }
    }

    private fun <K, V> addFromMap(projectTree: MavenProjectsTree, getter: (MavenProjectsTree) -> MutableMap<K, V>) {
      val my = getter(this@MavenProjectsTree)
      val theirs = getter(projectTree)
      my.putAll(theirs)
    }

  }

  companion object {
    private val LOG = Logger.getInstance(MavenProjectsTree::class.java)

    private val STORAGE_VERSION = MavenProjectsTree::class.java.simpleName + ".9"

    @JvmStatic
    @Throws(IOException::class)
    fun read(project: Project, file: Path): MavenProjectsTree? {
      val result = MavenProjectsTree(project)

      DataInputStream(BufferedInputStream(Files.newInputStream(file))).use { inputStream ->
        try {
          if (STORAGE_VERSION != inputStream.readUTF()) return null
          result.myManagedFilesPaths = readCollection(inputStream, LinkedHashSet())
          result.myIgnoredFilesPaths = readCollection(inputStream, ArrayList())
          result.myIgnoredFilesPatterns = readCollection(inputStream, ArrayList())
          result.myExplicitProfiles = MavenExplicitProfiles(readCollection(inputStream, HashSet()),
                                                            readCollection(inputStream, HashSet()))
          result.myRootProjects.addAll(readProjectsRecursively(inputStream, result))
        }
        catch (e: IOException) {
          inputStream.close()
          Files.delete(file)
          throw e
        }
        catch (e: Throwable) {
          throw IOException(e)
        }
      }
      return result
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
    private fun readProjectsRecursively(inputStream: DataInputStream,
                                        tree: MavenProjectsTree): MutableList<MavenProject> {
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
            tree.myAggregatorToModuleMapping[project] = modules
            for (eachModule in modules) {
              tree.myModuleToAggregatorMapping[eachModule] = project
            }
          }
        }
      }
      return result
    }

    private fun updateExplicitProfiles(explicitProfiles: MutableCollection<String>,
                                       temporarilyRemovedExplicitProfiles: MutableCollection<String>,
                                       available: Set<String>) {
      val removedProfiles = HashSet(explicitProfiles)
      removedProfiles.removeAll(available)
      temporarilyRemovedExplicitProfiles.addAll(removedProfiles)

      val restoredProfiles = HashSet(temporarilyRemovedExplicitProfiles)
      restoredProfiles.retainAll(available)
      temporarilyRemovedExplicitProfiles.removeAll(restoredProfiles)

      explicitProfiles.removeAll(removedProfiles)
      explicitProfiles.addAll(restoredProfiles)
    }

    private fun mavenProjectToNioPath(mavenProject: MavenProject): Path {
      return Path.of(mavenProject.file.parent.path)
    }

    private fun updateCrc(crc: CRC32, xInt: Int) {
      var x = xInt
      crc.update(x and 0xFF)
      x = x ushr 8
      crc.update(x and 0xFF)
      x = x ushr 8
      crc.update(x and 0xFF)
      x = x ushr 8
      crc.update(x)
    }

    private fun updateCrc(crc: CRC32, l: Long) {
      updateCrc(crc, l.toInt())
      updateCrc(crc, (l ushr 32).toInt())
    }

    private fun updateCrc(crc: CRC32, s: String?) {
      if (s == null) {
        crc.update(111)
      }
      else {
        updateCrc(crc, s.hashCode())
        crc.update(s.length and 0xFF)
      }
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
