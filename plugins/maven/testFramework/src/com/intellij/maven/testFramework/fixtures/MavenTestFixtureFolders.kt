// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.maven.testFramework.fixtures

import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ContentFolder
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.util.ArrayUtil
import com.intellij.workspaceModel.ide.legacyBridge.SourceRootTypeRegistry
import junit.framework.TestCase
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.project.MavenFolderResolver
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.junit.jupiter.api.Assertions.assertNotNull
import java.nio.file.Path
import kotlin.math.min

// Project-folder authoring and source/resource-root assertions, mirroring MavenMultiVersionImportingTestCase /
// MavenImportingTestCase. Source-root inspection reads the workspace model directly (legacy doAssertSourceRoots).

fun MavenImportingTestFixture.defaultResources(): Array<String> =
  arrayOfNotNull("src/main/resources", withModel410Only("src/main/resources-filtered"))

fun MavenImportingTestFixture.defaultTestResources(): Array<String> =
  arrayOfNotNull("src/test/resources", withModel410Only("src/test/resources-filtered"))

// Ported from FoldersImportingTestCase.
suspend fun MavenImportingTestFixture.resolveFoldersAndImport() {
  MavenFolderResolver(projectsManager.project).resolveFoldersAndImport(projectsManager.projects)
}

fun MavenTestFixture.createProjectSubDirsWithFile(vararg dirs: String) {
  for (dir in dirs) {
    createProjectSubFile("$dir/a.txt")
  }
}

fun MavenImportingTestFixture.allDefaultResources(): Array<String> =
  ArrayUtil.mergeArrays(defaultResources(), *defaultTestResources())

fun MavenImportingTestFixture.createStdProjectFolders(subdir: String = "") {
  val prefix = if (subdir.isEmpty()) "" else "$subdir/"
  val folders = ArrayUtil.mergeArrays(allDefaultResources(), "src/main/java", "src/test/java")
  for (path in folders) {
    createProjectSubDir(prefix + path)
  }
}

fun MavenTestFixture.createProjectSubDirs(vararg relativePaths: String) {
  for (path in relativePaths) {
    createProjectSubDir(path)
  }
}

fun MavenTestFixture.assertSources(moduleName: String, vararg expectedSources: String) {
  doAssertSourceRoots(moduleName, JavaSourceRootType.SOURCE, *expectedSources)
}

fun MavenTestFixture.assertResources(moduleName: String, vararg expectedSources: String) {
  doAssertSourceRoots(moduleName, JavaResourceRootType.RESOURCE, *expectedSources)
}

fun MavenTestFixture.assertTestSources(moduleName: String, vararg expectedSources: String) {
  doAssertSourceRoots(moduleName, JavaSourceRootType.TEST_SOURCE, *expectedSources)
}

fun MavenTestFixture.assertTestResources(moduleName: String, vararg expectedSources: String) {
  doAssertSourceRoots(moduleName, JavaResourceRootType.TEST_RESOURCE, *expectedSources)
}

fun MavenImportingTestFixture.assertDefaultResources(moduleName: String, vararg additionalSources: String) {
  val expectedSources = ArrayUtil.mergeArrays(defaultResources(), *additionalSources)
  assertResources(moduleName, *expectedSources)
}

fun MavenImportingTestFixture.assertDefaultTestResources(moduleName: String, vararg additionalSources: String) {
  val expectedSources = ArrayUtil.mergeArrays(defaultTestResources(), *additionalSources)
  assertTestResources(moduleName, *expectedSources)
}

fun MavenTestFixture.assertExcludes(moduleName: String, vararg expectedExcludes: String) {
  val moduleEntity = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
  val actualPaths = moduleEntity.contentRoots
    .flatMap { it.excludedUrls }
    .map { Path.of(it.url.url.removePrefix("file://")) }
  doAssertSourceRootPaths(moduleEntity, actualPaths, expectedExcludes.map { Path.of(it) })
}

fun MavenTestFixture.assertGeneratedSources(moduleName: String, vararg expectedSources: String) {
  val contentRoot = getContentRoot(moduleName)
  val folders = ArrayList<ContentFolder>()
  for (folder in contentRoot.getSourceFolders(JavaSourceRootType.SOURCE)) {
    val properties = folder.jpsElement.getProperties(JavaSourceRootType.SOURCE)
    assertNotNull(properties)
    if (properties!!.isForGeneratedSources) {
      folders.add(folder)
    }
  }
  doAssertContentFolders(contentRoot, folders, *expectedSources)
}

private fun MavenTestFixture.getContentRoots(moduleName: String): Array<ContentEntry> =
  ModuleRootManager.getInstance(getModule(moduleName)).contentEntries

private fun MavenTestFixture.getContentRoot(moduleName: String): ContentEntry {
  val ee = getContentRoots(moduleName)
  val message = "Several content roots found: [" + ee.joinToString(", ") { it.url } + "]"
  TestCase.assertEquals(message, 1, ee.size)
  return ee[0]
}

private fun doAssertContentFolders(e: ContentEntry, folders: List<ContentFolder>, vararg expected: String) {
  val actual = ArrayList<String>()
  for (f in folders) {
    val rootUrl = e.url
    var folderUrl = f.url
    if (folderUrl.startsWith(rootUrl)) {
      val length = rootUrl.length + 1
      folderUrl = folderUrl.substring(min(length, folderUrl.length))
    }
    actual.add(folderUrl)
  }
  assertSameElements("Unexpected list of folders in content root " + e.url, actual, listOf(*expected))
}

private fun MavenTestFixture.doAssertSourceRoots(moduleName: String, rootType: JpsModuleSourceRootType<*>, vararg expected: String) {
  val sourceRootTypeRegistry = SourceRootTypeRegistry.getInstance()
  val moduleEntity = project.workspaceModel.currentSnapshot.resolve(ModuleId(moduleName))!!
  val actualPaths = moduleEntity.contentRoots
    .flatMap { it.sourceRoots }
    .filter { sourceRootTypeRegistry.findTypeById(it.rootTypeId) == rootType }
    .map { Path.of(it.url.url.removePrefix("file://")) }

  val expectedPaths = expected.map { Path.of(it) }

  doAssertSourceRootPaths(moduleEntity, actualPaths, expectedPaths)
}

private fun MavenTestFixture.doAssertSourceRootPaths(moduleEntity: ModuleEntity, actualPaths: List<Path>, expectedPaths: List<Path>) {
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

fun MavenTestFixture.assertContentRoots(moduleName: String, vararg expectedRoots: String) {
  val actual = getContentRoots(moduleName).map { it.url }
  assertUnorderedPathsAreEqual(actual, expectedRoots.map { VfsUtilCore.pathToUrl(it) })
}

fun MavenTestFixture.assertContentRoots(moduleName: String, vararg expectedRoots: Path) {
  assertContentRoots(moduleName, *expectedRoots.map { it.toString() }.toTypedArray())
}

fun MavenTestFixture.assertRelativeContentRoots(moduleName: String, vararg expectedRelativeRoots: String) {
  val expectedRoots = expectedRelativeRoots.map { projectPath.resolve(it).toCanonicalPath() }.toTypedArray()
  assertContentRoots(moduleName, *expectedRoots)
}

private fun MavenTestFixture.getContentRoot(moduleName: String, path: String): ContentEntry {
  val expectedUrl = VfsUtilCore.pathToUrl(path)
  return getContentRoots(moduleName).firstOrNull { it.url == expectedUrl }
         ?: throw AssertionError("Content root $path not found in module $moduleName")
}

fun MavenTestFixture.assertContentRootSources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
  val root = getContentRoot(moduleName, contentRoot)
  doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.SOURCE), *expectedSources)
}

fun MavenTestFixture.assertContentRootResources(moduleName: String, contentRoot: String, vararg expectedResources: String) {
  val root = getContentRoot(moduleName, contentRoot)
  doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.RESOURCE), *expectedResources)
}

fun MavenTestFixture.assertContentRootTestSources(moduleName: String, contentRoot: String, vararg expectedSources: String) {
  val root = getContentRoot(moduleName, contentRoot)
  doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.TEST_SOURCE), *expectedSources)
}

fun MavenTestFixture.assertContentRootTestResources(moduleName: String, contentRoot: String, vararg expectedResources: String) {
  val root = getContentRoot(moduleName, contentRoot)
  doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.TEST_RESOURCE), *expectedResources)
}
