// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ContentFolder
import com.intellij.openapi.roots.ExcludeFolder
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.RunAll
import com.intellij.util.ArrayUtil
import com.intellij.util.Function
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.VersionComparatorUtil
import junit.framework.TestCase
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
import java.util.*
import kotlin.math.min

private const val MAVEN_4_VERSION = "4.0.0-rc-3"
private val MAVEN_VERSIONS: Array<String> = arrayOf<String>(
  "bundled",
  "4"
)

@RunWith(Parameterized::class)
abstract class MavenMultiVersionImportingTestCase : MavenImportingTestCase() {

  override fun runInDispatchThread(): Boolean {
    return false
  }

  @Parameterized.Parameter(0)
  @JvmField
  var myMavenVersion: String? = null
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
    MavenLog.LOG.warn("Running test with Maven $actualMavenVersion")
    myWrapperTestFixture = MavenWrapperTestFixture(project, actualMavenVersion)
    myWrapperTestFixture!!.setUp()
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

  protected fun maven4orNull(value: String?): String? {
    return if (this.isMaven4) value else null
  }

  protected fun defaultResources(): Array<String> {
    return arrayOfNotNull("src/main/resources", maven4orNull("src/main/resources-filtered"))
  }

  protected fun defaultTestResources(): Array<String> {
    return arrayOfNotNull("src/test/resources", maven4orNull("src/test/resources-filtered"))
  }

  protected fun allDefaultResources(): Array<String> {
    return ArrayUtil.mergeArrays(defaultResources(), *defaultTestResources())
  }

  protected fun assertDefaultResources(moduleName: String, vararg additionalSources: String?) {
    val expectedSources = ArrayUtil.mergeArrays(defaultResources(), *additionalSources)
    assertResources(moduleName, *expectedSources)
  }

  protected fun assertDefaultTestResources(moduleName: String, vararg additionalSources: String?) {
    val expectedSources = ArrayUtil.mergeArrays(defaultTestResources(), *additionalSources)
    assertTestResources(moduleName, *expectedSources)
  }

  protected fun assertDefaultResources(moduleName: String, rootType: JpsModuleSourceRootType<*>, vararg additionalSources: String?) {
    val expectedSources = ArrayUtil.mergeArrays(defaultResources(), *additionalSources)
    val contentRoot = getContentRoot(moduleName)
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), *expectedSources)
  }

  protected fun assertDefaultTestResources(moduleName: String, rootType: JpsModuleSourceRootType<*>, vararg additionalSources: String?) {
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

  private fun createProjectSubDirs(subdir: String?, vararg relativePaths: String?) {
    for (path in relativePaths) {
      createProjectSubDir(subdir + path)
    }
  }

  protected fun assertRelativeContentRoots(moduleName: String, vararg expectedRelativeRoots: String?) {
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

  protected fun assertSources(moduleName: String, vararg expectedSources: String) {
    doAssertContentFolders(moduleName, JavaSourceRootType.SOURCE, *expectedSources)
  }

  protected fun assertContentRootSources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.SOURCE), *expectedSources)
  }

  protected fun assertResources(moduleName: String, vararg expectedSources: String) {
    doAssertContentFolders(moduleName, JavaResourceRootType.RESOURCE, *expectedSources)
  }

  protected fun assertContentRootResources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.RESOURCE), *expectedSources)
  }

  protected fun assertTestSources(moduleName: String, vararg expectedSources: String) {
    doAssertContentFolders(moduleName, JavaSourceRootType.TEST_SOURCE, *expectedSources)
  }

  protected fun assertContentRootTestSources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.TEST_SOURCE), *expectedSources)
  }

  protected fun assertTestResources(moduleName: String, vararg expectedSources: String) {
    doAssertContentFolders(moduleName, JavaResourceRootType.TEST_RESOURCE, *expectedSources)
  }

  protected fun assertContentRootTestResources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.TEST_RESOURCE), *expectedSources)
  }

  protected fun assertExcludes(moduleName: String, vararg expectedExcludes: String) {
    val contentRoot = getContentRoot(moduleName)
    doAssertContentFolders(contentRoot, Arrays.asList<ExcludeFolder?>(*contentRoot.getExcludeFolders()), *expectedExcludes)
  }

  protected fun assertContentRootExcludes(moduleName: String, contentRoot: String, vararg expectedExcludes: String) {
    val root = getContentRoot(moduleName, contentRoot)
    doAssertContentFolders(root, listOf<ExcludeFolder>(*root.getExcludeFolders()), *expectedExcludes)
  }

  protected fun doAssertContentFolders(moduleName: String, rootType: JpsModuleSourceRootType<*>, vararg expected: String) {
    val contentRoot = getContentRoot(moduleName)
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), *expected)
  }

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

  companion object {
    @Parameterized.Parameters(name = "with Maven-{0}")
    @JvmStatic
    fun getMavenVersions(): List<Array<String>> {
      val mavenVersionsString = System.getProperty("maven.versions.to.run")
      var mavenVersionsToRun: Array<String> = MAVEN_VERSIONS
      if (mavenVersionsString != null && !mavenVersionsString.isEmpty()) {
        mavenVersionsToRun = mavenVersionsString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      }
      return mavenVersionsToRun.map { arrayOf<String>(it) }
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
