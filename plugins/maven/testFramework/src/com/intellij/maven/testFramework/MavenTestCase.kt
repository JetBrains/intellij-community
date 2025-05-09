// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.UtilBundle
import com.intellij.diagnostic.ThreadDumper
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.*
import com.intellij.testFramework.TemporaryDirectory.Companion.generateTemporaryPath
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.ExceptionUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerConnectorImpl
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenProgressIndicator.MavenProgressTracker
import org.jetbrains.idea.maven.utils.MavenUtil
import java.awt.HeadlessException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

/**
 * This test case uses the NIO API for handling file operations.
 *
 * **Background**:
 * The test framework is transitioning from the `IO` API to the`NIO` API
 *
 * **Implementation Notes**:
 * - `<TestCase>` represents the updated implementation using the `NIO` API.
 * - `<TestCaseLegacy>` represents the legacy implementation using the `IO` API.
 * - For now, both implementations coexist to allow for a smooth transition and backward compatibility.
 * - Eventually, `<TestCaseLegacy>` will be removed from the codebase.
 *
 * **Action Items**:
 * - Prefer using `<TestCase>` for new test cases.
 * - Update existing tests to use `<TestCase>` where possible.
 *
 *
 *
 * **Future Direction**:
 * Once the transition is complete, all test cases relying on the `IO` API will be retired,
 * and the codebase will exclusively use the `NIO` implementation.
 */
abstract class MavenTestCase : UsefulTestCase() {
  protected var mavenProgressIndicator: MavenProgressIndicator? = null
    private set
  private var myWSLDistribution: WSLDistribution? = null
  private var myPathTransformer: RemotePathTransformerFactory.Transformer? = null

  private lateinit var ourTempDir: Path
  private lateinit var myDir: Path

  private var myTestFixture: IdeaProjectTestFixture? = null
  private var myJdk: Sdk? = null

  private var myProject: Project? = null

  private var myProjectRoot: VirtualFile? = null

  private var myProjectPom: VirtualFile? = null
  private val myAllPoms: MutableSet<VirtualFile> = mutableSetOf()

  val pathTransformer: RemotePathTransformerFactory.Transformer
    get() = myPathTransformer!!

  var testFixture: IdeaProjectTestFixture
    get() = myTestFixture!!
    set(testFixture) {
      myTestFixture = testFixture
    }

  fun setTestFixtureNull() {
    myTestFixture = null
  }

  val project: Project
    get() = myProject!!

  val dir: Path
    get() = myDir

  val projectRoot: VirtualFile
    get() = myProjectRoot!!

  var projectPom: VirtualFile
    get() = myProjectPom!!
    set(projectPom) {
      myProjectPom = projectPom
    }

  val allPoms: List<VirtualFile>
    get() = myAllPoms.toList()

  fun addPom(pom: VirtualFile) {
    myAllPoms.add(pom)
  }

  override fun setUp() {
    super.setUp()

    setUpFixtures()
    myProject = myTestFixture!!.project
    myPathTransformer = RemotePathTransformerFactory.createForProject(project)
    setupCustomJdk()
    ensureTempDirCreated()

    myDir = ourTempDir.resolve(getTestName(false))
    myDir.ensureExists()

    mavenProgressIndicator = MavenProgressIndicator(project, EmptyProgressIndicator(ModalityState.nonModal()), null)

    MavenWorkspaceSettingsComponent.getInstance(project).loadState(MavenWorkspacePersistedSettings())

    val home = testMavenHome
    if (home != null) {
      mavenGeneralSettings.mavenHomeType = MavenInSpecificPath(home)
    }

    mavenGeneralSettings.isAlwaysUpdateSnapshots = true

    MavenUtil.cleanAllRunnables()
    MavenSettingsCache.getInstance(project).reload()

    EdtTestUtil.runInEdtAndWait<IOException> {
      restoreSettingsFile()
      try {
        WriteAction.run<Exception> { this.setUpInWriteAction() }
      }
      catch (e: Throwable) {
        try {
          tearDown()
        }
        catch (e1: Exception) {
          e1.printStackTrace()
        }
        throw RuntimeException(e)
      }
    }
  }

  private fun setupCustomJdk() {
    var jdkPath: String? = null
    if (myWSLDistribution != null) {
      jdkPath = System.getProperty("wsl.jdk.path") ?: "/usr/lib/jvm/java-11-openjdk-amd64"
      assertTrue(myWSLDistribution!!.getWindowsPath(myWSLDistribution!!.userHome!!).toNioPathOrNull()!!.isDirectory())
      // SDK might be null; if so, jdkPath will be used to create a JDK instance
      myJdk = findExisingJdkByPath(myWSLDistribution!!.getWindowsPath(jdkPath))
    }
    if (isProjectInEelEnvironment()) {
      jdkPath = getEelFixtureEngineJavaHome()
    }
    if (myJdk == null && jdkPath != null) {
      myJdk = JavaSdk.getInstance().createJdk("Maven Test JDK", jdkPath)
      val jdkTable = ProjectJdkTable.getInstance()
      WriteAction.runAndWait<RuntimeException> { jdkTable.addJdk(myJdk!!) }
    }
    if (myJdk != null) {
      WriteAction.runAndWait<RuntimeException> { ProjectRootManagerEx.getInstanceEx(myProject).projectSdk = myJdk }
    }
  }

  private fun tearDownJdk() {
    if (myJdk != null) {
      WriteAction.runAndWait<RuntimeException> {
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.removeJdk(myJdk!!)
      }
    }
  }


  private fun isProjectInEelEnvironment(): Boolean {
    return System.getenv("EEL_FIXTURE_ENGINE") != null
  }

  private fun getEelFixtureEngineJavaHome(): String {
    return System.getenv("EEL_FIXTURE_ENGINE_JAVA_HOME") ?: throw IllegalArgumentException("The system environment variable EEL_FIXTURE_ENGINE_JAVA_HOME should be explicitly specified")
  }

  protected fun waitForMavenUtilRunnablesComplete() {
    PlatformTestUtil.waitWithEventsDispatching(
      { "Waiting for MavenUtils runnables completed" + MavenUtil.uncompletedRunnables },
      { MavenUtil.noUncompletedRunnables() }, 15)
  }

  private fun isNetworkNameError(t: Throwable, message: String): Boolean {
    return (t.message ?: "").contains("The network name cannot be found") &&
           message.contains("Couldn't read shelf information")
  }

  private fun isJdkAnnotationsError(t: Throwable, category: String): Boolean {
    return "JDK annotations not found" == t.message &&
           "#com.intellij.openapi.projectRoots.impl.JavaSdkImpl" == category
  }

  private fun isLicenseError(message: String): Boolean {
    return "LicenseManager is not installed" == message
  }



  override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
      override fun processError(
        category: String,
        message: String,
        details: Array<String>,
        t: Throwable?,
      ): Set<Action> {
        val intercept = t != null && (isNetworkNameError(t, message) || isJdkAnnotationsError(t, category) || isLicenseError(message))
        return if (intercept) Action.NONE else Action.ALL
      }
    }) { super.runBare(testRunnable) }
  }
  

  private fun findExisingJdkByPath(jdkPath: String): Sdk? {
    val sdk = ProjectJdkTable.getInstance().allJdks.find { jdkPath == it.homePath }!!
    val jdkTable = ProjectJdkTable.getInstance()
    for (existingSdk in jdkTable.allJdks) {
      if (existingSdk === sdk) return sdk
    }
    return null
  }

  override fun tearDown() {
    val basePath = myProject!!.basePath
    RunAll(
      ThrowableRunnable {
        val mavenProgressTracker =
          myProject!!.getServiceIfCreated(MavenProgressTracker::class.java)
        mavenProgressTracker?.assertProgressTasksCompleted()
      },
      ThrowableRunnable { MavenServerManager.getInstance().closeAllConnectorsAndWait() },
      ThrowableRunnable { checkAllMavenConnectorsDisposed() },
      ThrowableRunnable { myProject = null },
      ThrowableRunnable { tearDownJdk() },
      ThrowableRunnable {
        val defaultProject = ProjectManager.getInstance().defaultProject
        val mavenIndicesManager = defaultProject.getServiceIfCreated(MavenIndicesManager::class.java)
        if (mavenIndicesManager != null) {
          Disposer.dispose(mavenIndicesManager)
        }
      },
      ThrowableRunnable { doTearDownFixtures() },
      ThrowableRunnable { deleteDirOnTearDown(myDir) },
      ThrowableRunnable {
        if (myWSLDistribution != null && basePath != null) {
          deleteDirOnTearDown(basePath.toNioPathOrNull())
        }
      },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  private fun doTearDownFixtures() {
    if (ApplicationManager.getApplication().isDispatchThread) {
      EdtTestUtil.runInEdtAndWait<Exception> { tearDownFixtures() }
    }
    else {
      runBlockingMaybeCancellable {
        withContext(Dispatchers.EDT) {
          writeIntentReadAction {
            tearDownFixtures()
          }
        }
      }
    }
  }

  private fun ensureTempDirCreated() {
    ourTempDir = when {
      isProjectInEelEnvironment() -> {
        val fileSystemMount = getFileSystemMount()
        if (fileSystemMount.isBlank()) {
          throw IllegalArgumentException("The EEL_FIXTURE_MOUNT environment variable is not specified")
        }
        Path("$fileSystemMount/mavenTests")
      }
      myWSLDistribution != null -> myWSLDistribution!!.getWindowsPath("/tmp").toNioPathOrNull()!!.resolve("mavenTests")
      else -> FileUtil.getTempDirectory().toNioPathOrNull()!!.resolve("mavenTests")
    }
    FileUtil.delete(ourTempDir)
    ourTempDir.ensureExists()
  }

  protected open fun setUpFixtures() {
    val wslDistributionName = System.getProperty("wsl.distribution.name")
    myTestFixture = when {
      wslDistributionName != null -> setupFixtureOnWsl(wslDistributionName)
      else -> IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name, useDirectoryBasedProjectFormat()).fixture
    }
    myTestFixture!!.setUp()
  }

  private fun setupFixtureOnWsl(wslDistributionName: String): IdeaProjectTestFixture {
    val path = generateTemporaryPath(FileUtil.sanitizeFileName(name, false), Paths.get("\\\\wsl.localhost\\${wslDistributionName}\\tmp"))
    return IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name, path, useDirectoryBasedProjectFormat()).fixture
  }

  protected open fun useDirectoryBasedProjectFormat(): Boolean {
    return false
  }

  protected open fun setUpInWriteAction() {
    val projectDir = myDir.resolve("project")
    projectDir.createDirectories()
    myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir)
  }

  protected open fun tearDownFixtures() {
    try {
      myTestFixture!!.tearDown()
    }
    finally {
      myTestFixture = null
    }
  }

  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
    try {
      super.runTestRunnable(testRunnable)
    }
    catch (throwable: Throwable) {
      if (ExceptionUtil.causedBy(throwable, HeadlessException::class.java)) {
        printIgnoredMessage("Doesn't work in Headless environment")
      }
      throw throwable
    }
  }

  protected val mavenGeneralSettings: MavenGeneralSettings
    get() = MavenProjectsManager.getInstance(myProject!!).generalSettings

  protected val mavenImporterSettings: MavenImportingSettings
    get() = MavenProjectsManager.getInstance(myProject!!).importingSettings

  protected val repositoryPathCanonical: String
    get() = repositoryPath.toCanonicalPath()

  protected var repositoryPath: Path
    get() = MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo()
    set(path) {
      mavenGeneralSettings.setLocalRepository(path.toCanonicalPath())
      MavenSettingsCache.getInstance(project).reload()
    }

  protected fun resetRepositoryFile() {
    mavenGeneralSettings.setLocalRepository(null)
    MavenSettingsCache.getInstance(project).reload()
  }

  protected val projectPath: Path
    get() = myProjectRoot!!.path.toNioPathOrNull()!!

  protected val parentPath: Path
    get() = projectPath.parent

  protected fun pathFromBasedir(relPath: String): String {
    return pathFromBasedir(myProjectRoot, relPath)
  }

  protected fun createSettingsXml(innerContent: String): VirtualFile {
    val content = createSettingsXmlContent(innerContent)
    val path = myDir.resolve("settings.xml")
    Files.writeString(path, content)
    mavenGeneralSettings.setUserSettingsFile(path.toString())
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
  }

  protected suspend fun updateSettingsXml(content: String): VirtualFile {
    return updateSettingsXmlFully(createSettingsXmlContent(content)).also {
      MavenSettingsCache.getInstance(project).reloadAsync()
    }
  }

  protected fun updateSettingsXmlFully(@Language("XML") content: @NonNls String): VirtualFile {
    val ioFile = myDir.resolve("settings.xml")
    ioFile.findOrCreateFile()
    val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ioFile)!!
    setFileContent(f, content)
    refreshFiles(listOf(f))
    mavenGeneralSettings.setUserSettingsFile(f.path)
    return f
  }

  protected fun restoreSettingsFile() {
    updateSettingsXmlFully(createSettingsXmlContent("""
      <mirrors>
        <mirror>
          <id>central-mirror</id>
          <url>https://cache-redirector.jetbrains.com/repo1.maven.org/maven2</url>
          <mirrorOf>central</mirrorOf>
        </mirror>
      </mirrors>
    """.trimIndent()))
  }

  protected fun createModule(name: String, type: ModuleType<*>): Module {
    try {
      return WriteCommandAction.writeCommandAction(myProject).compute<Module, IOException> {
        val f = createProjectSubFile("$name/$name.iml")
        val module = getInstance(myProject!!).newModule(f.path, type.id)
        PsiTestUtil.addContentRoot(module, f.parent)
        module
      }
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  protected fun createModule(name: String): Module = createModule(name, StdModuleTypes.JAVA)


  protected fun createProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
    return createPomFile(projectRoot, xml).also { myProjectPom = it }
  }

  protected fun updateProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
    val pom = createProjectPom(xml)
    refreshFiles(listOf(pom))
    return pom
  }

  protected fun createModulePom(
    relativePath: String,
    @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  ): VirtualFile {
    return createPomFile(createProjectSubDir(relativePath), xml)
  }

  protected fun updateModulePom(
    relativePath: String,
    @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  ): VirtualFile {
    val pom = createModulePom(relativePath, xml)
    refreshFiles(listOf(pom))
    return pom
  }

  protected fun createPomFile(
    dir: VirtualFile,
    @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  ): VirtualFile {
    return createPomFile(dir, "pom.xml", xml)
  }

  protected fun createPomFile(
    dir: VirtualFile, fileName: String = "pom.xml",
    @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  ): VirtualFile {
    val filePath = Path.of(dir.path, fileName)
    setPomContent(filePath, xml)
    dir.refresh(false, false)
    var f = dir.findChild(fileName) ?: throw AssertionError("can't find file ${filePath.absolutePathString()} in VFS")
    myAllPoms.add(f)
    refreshFiles(listOf(f))
    return f
  }

  protected fun createProfilesXmlOldStyle(xml: String): VirtualFile {
    return createProfilesFile(projectRoot, xml, true)
  }

  protected fun createProfilesXmlOldStyle(relativePath: String, xml: String): VirtualFile {
    return createProfilesFile(createProjectSubDir(relativePath), xml, true)
  }

  protected fun createProfilesXml(xml: String): VirtualFile {
    return createProfilesFile(projectRoot, xml, false)
  }

  protected fun createProfilesXml(relativePath: String, xml: String): VirtualFile {
    return createProfilesFile(createProjectSubDir(relativePath), xml, false)
  }

  protected fun createFullProfilesXml(content: String): VirtualFile {
    return createProfilesFile(projectRoot, content)
  }

  protected fun createFullProfilesXml(relativePath: String, content: String): VirtualFile {
    return createProfilesFile(createProjectSubDir(relativePath), content)
  }

  protected fun deleteProfilesXml() {
    WriteCommandAction.writeCommandAction(myProject).run<IOException> {
      val f = myProjectRoot!!.findChild("profiles.xml")
      f?.delete(this)
    }
  }

  protected fun createProjectSubDirs(vararg relativePaths: String) {
    for (path in relativePaths) {
      createProjectSubDir(path)
    }
  }

  protected fun createProjectSubDir(relativePath: String): VirtualFile {
    val f = projectPath.resolve(relativePath)
    f.createDirectories()
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f)!!
  }

  protected fun createFile(path: Path): VirtualFile {
    path.parent.createDirectories()
    path.createFile()
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
  }

  protected fun createProjectSubFile(relativePath: String): VirtualFile {
    val f = projectPath.resolve(relativePath)
    return createFile(f)
  }

  protected fun createProjectSubFile(relativePath: String, content: String): VirtualFile {
    val file = createProjectSubFile(relativePath)
    setFileContent(file, content)
    refreshFiles(listOf(file))
    return file
  }

  protected fun createFile(path: Path, content: String): VirtualFile {
    val file = createFile(path)
    setFileContent(file, content)
    refreshFiles(listOf(file))
    return file
  }

  protected fun updateProjectSubFile(relativePath: String, content: String): VirtualFile {
    val f = projectPath.resolve(relativePath)
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f)!!
    setFileContent(file, content)
    refreshFiles(listOf(file))
    return file
  }

  protected fun refreshFiles(files: List<VirtualFile>) {
    val relativePaths = files.map { dir.relativize(it.path.toNioPathOrNull()!!) }
    MavenLog.LOG.warn("Refreshing files: $relativePaths")
    LocalFileSystem.getInstance().refreshFiles(files)
  }

  protected fun hasMavenInstallation(): Boolean {
    val result = testMavenHome != null
    if (!result) printIgnoredMessage("Maven installation not found")
    return result
  }

  private fun printIgnoredMessage(message: String?) {
    var toPrint = "Ignored"
    if (message != null) {
      toPrint += ", because $message"
    }
    toPrint += ": " + javaClass.simpleName + "." + name
    println(toPrint)
  }

  protected fun <R, E : Throwable?> runWriteAction(computable: ThrowableComputable<R, E>): R {
    return WriteCommandAction.writeCommandAction(myProject).compute(computable)
  }

  protected fun <E : Throwable?> runWriteAction(runnable: ThrowableRunnable<E>) {
    WriteCommandAction.writeCommandAction(myProject).run(runnable)
  }

  protected fun createTestDataContext(pomFile: VirtualFile): DataContext {
    val defaultContext = DataManager.getInstance().dataContext
    return DataContext { dataId: String? ->
      if (CommonDataKeys.PROJECT.`is`(dataId)) {
        return@DataContext myProject
      }
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId)) {
        return@DataContext arrayOf<VirtualFile>(pomFile)
      }
      defaultContext.getData(dataId!!)
    }
  }

  protected val MAVEN_COMPILER_PROPERTIES: String = """
    <properties>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <maven.compiler.source>11</maven.compiler.source>
            <maven.compiler.target>11</maven.compiler.target>
    </properties>
    
    """.trimIndent()

  private fun checkAllMavenConnectorsDisposed() {
    val connectors = MavenServerManager.getInstance().getAllConnectors()
    if (!connectors.isEmpty()) {
      MavenLog.LOG.warn("Connectors not empty, printing thread dump")
      MavenLog.LOG.warn("===============================================")
      MavenLog.LOG.warn(ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false).rawDump)
      MavenLog.LOG.warn("===============================================")
      fail("all maven connectors should be disposed but got $connectors")
    }
  }

  protected fun deleteDirOnTearDown(dir: Path?) {
    FileUtil.delete(dir!!)
    // cannot use reliably the result of the com.intellij.openapi.util.io.FileUtil.delete() method
    // because com.intellij.openapi.util.io.FileUtilRt.deleteRecursivelyNIO() does not honor this contract
    if (dir.exists()) {
      System.err.println("Cannot delete $dir")
      //printDirectoryContent(myDir);
      dir.toFile().deleteOnExit()
    }
  }

  private fun printDirectoryContent(dir: Path) {
    dir.listDirectoryEntries()
      .forEach { file ->
        println(file.toAbsolutePath())
        if (file.isDirectory()) {
          printDirectoryContent(file)
        }
      }
  }

  protected val root: String
    get() {
      if (SystemInfo.isWindows) return "c:"
      return ""
    }

  protected val envVar: String
    get() {
      if (SystemInfo.isWindows) {
        return "TEMP"
      }
      return "TMPDIR"
    }

  protected fun pathFromBasedir(root: VirtualFile?, relPath: String): String {
    return FileUtil.toSystemIndependentName(root!!.path + "/" + relPath)
  }

  private fun createSettingsXmlContent(content: String): String {
    return "<settings>" +
           content +
           "</settings>\r\n"
  }

  private fun createProfilesFile(dir: VirtualFile, xml: String, oldStyle: Boolean): VirtualFile {
    return createProfilesFile(dir, createValidProfiles(xml, oldStyle))
  }

  private fun createProfilesFile(dir: VirtualFile, content: String): VirtualFile {
    val fileName = "profiles.xml"
    val filePath = Path.of(dir.path, fileName)
    setFileContent(filePath, content)
    var f = dir.findChild(fileName)
    if (null == f) {
      refreshFiles(listOf(dir))
      f = dir.findChild(fileName)!!
    }
    refreshFiles(listOf(f))
    return f
  }

  @Language("XML")
  private fun createValidProfiles(@Language("XML") xml: String, oldStyle: Boolean): String {
    if (oldStyle) {
      return "<?xml version=\"1.0\"?>" +
             "<profiles>" +
             xml +
             "</profiles>"
    }
    return "<?xml version=\"1.0\"?>" +
           "<profilesXml>" +
           "<profiles>" +
           xml +
           "</profiles>" +
           "</profilesXml>"
  }

  protected fun setPomContent(file: VirtualFile, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String?) {
    setFileContent(file, createPomXml(xml))
  }

  private fun setPomContent(file: Path, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String?) {
    setFileContent(file, createPomXml(xml))
  }

  private fun setFileContent(file: VirtualFile, content: String) {
    return setFileContent(file.toNioPath(), content)
  }

  private fun setFileContent(file: Path, content: String) {
    val relativePath = dir.relativize(file)
    MavenLog.LOG.warn("Writing content to $relativePath")
    Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
  }

  protected fun <T> assertOrderedElementsAreEqual(actual: Collection<T>, expected: List<T>) {
    val s = "\nexpected: $expected\nactual: $actual"
    assertEquals(s, expected.size, actual.size)

    val actualList: List<T> = ArrayList(actual)
    for (i in expected.indices) {
      val expectedElement = expected[i]
      val actualElement = actualList[i]
      assertEquals(s, expectedElement, actualElement)
    }
  }

  protected fun <T> assertOrderedElementsAreEqual(actual: Collection<T>, vararg expected: T) {
    assertOrderedElementsAreEqual(actual, expected.toList())
  }

  protected fun <T> assertUnorderedElementsAreEqual(actual: Collection<T>, expected: Collection<T>) {
    assertSameElements(actual, expected)
  }

  protected fun assertUnorderedPathsAreEqual(actual: Collection<String>, expected: Collection<String>) {
    assertEquals((CollectionFactory.createFilePathSet(expected)), (CollectionFactory.createFilePathSet(actual)))
  }

  protected fun <T> assertUnorderedElementsAreEqual(actual: Array<T>, vararg expected: T) {
    assertUnorderedElementsAreEqual(actual.toList(), *expected)
  }

  protected fun <T> assertUnorderedElementsAreEqual(actual: Collection<T>, vararg expected: T) {
    assertUnorderedElementsAreEqual(actual, expected.toList())
  }

  protected fun <T> assertContain(actual: Collection<T>, vararg expected: T) {
    val expectedList = expected.toList()
    if (actual.containsAll(expectedList)) return
    val absent: MutableSet<T> = HashSet(expectedList)
    absent.removeAll(actual.toSet())
    fail("""
  expected: $expectedList
  actual: $actual
  this elements not present: $absent
  """.trimIndent())
  }

  protected fun <T> assertDoNotContain(actual: List<T>, vararg expected: T) {
    val actualCopy: MutableList<T> = ArrayList(actual)
    actualCopy.removeAll(expected.toSet())
    assertEquals(actual.toString(), actualCopy.size, actual.size)
  }

  protected fun assertUnorderedLinesWithFile(filePath: String?, expectedText: String?) {
    try {
      assertSameLinesWithFile(filePath!!, expectedText!!)
    }
    catch (e: FileComparisonFailedError) {
      val expected: String = e.expectedStringPresentation
      val actual: String = e.actualStringPresentation
      assertUnorderedElementsAreEqual(expected.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray(),
                                      *actual.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    }
  }

  protected fun ensureConnected(connector: MavenServerConnector): MavenServerConnector {
    assertTrue("Connector is Dummy!", connector is MavenServerConnectorImpl)
    val timeout = TimeUnit.SECONDS.toMillis(10)
    val start = System.currentTimeMillis()
    while (connector.state == MavenServerConnector.State.STARTING) {
      if (System.currentTimeMillis() > start + timeout) {
        throw RuntimeException("Server connector not connected in 10 seconds")
      }
      EdtTestUtil.runInEdtAndWait<RuntimeException> {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }
    }
    assertTrue(connector.checkConnected())
    return connector
  }

  protected fun getFileSystemMount(): String {
    return System.getenv("EEL_FIXTURE_MOUNT") ?: ""
  }

  private val testMavenHome: String?
    get() = System.getProperty("idea.maven.test.home")

  protected fun fileContentEqual(file1: Path, file2: Path): Boolean {
    val file1Bytes = file1.readBytes()
    val file2Bytes = file2.readBytes()
    return file1Bytes.contentEquals(file2Bytes)
  }

  private fun Path.ensureExists() {
    if (!exists()) {
      try {
        createDirectories()
      }
      catch (e: Exception) {
        throw IOException(UtilBundle.message("exception.directory.can.not.create", this), e)
      }
    }
  }

  protected fun getRelativePath(base: Path, path: String) : String {
    return base.relativize(Path.of(path)).toCanonicalPath().toString()
  }

  companion object {
    val preimportTestMode: Boolean = java.lang.Boolean.getBoolean("MAVEN_TEST_PREIMPORT")

    @Language("XML")
    fun createPomXml(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: @NonNls String?): @NonNls String {
      return """
             <?xml version="1.0"?>
             <project xmlns="http://maven.apache.org/POM/4.0.0"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
               <modelVersion>4.0.0</modelVersion>
             
             """.trimIndent() + xml + "</project>"
    }
  }
}
