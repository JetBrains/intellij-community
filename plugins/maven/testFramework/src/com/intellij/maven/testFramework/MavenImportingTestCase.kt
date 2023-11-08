// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.application.options.CodeStyle
import com.intellij.compiler.CompilerTestUtil
import com.intellij.java.library.LibraryWithMavenCoordinatesProperties
import com.intellij.maven.testFramework.utils.importMavenProjects
import com.intellij.openapi.application.*
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.CodeStyleSettingsTracker
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.importing.MavenProjectImporter.Companion.isImportToWorkspaceModelEnabled
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.project.preimport.MavenProjectPreImporter
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

abstract class MavenImportingTestCase : MavenTestCase() {
  private var myProjectsManager: MavenProjectsManager? = null
  private var myCodeStyleSettingsTracker: CodeStyleSettingsTracker? = null
  private var myNotificationAware: AutoImportProjectNotificationAware? = null
  private var myProjectTracker: AutoImportProjectTracker? = null
  private var isAutoReloadEnabled = false

  @Throws(Exception::class)
  override fun setUp() {
    assumeThisTestCanBeReusedForPreimport()
    isAutoReloadEnabled = false
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), PathManager.getConfigPath())
    super.setUp()
    myCodeStyleSettingsTracker = CodeStyleSettingsTracker { currentCodeStyleSettings }
    val settingsFile = MavenUtil.resolveGlobalSettingsFile(BundledMaven3)
    if (settingsFile != null) {
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), settingsFile.absolutePath)
    }
    myNotificationAware = AutoImportProjectNotificationAware.getInstance(myProject)
    myProjectTracker = AutoImportProjectTracker.getInstance(myProject)
    myProject.messageBus.connect(testRootDisposable).subscribe(MavenImportListener.TOPIC, MavenImportLoggingListener())
  }

  @Throws(Exception::class)
  override fun tearDown() {
    val projectsManager = myProjectsManager
    runAll(
      ThrowableRunnable<Throwable> { WriteAction.runAndWait<RuntimeException> { JavaAwareProjectJdkTableImpl.removeInternalJdkInTests() } },
      ThrowableRunnable<Throwable> { TestDialogManager.setTestDialog(TestDialog.DEFAULT) },
      ThrowableRunnable<Throwable> { removeFromLocalRepository("test") },
      ThrowableRunnable<Throwable> { CompilerTestUtil.deleteBuildSystemDirectory(myProject) },
      ThrowableRunnable<Throwable> { projectsManager?.waitForAfterImportJobs() },
      ThrowableRunnable<Throwable> { myProjectsManager = null },
      ThrowableRunnable<Throwable> { super.tearDown() },
      ThrowableRunnable<Throwable> {
        if (myCodeStyleSettingsTracker != null) {
          myCodeStyleSettingsTracker!!.checkForSettingsDamage()
        }
      }
    )
  }

  override fun useDirectoryBasedProjectFormat(): Boolean {
    return true
  }

  val isWorkspaceImport: Boolean
    get() = isImportToWorkspaceModelEnabled(myProject)

  fun supportModuleGroups(): Boolean {
    return !isWorkspaceImport
  }

  fun supportsKeepingManualChanges(): Boolean {
    return !isWorkspaceImport
  }

  fun supportsImportOfNonExistingFolders(): Boolean {
    return isWorkspaceImport
  }

  fun supportsKeepingModulesFromPreviousImport(): Boolean {
    return !isWorkspaceImport
  }

  fun supportsLegacyKeepingFoldersFromPreviousImport(): Boolean {
    return !isWorkspaceImport
  }

  fun supportsKeepingFacetsFromPreviousImport(): Boolean {
    return !isWorkspaceImport
  }

  fun supportsCreateAggregatorOption(): Boolean {
    return !isWorkspaceImport
  }


  @Throws(Exception::class)
  override fun setUpInWriteAction() {
    super.setUpInWriteAction()
    myProjectsManager = MavenProjectsManager.getInstance(myProject)
    removeFromLocalRepository("test")
  }

  @Suppress("unused")
  protected fun mn(parent: String /* can be used to prepend module name depending on the importing settings*/,
                   moduleName: String): String {
    return moduleName
  }

  protected fun assertModules(vararg expectedNames: String) {
    val actual = ModuleManager.getInstance(myProject).modules
    val actualNames: MutableList<String> = ArrayList()
    for (m in actual) {
      actualNames.add(m.getName())
    }
    assertUnorderedElementsAreEqual(actualNames, *expectedNames)
  }

  protected fun assertRootProjects(vararg expectedNames: String?) {
    val rootProjects = projectsManager.getProjectsTree().rootProjects
    val actualNames = ContainerUtil.map(rootProjects) { it: MavenProject -> it.mavenId.artifactId }
    assertUnorderedElementsAreEqual(actualNames, *expectedNames)
  }

  protected val projectsTree: MavenProjectsTree
    protected get() = projectsManager.getProjectsTree()

  protected fun assertModuleOutput(moduleName: String, output: String?, testOutput: String?) {
    val e = getCompilerExtension(moduleName)
    assertFalse(e!!.isCompilerOutputPathInherited())
    assertEquals(output, getAbsolutePath(e.getCompilerOutputUrl()))
    assertEquals(testOutput, getAbsolutePath(e.getCompilerOutputUrlForTests()))
  }

  protected val projectsManager: MavenProjectsManager
    protected get() = myProjectsManager!!

  protected fun assertProjectOutput(module: String) {
    assertTrue(getCompilerExtension(module)!!.isCompilerOutputPathInherited())
  }

  protected fun getCompilerExtension(module: String): CompilerModuleExtension? {
    val m = getRootManager(module)
    return CompilerModuleExtension.getInstance(m.getModule())
  }

  @JvmOverloads
  protected fun assertModuleLibDep(moduleName: String,
                                   depName: String,
                                   classesPath: String? = null,
                                   sourcePath: String? = null,
                                   javadocPath: String? = null) {
    val lib = getModuleLibDep(moduleName, depName)
    assertModuleLibDepPath(lib, OrderRootType.CLASSES, if (classesPath == null) null else listOf(classesPath))
    assertModuleLibDepPath(lib, OrderRootType.SOURCES, if (sourcePath == null) null else listOf(sourcePath))
    assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), if (javadocPath == null) null else listOf(javadocPath))
  }

  protected fun assertModuleLibDep(moduleName: String,
                                   depName: String,
                                   classesPaths: List<String>,
                                   sourcePaths: List<String>,
                                   javadocPaths: List<String>) {
    val lib = getModuleLibDep(moduleName, depName)
    assertModuleLibDepPath(lib, OrderRootType.CLASSES, classesPaths)
    assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePaths)
    assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), javadocPaths)
  }

  protected fun assertModuleLibDepScope(moduleName: String, depName: String, scope: DependencyScope?) {
    val dep = getModuleLibDep(moduleName, depName)
    assertEquals(scope, dep.getScope())
  }

  protected fun getModuleLibDep(moduleName: String, depName: String): LibraryOrderEntry {
    return getModuleDep(moduleName, depName, LibraryOrderEntry::class.java)!!
  }

  protected fun assertModuleLibDeps(moduleName: String, vararg expectedDeps: String) {
    assertModuleDeps(moduleName, LibraryOrderEntry::class.java, *expectedDeps)
  }

  protected fun assertExportedDeps(moduleName: String, vararg expectedDeps: String?) {
    val actual: MutableList<String?> = ArrayList()
    getRootManager(moduleName).orderEntries().withoutSdk().withoutModuleSourceEntries().exportedOnly().process<Any?>(
      object : RootPolicy<Any?>() {
        override fun visitModuleOrderEntry(e: ModuleOrderEntry, value: Any?): Any? {
          actual.add(e.getModuleName())
          return null
        }

        override fun visitLibraryOrderEntry(e: LibraryOrderEntry, value: Any?): Any? {
          actual.add(e.getLibraryName())
          return null
        }
      }, null)
    assertOrderedElementsAreEqual(actual, *expectedDeps)
  }

  protected fun assertModuleModuleDeps(moduleName: String, vararg expectedDeps: String) {
    assertModuleDeps(moduleName, ModuleOrderEntry::class.java, *expectedDeps)
  }

  private fun assertModuleDeps(moduleName: String, clazz: Class<*>, vararg expectedDeps: String) {
    assertOrderedElementsAreEqual(collectModuleDepsNames(moduleName, clazz), *expectedDeps)
  }

  protected fun assertModuleModuleDepScope(moduleName: String, depName: String, scope: DependencyScope?) {
    val dep = getModuleModuleDep(moduleName, depName)
    assertEquals(scope, dep.getScope())
  }

  private fun getModuleModuleDep(moduleName: String, depName: String): ModuleOrderEntry {
    return getModuleDep(moduleName, depName, ModuleOrderEntry::class.java)!!
  }

  private fun collectModuleDepsNames(moduleName: String, clazz: Class<*>): List<String> {
    val actual: MutableList<String> = ArrayList()
    for (e in getRootManager(moduleName).getOrderEntries()) {
      if (clazz.isInstance(e)) {
        actual.add(e.getPresentableName())
      }
    }
    return actual
  }

  private fun <T> getModuleDep(moduleName: String, depName: String, clazz: Class<T>): T? {
    var dep: T? = null
    for (e in getRootManager(moduleName).getOrderEntries()) {
      if (clazz.isInstance(e) && e.getPresentableName() == depName) {
        dep = e as T
      }
    }
    assertNotNull("""
  Dependency not found: $depName
  among: ${collectModuleDepsNames(moduleName, clazz)}
  """.trimIndent(),
                  dep)
    return dep
  }

  fun assertProjectLibraries(vararg expectedNames: String) {
    val actualNames: MutableList<String> = ArrayList()
    for (each in LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraries()) {
      val name = each.getName()
      actualNames.add(name ?: "<unnamed>")
    }
    assertUnorderedElementsAreEqual(actualNames, *expectedNames)
  }

  fun assertProjectLibraryCoordinates(libraryName: String,
                                      groupId: String?,
                                      artifactId: String?,
                                      version: String?) {
    assertProjectLibraryCoordinates(libraryName, groupId, artifactId, null, JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING, version)
  }

  fun assertProjectLibraryCoordinates(libraryName: String,
                                      groupId: String?,
                                      artifactId: String?,
                                      classifier: String?,
                                      packaging: String?,
                                      version: String?) {
    val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(libraryName)
    assertNotNull("Library [$libraryName] not found", lib)
    val libraryProperties = (lib as LibraryEx?)!!.getProperties()
    assertInstanceOf(libraryProperties, LibraryWithMavenCoordinatesProperties::class.java)
    val coords = (libraryProperties as LibraryWithMavenCoordinatesProperties).mavenCoordinates
    assertNotNull("Expected non-empty maven coordinates", coords)
    assertEquals("Unexpected groupId", groupId, coords!!.groupId)
    assertEquals("Unexpected artifactId", artifactId, coords.artifactId)
    assertEquals("Unexpected classifier", classifier, coords.classifier)
    assertEquals("Unexpected packaging", packaging, coords.packaging)
    assertEquals("Unexpected version", version, coords.version)
  }

  protected fun assertModuleGroupPath(moduleName: String, vararg expected: String) {
    assertModuleGroupPath(moduleName, false, *expected)
  }

  protected fun assertModuleGroupPath(moduleName: String, groupWasManuallyAdded: Boolean, vararg expected: String) {
    val moduleGroupsSupported = supportModuleGroups() || groupWasManuallyAdded && supportsKeepingManualChanges()
    val path = ModuleManager.getInstance(myProject).getModuleGroupPath(getModule(moduleName)!!)
    if (!moduleGroupsSupported || expected.size == 0) {
      assertNull(path)
    }
    else {
      assertNotNull(path)
      assertOrderedElementsAreEqual(listOf(*path!!), *expected)
    }
  }

  protected fun getModule(name: String): Module {
    val m = ReadAction.compute<Module?, RuntimeException> { ModuleManager.getInstance(myProject).findModuleByName(name) }
    assertNotNull("Module $name not found", m)
    return m
  }

  protected fun assertMavenizedModule(name: String) {
    assertTrue(MavenProjectsManager.getInstance(myProject).isMavenizedModule(getModule(name)))
  }

  protected fun assertNotMavenizedModule(name: String) {
    assertFalse(MavenProjectsManager.getInstance(myProject).isMavenizedModule(getModule(name)))
  }

  fun getContentRoots(moduleName: String): Array<ContentEntry> {
    return getRootManager(moduleName).getContentEntries()
  }

  fun getRootManager(module: String): ModuleRootManager {
    return ModuleRootManager.getInstance(getModule(module))
  }

  @Obsolete
  // use importProjectAsync(String)
  protected open fun importProject(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String) {
    createProjectPom(xml)
    importProject()
  }

  protected suspend fun importProjectAsync(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String) {
    createProjectPom(xml)
    importProjectAsync()
  }

  @Obsolete
  // use importProjectAsync()
  protected fun importProject() {
    importProjectWithProfiles()
  }

  protected suspend fun importProjectAsync() {
    importProjectsAsync(listOf(myProjectPom))
  }

  protected suspend fun importProjectAsync(file: VirtualFile) {
    importProjectsAsync(listOf(file))
  }

  protected suspend fun importProjectsAsync(vararg files: VirtualFile) {
    importProjectsAsync(listOf(*files))
  }

  protected suspend fun importProjectsAsync(files: List<VirtualFile>) {
    if (preimportTestMode) {
      val activity = ProjectImportCollector.IMPORT_ACTIVITY.started(myProject)
      try {
        MavenProjectPreImporter.getInstance(myProject)
          .preimport(files, null, mavenImporterSettings, mavenGeneralSettings, activity)
      }
      finally {
        activity.finished()
      }


    }
    else {
      initProjectsManager(false)
      projectsManager.addManagedFilesWithProfilesAndUpdate(files, MavenExplicitProfiles.NONE, null, null)
      projectsManager.waitForAfterImportJobs()
    }


  }

  protected fun importProjectWithErrors() {
    val files = listOf(myProjectPom)
    doImportProjects(files, false)
    importMavenProjects(projectsManager, files)
  }

  protected fun importProjectWithProfiles(vararg profiles: String) {
    doImportProjects(listOf(myProjectPom), true, *profiles)
  }

  @Obsolete
  // use importProjectAsync()
  protected fun importProject(file: VirtualFile) {
    importProjects(file)
  }

  @Obsolete
  // use importProjectAsync()
  protected fun importProjects(vararg files: VirtualFile) {
    doImportProjects(listOf(*files), true)
  }

  protected fun importProjectsWithErrors(vararg files: VirtualFile) {
    doImportProjects(listOf(*files), false)
  }

  protected fun setIgnoredFilesPathForNextImport(paths: List<String?>) {
    projectsManager.setIgnoredFilesPaths(paths)
  }

  protected fun setIgnoredPathPatternsForNextImport(patterns: List<String?>) {
    projectsManager.setIgnoredFilesPatterns(patterns)
  }

  protected open fun doImportProjects(files: List<VirtualFile>, failOnReadingError: Boolean, vararg profiles: String) {
    doImportProjects(files, failOnReadingError, emptyList<String>(), *profiles)
  }


  protected fun doImportProjects(files: List<VirtualFile>, failOnReadingError: Boolean,
                                 disabledProfiles: List<String>, vararg profiles: String) {
    assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed())
    initProjectsManager(false)
    readProjects(files, disabledProfiles, *profiles)
    resolvePlugins()
    val promise = projectsManager.waitForImportCompletion()
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.waitForPromise(promise) }
    if (failOnReadingError) {
      for (each in projectsManager.getProjectsTree().projects) {
        assertFalse("Failed to import Maven project: " + each.getProblems(), each.hasReadingProblems())
      }
    }
  }

  protected fun waitForImportCompletion() {
    edt<RuntimeException> {
      PlatformTestUtil.waitForPromise(
        projectsManager.waitForImportCompletion(), 60000)
    }
  }

  protected fun readProjects(files: List<VirtualFile>, vararg profiles: String) {
    readProjects(files, emptyList<String>(), *profiles)
  }

  protected fun readProjects(files: List<VirtualFile>, disabledProfiles: List<String>, vararg profiles: String) {
    projectsManager.resetManagedFilesAndProfilesInTests(files, MavenExplicitProfiles(listOf(*profiles), disabledProfiles))
    waitForImportCompletion()
  }

  protected fun updateProjectsAndImport(vararg files: VirtualFile) {
    readProjects(*files)
  }

  protected fun initProjectsManager(enableEventHandling: Boolean) {
    projectsManager.initForTests()
    if (enableEventHandling) {
      enableAutoReload()
    }
  }

  protected fun enableAutoReload() {
    projectsManager.enableAutoImportInTests()
    isAutoReloadEnabled = true
  }

  private fun assertAutoReloadIsInitialized() {
    assertTrue("Auto-reload is disabled for tests by default", isAutoReloadEnabled)
  }

  protected fun assertHasPendingProjectForReload() {
    assertAutoReloadIsInitialized()
    assertTrue("Expected notification about pending projects for auto-reload", myNotificationAware!!.isNotificationVisible())
    assertNotEmpty(myNotificationAware!!.getProjectsWithNotification())
  }

  protected fun assertNoPendingProjectForReload() {
    assertAutoReloadIsInitialized()
    assertFalse(myNotificationAware!!.isNotificationVisible())
    assertEmpty(myNotificationAware!!.getProjectsWithNotification())
  }

  @RequiresBackgroundThread
  // TODO: suspend
  protected fun scheduleProjectImportAndWait() {
    assertAutoReloadIsInitialized()

    // otherwise all imports will be skipped
    assertHasPendingProjectForReload()
    runBlocking {
      waitForImportWithinTimeout {
        withContext(Dispatchers.EDT) {
          myProjectTracker!!.scheduleProjectRefresh()
        }
      }
    }
    MavenUtil.invokeAndWait(myProject) {}

    // otherwise project settings was modified while importing
    assertNoPendingProjectForReload()
  }

  protected suspend fun scheduleProjectImportAndWaitAsync() {
    assertAutoReloadIsInitialized()

    // otherwise all imports will be skipped
    assertHasPendingProjectForReload()
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        myProjectTracker!!.scheduleProjectRefresh()
      }
    }

    // otherwise project settings was modified while importing
    assertNoPendingProjectForReload()
  }

  protected suspend fun updateAllProjects() {
    projectsManager.updateAllMavenProjects(MavenImportSpec.EXPLICIT_IMPORT)
    projectsManager.waitForAfterImportJobs()
  }

  protected fun waitForReadingCompletion() {
    ApplicationManager.getApplication().invokeAndWait {
      try {
        projectsManager.waitForReadingCompletion()
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }
  }

  @Throws(Exception::class)
  protected open fun readProjects() {
    readProjects(projectsManager.getProjectsFiles())
  }

  protected fun readProjects(vararg files: VirtualFile?) {
    val projects: MutableList<MavenProject> = ArrayList()
    for (each in files) {
      val mavenProject = projectsManager.findProject(each!!)
      if (null != mavenProject) {
        projects.add(mavenProject)
      }
    }
    projectsManager.scheduleForceUpdateMavenProjects(projects)
    waitForReadingCompletion()
  }

  protected fun resolveDependenciesAndImport() {
    ApplicationManager.getApplication().invokeAndWait { projectsManager.waitForReadingCompletion() }
  }

  protected fun resolvePlugins() {
    projectsManager.waitForImportCompletion()
  }

  protected suspend fun downloadArtifacts() {
    projectsManager.downloadArtifacts(projectsManager.getProjects(), null, true, true)
  }

  protected open fun performPostImportTasks() {}

  @Throws(Exception::class)
  protected fun executeGoal(relativePath: String?, goal: String) {
    val dir = myProjectRoot.findFileByRelativePath(relativePath!!)
    val rp = MavenRunnerParameters(true, dir!!.getPath(), null as String?, listOf(goal), emptyList())
    val rs = MavenRunnerSettings()
    val wait = Semaphore(1)
    wait.acquire()
    MavenRunner.getInstance(myProject).run(rp, rs) { wait.release() }
    val tryAcquire = wait.tryAcquire(10, TimeUnit.SECONDS)
    assertTrue("Maven execution failed", tryAcquire)
  }

  protected fun removeFromLocalRepository(relativePath: String?) {
    if (SystemInfo.isWindows) {
      MavenServerManager.getInstance().shutdown(true)
    }
    FileUtil.delete(File(getRepositoryPath(), relativePath))
  }

  protected fun setupJdkForModules(vararg moduleNames: String) {
    for (each in moduleNames) {
      setupJdkForModule(each)
    }
  }

  protected fun setupJdkForModule(moduleName: String): Sdk {
    val sdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk()
    WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, getTestRootDisposable()) }
    ModuleRootModificationUtil.setModuleSdk(getModule(moduleName), sdk)
    return sdk
  }

  private val currentCodeStyleSettings: CodeStyleSettings
    get() = if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) CodeStyle.createTestSettings()
    else CodeStyle.getSettings(myProject)

  protected fun waitForSmartMode() {
    val promise = AsyncPromise<Void>()
    DumbService.getInstance(myProject).smartInvokeLater { promise.setResult(null) }
    edt<RuntimeException> { PlatformTestUtil.waitForPromise(promise, 60_000) }
  }

  protected suspend fun renameModule(oldName: String, newName: String) {
    val moduleManager = ModuleManager.getInstance(myProject)
    val module = moduleManager.findModuleByName(oldName)!!
    val modifiableModel = moduleManager.getModifiableModel()
    try {
      modifiableModel.renameModule(module, newName)
    }
    catch (e: ModuleWithNameAlreadyExists) {
      throw RuntimeException(e)
    }
    writeAction {
      modifiableModel.commit()
      myProject.getMessageBus().syncPublisher(ModuleListener.TOPIC).modulesRenamed(myProject, listOf(module)) { oldName }
    }
  }

  @RequiresBackgroundThread
  protected suspend fun waitForImportWithinTimeout(action: suspend () -> Any?) {
    MavenLog.LOG.warn("waitForImportWithinTimeout started")
    val importStarted = AtomicBoolean(false)
    val importFinished = AtomicBoolean(false)
    val pluginResolutionFinished = AtomicBoolean(true)
    val artifactDownloadingFinished = AtomicBoolean(true)
    myProject.messageBus.connect(testRootDisposable)
      .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun importStarted() {
          importStarted.set(true)
        }

        override fun importFinished(importedProjects: MutableCollection<MavenProject>, newModules: MutableList<Module>) {
          if (importStarted.get()) {
            importFinished.set(true)
          }
        }

        override fun pluginResolutionStarted() {
          pluginResolutionFinished.set(false)
        }

        override fun pluginResolutionFinished() {
          pluginResolutionFinished.set(true)
        }

        override fun artifactDownloadingStarted() {
          artifactDownloadingFinished.set(false)
        }

        override fun artifactDownloadingFinished() {
          artifactDownloadingFinished.set(true)
        }
      })

    action()

    assertWithinTimeout {
      assertTrue(
        importStarted.get()
        && importFinished.get()
        && pluginResolutionFinished.get()
        && artifactDownloadingFinished.get()
      )
      MavenLog.LOG.warn("waitForImportWithinTimeout finished")
    }
  }

  private class MavenImportLoggingListener : MavenImportListener {
    private val logCounts = ConcurrentHashMap<String, Int>()

    private fun log(method: String, details: String) {
      logCounts.putIfAbsent(method, 0)
      val logCount = logCounts[method]!!.plus(1)
      logCounts[method] = logCount
      val extraDetails = if (details.isEmpty()) "" else ": $details"
      MavenLog.LOG.warn("ImportLogging. $method ($logCount)$extraDetails")
    }

    private fun log(method: String) {
      log(method, "")
    }

    override fun importStarted() {
      log("importStarted")
    }

    override fun importFinished(importedProjects: Collection<MavenProject?>, newModules: List<Module>) {
      log("importFinished", "${importedProjects.size}, ${newModules.size}")
    }

    override fun pomReadingStarted() {
      log("pomReadingStarted")
    }

    override fun pomReadingFinished() {
      log("pomReadingFinished")
    }

    override fun pluginResolutionStarted() {
      log("pluginResolutionStarted")
    }

    override fun pluginResolutionFinished() {
      log("pluginResolutionFinished")
    }

    override fun artifactDownloadingScheduled() {
      log("artifactDownloadingScheduled")
    }

    override fun artifactDownloadingStarted() {
      log("artifactDownloadingStarted")
    }

    override fun artifactDownloadingFinished() {
      log("artifactDownloadingFinished")
    }
  }

  companion object {
    private fun getAbsolutePath(path: String?): String {
      return if (path == null) "" else FileUtil.toSystemIndependentName(FileUtil.toCanonicalPath(VirtualFileManager.extractPath(path)))
    }

    private fun assertModuleLibDepPath(lib: LibraryOrderEntry, type: OrderRootType, paths: List<String>?) {
      if (paths == null) return
      assertUnorderedPathsAreEqual(listOf(*lib.getRootUrls(type)), paths)
      // also check the library because it may contain slight different set of urls (e.g. with duplicates)
      assertUnorderedPathsAreEqual(listOf(*lib.getLibrary()!!.getUrls(type)), paths)
    }

    @JvmStatic
    protected fun createJdk(): Sdk {
      return IdeaTestUtil.getMockJdk17()
    }

    @JvmStatic
    protected fun configConfirmationForYesAnswer(): AtomicInteger {
      val counter = AtomicInteger()
      TestDialogManager.setTestDialog {
        counter.getAndIncrement()
        Messages.YES
      }
      return counter
    }

    @JvmStatic
    protected fun configConfirmationForNoAnswer(): AtomicInteger {
      val counter = AtomicInteger()
      TestDialogManager.setTestDialog {
        counter.getAndIncrement()
        Messages.NO
      }
      return counter
    }
  }
}
