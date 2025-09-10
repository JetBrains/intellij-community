// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ContentFolder
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.RunAll
import com.intellij.util.ArrayUtil
import com.intellij.util.Function
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.workspaceModel.ide.legacyBridge.SourceRootTypeRegistry
import junit.framework.TestCase
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.junit.Assume
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path
import kotlin.math.min

private const val MAVEN_4_VERSION = "4.0.0-rc-4"
private val MAVEN_VERSIONS: Array<String> = arrayOf<String>(
  "bundled",
  "4/4.0.0",
)

@RunWith(Parameterized::class)
abstract class MavenMultiVersionImportingTestCase : MavenImportingTestCase() {

  override fun runInDispatchThread(): Boolean {
    return false
  }

  @Parameterized.Parameter(0)
  @JvmField
  var myMavenVersion: String? = null

  @Parameterized.Parameter(1)
  @JvmField
  var myMavenModelVersion: String? = null

  protected val modulesTag: String
    get() = if(isModel410()) "subprojects" else "modules"

  protected val moduleTag: String
    get() = if(isModel410()) "subproject" else "module"

  protected var myWrapperTestFixture: MavenWrapperTestFixture? = null

  protected fun assumeVersionMoreThan(version: String) {
    Assume.assumeTrue("Version should be more than $version",
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion!!), getActualVersion(version)) > 0)
  }


  protected fun forMaven3(r: Runnable) {
    val version: String = getActualVersion(myMavenVersion!!)
    if (version.startsWith("3.")) r.run()
  }

  protected fun forMaven4(r: Runnable) {
    val version: String = getActualVersion(myMavenVersion!!)
    if (version.startsWith("4.")) r.run()
  }

  protected fun forModel40(r: Runnable) {
    if (myMavenModelVersion == MavenConstants.MODEL_VERSION_4_0_0) r.run()
  }

  protected fun forModel41(r: Runnable) {
    if (myMavenModelVersion == MavenConstants.MODEL_VERSION_4_1_0) r.run()
  }


  protected fun assumeModel_4_0_0(message: String) {
    Assume.assumeTrue(message, myMavenModelVersion == MavenConstants.MODEL_VERSION_4_0_0)
  }
  protected fun assumeModel_4_1_0(message: String) {
    Assume.assumeTrue(message, myMavenModelVersion == MavenConstants.MODEL_VERSION_4_1_0)
  }

  protected fun assumeMaven3() {
    val version: String = getActualVersion(myMavenVersion!!)
    Assume.assumeTrue(version.startsWith("3."))
  }

  protected fun assumeMaven4() {
    val version: String = getActualVersion(myMavenVersion!!)
    Assume.assumeTrue(version.startsWith("4."))
  }

  protected fun assumeVersionAtLeast(version: String) {
    Assume.assumeTrue("Version should be $version or more",
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion!!), getActualVersion(version)) >= 0)
  }

  protected fun assumeVersionLessThan(version: String) {
    Assume.assumeTrue("Version should be less than $version",
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion!!), getActualVersion(version)) < 0)
  }

  protected fun assumeVersionNot(version: String) {
    Assume.assumeTrue("Version $version skipped",
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion!!), getActualVersion(version)) != 0)
  }

  protected fun assumeVersion(version: String) {
    Assume.assumeTrue("Version $myMavenVersion is not $version, therefore skipped",
                      VersionComparatorUtil.compare(getActualVersion(myMavenVersion!!), getActualVersion(version)) == 0)
  }

  override fun setUp() {
    super.setUp()
    if ("bundled" == myMavenVersion) {
      MavenDistributionsCache.resolveEmbeddedMavenHome()
      return
    }
    val actualMavenVersion = getActualVersion(myMavenVersion!!)
    if (isMaven4)
      MavenLog.LOG.warn("Running test with Maven $actualMavenVersion")
    myWrapperTestFixture = MavenWrapperTestFixture(project, actualMavenVersion)
    myWrapperTestFixture!!.setUp()
    modelVersion = myMavenModelVersion ?: MavenConstants.MODEL_VERSION_4_0_0
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable {
        if (myWrapperTestFixture != null) {
          myWrapperTestFixture!!.tearDown()
        }
      },
      ThrowableRunnable { super.tearDown() }).run()
  }

  protected val defaultLanguageLevel: LanguageLevel
    get() {
      val version: String = getActualVersion(myMavenVersion!!)
      if (VersionComparatorUtil.compare("3.9.3", version) <= 0) {
        return LanguageLevel.JDK_1_8
      }
      if (VersionComparatorUtil.compare("3.9.0", version) <= 0) {
        return LanguageLevel.JDK_1_7
      }
      return LanguageLevel.JDK_1_5
    }


  protected fun getDefaultPluginVersion(pluginId: String): String {
    if (pluginId == "org.apache.maven:maven-compiler-plugin") {
      if (getActualVersion(myMavenVersion!!) in setOf("3.3.9", "3.5.4", "3.6.3", "3.8.9")) {
        return "3.11.0"
      }
      if (mavenVersionIsOrMoreThan("3.9.7")) {
        return "3.13.0"
      }
      if (mavenVersionIsOrMoreThan("3.9.3")) {
        return "3.11.0"
      }
      if (mavenVersionIsOrMoreThan("3.9.0")) {
        return "3.10.1"
      }
      return "3.1"
    }
    throw IllegalArgumentException(
      "this plugin is not configured yet, consider https://youtrack.jetbrains.com/issue/IDEA-313733/create-matrix-of-plugin-levels-for-different-java-versions")
  }

  protected fun mavenVersionIsOrMoreThan(version: String?): Boolean {
    return StringUtil.compareVersionNumbers(version, getActualVersion(myMavenVersion!!)) <= 0
  }

  protected val isMaven4: Boolean
    get() = StringUtil.compareVersionNumbers(
      getActualVersion(myMavenVersion!!), "4.0") >= 0

  protected fun withModel410Only(value: String?): String? {
    val isRc3 = getActualVersion(myMavenVersion!!).equals("4.0.0-rc-3", true)
    return if (isRc3 || this.myMavenModelVersion == MavenConstants.MODEL_VERSION_4_1_0) value else null
  }

  protected fun isModel410(): Boolean {
    val isRc3 = getActualVersion(myMavenVersion!!).equals("4.0.0-rc-3", true)
    if (isRc3) return true
    return this.isMaven4 && this.myMavenModelVersion == MavenConstants.MODEL_VERSION_4_1_0
  }

  protected fun defaultResources(): Array<String> {
    return arrayOfNotNull("src/main/resources", withModel410Only("src/main/resources-filtered"))
  }

  protected fun defaultTestResources(): Array<String> {
    return arrayOfNotNull("src/test/resources", withModel410Only("src/test/resources-filtered"))
  }

  protected fun allDefaultResources(): Array<String> {
    return ArrayUtil.mergeArrays(defaultResources(), *defaultTestResources())
  }

  protected fun assertDefaultResources(moduleName: String, vararg additionalSources: String) {
    val expectedSources = ArrayUtil.mergeArrays(defaultResources(), *additionalSources)
    assertResources(moduleName, *expectedSources)
  }

  protected fun assertDefaultTestResources(moduleName: String, vararg additionalSources: String) {
    val expectedSources = ArrayUtil.mergeArrays(defaultTestResources(), *additionalSources)
    assertTestResources(moduleName, *expectedSources)
  }

  protected fun assertDefaultResources(moduleName: String, rootType: JpsModuleSourceRootType<*>, vararg additionalSources: String) {
    val expectedSources = ArrayUtil.mergeArrays(defaultResources(), *additionalSources)
    val contentRoot = getContentRoot(moduleName)
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), *expectedSources)
  }

  protected fun assertDefaultTestResources(moduleName: String, rootType: JpsModuleSourceRootType<*>, vararg additionalSources: String) {
    val expectedSources = ArrayUtil.mergeArrays(defaultTestResources(), *additionalSources)
    val contentRoot = getContentRoot(moduleName)
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), *expectedSources)
  }

  protected fun arrayOfNotNull(vararg values: String?): Array<String> {
    return values.filterNotNull().toTypedArray()
  }

  protected fun createStdProjectFolders(subdir: String = "") {
    var subdir = subdir
    if (!subdir.isEmpty()) subdir += "/"

    val folders = ArrayUtil.mergeArrays(allDefaultResources(),
                                        "src/main/java",
                                        "src/test/java"
    )

    createProjectSubDirs(subdir, *folders)
  }

  private fun createProjectSubDirs(subdir: String?, vararg relativePaths: String) {
    for (path in relativePaths) {
      createProjectSubDir(subdir + path)
    }
  }

  protected fun assertRelativeContentRoots(moduleName: String, vararg expectedRelativeRoots: String) {
    val expectedRoots = expectedRelativeRoots
      .map { root -> projectPath.resolve(root).toCanonicalPath() }
      .toTypedArray<String>()
    assertContentRoots(moduleName, *expectedRoots)
  }

  protected fun assertContentRoots(moduleName: String, vararg expectedRoots: String) {
    val actual: MutableList<String> = ArrayList<String>()
    for (e in getContentRoots(moduleName)) {
      actual.add(e.getUrl())
    }
    assertUnorderedPathsAreEqual(actual, expectedRoots.map { VfsUtilCore.pathToUrl(it) })
  }

  protected fun assertContentRoots(moduleName: String, vararg expectedRoots: Path) {
    assertContentRoots(moduleName, *expectedRoots.map { it.toString() }.toTypedArray())
  }

  protected fun assertGeneratedSources(moduleName: String, vararg expectedSources: String) {
    val contentRoot = getContentRoot(moduleName)
    val folders: MutableList<ContentFolder> = ArrayList<ContentFolder>()
    for (folder in contentRoot.getSourceFolders(JavaSourceRootType.SOURCE)) {
      val properties = folder.getJpsElement().getProperties<JavaSourceRootProperties?>(JavaSourceRootType.SOURCE)
      assertNotNull(properties)
      if (properties!!.isForGeneratedSources) {
        folders.add(folder)
      }
    }
    doAssertContentFolders(contentRoot, folders, *expectedSources)
  }

  protected fun assertSources(moduleName: String, expectedSources: Collection<String>) {
    assertSources(moduleName, *expectedSources.toTypedArray())
  }

  protected fun assertSources(moduleName: String, vararg expectedSources: String) {
    doAssertSourceRoots(moduleName, JavaSourceRootType.SOURCE, *expectedSources)
  }

  protected fun assertContentRootSources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.SOURCE), *expectedSources)
  }

  protected fun assertResources(moduleName: String, vararg expectedSources: String) {
    doAssertSourceRoots(moduleName, JavaResourceRootType.RESOURCE, *expectedSources)
  }

  protected fun assertContentRootResources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.RESOURCE), *expectedSources)
  }

  protected fun assertTestSources(moduleName: String, expectedSources: Collection<String>) {
    assertTestSources(moduleName, *expectedSources.toTypedArray())
  }

  protected fun assertTestSources(moduleName: String, vararg expectedSources: String) {
    doAssertSourceRoots(moduleName, JavaSourceRootType.TEST_SOURCE, *expectedSources)
  }

  protected fun assertContentRootTestSources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.TEST_SOURCE), *expectedSources)
  }

  protected fun assertTestResources(moduleName: String, vararg expectedSources: String) {
    doAssertSourceRoots(moduleName, JavaResourceRootType.TEST_RESOURCE, *expectedSources)
  }

  protected fun assertContentRootTestResources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.TEST_RESOURCE), *expectedSources)
  }

  protected fun assertExcludes(moduleName: String, vararg expectedExcludes: String) {
    val moduleEntity = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
    val actualPaths = moduleEntity.contentRoots
      .flatMap { it.excludedUrls }
      .map { Path.of(it.url.url.removePrefix("file://")) }

    doAssertSourceRootPaths(moduleEntity, actualPaths, expectedExcludes.map { Path.of(it) })
  }

  protected fun doAssertSourceRoots(moduleName: String, rootType: JpsModuleSourceRootType<*>, vararg expected: String) {
    val sourceRootTypeRegistry = SourceRootTypeRegistry.getInstance()
    val moduleEntity = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
    val actualPaths = moduleEntity.contentRoots
      .flatMap { it.sourceRoots }
      .filter { sourceRootTypeRegistry.findTypeById(it.rootTypeId) == rootType }
      .map { Path.of(it.url.url.removePrefix("file://")) }

    val expectedPaths = expected.map { Path.of(it) }

    doAssertSourceRootPaths(moduleEntity, actualPaths, expectedPaths)
  }

  private fun doAssertSourceRootPaths(moduleEntity: ModuleEntity, actualPaths: List<Path>, expectedPaths: List<Path>) {
    // compare absolute paths
    if (expectedPaths.all { it.isAbsolute }) {
      assertSameElements("Unexpected list of source roots ", actualPaths, expectedPaths)
      return
    }

    val basePath: Path = MavenImportUtil.findPomXml(project, moduleEntity.name)?.parent?.toNioPath() ?: run {
      assertSize(1, moduleEntity.contentRoots)
      Path.of(moduleEntity.contentRoots.first().url.url.removePrefix("file://"))
    }

    // compare relative paths
    if (expectedPaths.all { !it.isAbsolute }) {
      val actualRelativePaths = actualPaths.map { basePath.relativize(it) }
      assertSameElements("Unexpected list of source roots ", actualRelativePaths.map { it.toString() }, expectedPaths.map { it.toString() })
      return
    }

    // compare absolute + relative paths
    val expectedAbsolutePaths = expectedPaths.map { basePath.resolve(it) }
    assertSameElements("Unexpected list of source roots ", actualPaths, expectedAbsolutePaths)
  }

  @Deprecated("use doAssertSourceRoots instead", ReplaceWith("doAssertSourceRoots(moduleName, rootType, *expected)"))
  protected fun doAssertContentFolders(moduleName: String, rootType: JpsModuleSourceRootType<*>, vararg expected: String) {
    val contentRoot = getContentRoot(moduleName)
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), *expected)
  }

  @Deprecated("use doAssertSourceRoots instead", ReplaceWith("doAssertSourceRoots(moduleName, rootType, *expected)"))
  private fun doAssertContentFolders(
    e: ContentEntry,
    folders: List<ContentFolder>,
    vararg expected: String,
  ) {
    val actual: MutableList<String?> = ArrayList<String?>()
    for (f in folders) {
      val rootUrl = e.getUrl()
      var folderUrl = f.getUrl()

      if (folderUrl.startsWith(rootUrl)) {
        val length = rootUrl.length + 1
        folderUrl = folderUrl.substring(min(length, folderUrl.length))
      }

      actual.add(folderUrl)
    }

    assertSameElements<String>("Unexpected list of folders in content root " + e.getUrl(), actual, listOf<String>(*expected))
  }

  private fun getContentRoot(moduleName: String): ContentEntry {
    val ee = getContentRoots(moduleName)
    val roots: MutableList<String?> = ArrayList<String?>()
    for (e in ee) {
      roots.add(e.getUrl())
    }

    val message = "Several content roots found: [" + StringUtil.join(roots, ", ") + "]"
    TestCase.assertEquals(message, 1, ee.size)

    return ee[0]
  }

  private fun getContentRoot(moduleName: String, path: String): ContentEntry {
    val roots = getContentRoots(moduleName)
    for (e in roots) {
      if (e.getUrl() == VfsUtilCore.pathToUrl(path)) return e
    }
    throw AssertionError("content root not found in module " + moduleName + ":" +
                         "\nExpected root: " + path +
                         "\nExisting roots:" +
                         "\n" + StringUtil.join<ContentEntry?>(roots, Function { it: ContentEntry? -> " * " + it!!.getUrl() }, "\n"))
  }

  protected fun getExpectedSourceLanguageLevel(): LanguageLevel {
    if (mavenVersionIsOrMoreThan("3.9.3")) {
      return LanguageLevel.JDK_1_8
    }
    return LanguageLevel.JDK_1_5
  }

  protected fun getExpectedTargetLanguageLevel(): String {
    if (mavenVersionIsOrMoreThan("3.9.3")) {
      return "1.8"
    }
    return "1.5"
  }

  protected fun getActualMavenVersion(): String {
    return getActualVersion(myMavenVersion!!)
  }

  companion object {
    @Parameterized.Parameters(name = "with Maven-{0} and model-{1}")
    @JvmStatic
    fun getMavenVersions(): List<Array<String>> {
      val mavenVersionsString = System.getProperty("maven.versions.to.run")
      var mavenVersionsToRun: Array<String> = MAVEN_VERSIONS
      if (mavenVersionsString != null && !mavenVersionsString.isEmpty()) {
        mavenVersionsToRun = mavenVersionsString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      }
      return mavenVersionsToRun.map {
        val versionAndModel = it.split('/')
        val version = versionAndModel[0]
        val model = versionAndModel.getOrElse(1) { MavenConstants.MODEL_VERSION_4_0_0 }
        if (model == MavenConstants.MODEL_VERSION_4_0_0 || model == MavenConstants.MODEL_VERSION_4_1_0) {
          return@map arrayOf(version, model)
        }
        throw IllegalStateException("Unknown model: $model from $it")
      }.toList()
    }

    internal fun getActualVersion(version: String): String {
      if (version == "bundled") {
        return MavenDistributionsCache.resolveEmbeddedMavenHome().version!!
      }
      if (version == "4") {
        return MAVEN_4_VERSION
      }
      return version
    }
  }

  protected suspend fun withRealJDK(jdkName: String = "JDK_FOR_MAVEN_TESTS", block: suspend () -> Unit) {
    val fixture = MavenProjectJDKTestFixture(project, jdkName)
    try {
      edtWriteAction {
        fixture.setUp()
      }
      block()
    }
    finally {
      edtWriteAction {
        fixture.tearDown()
      }
    }
  }
}
