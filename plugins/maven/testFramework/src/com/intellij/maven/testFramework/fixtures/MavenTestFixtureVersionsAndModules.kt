// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.maven.testFramework.fixtures

import com.intellij.compiler.CompilerConfiguration
import com.intellij.java.library.LibraryWithMavenCoordinatesProperties
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.RootPolicy
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor.equals
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.jupiter.api.Assumptions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes

// Maven-version assumptions and module / language-level inspection.

fun MavenImportingTestFixture.getActualMavenVersion(): String = MavenTestVersions.getActualVersion(mavenVersion)

fun MavenImportingTestFixture.mavenVersionIsOrMoreThan(version: String): Boolean =
  StringUtil.compareVersionNumbers(version, MavenTestVersions.getActualVersion(mavenVersion)) <= 0

fun MavenImportingTestFixture.assumeMaven3() {
  Assumptions.assumeTrue(MavenTestVersions.getActualVersion(mavenVersion).startsWith("3."))
}

fun MavenImportingTestFixture.assumeMaven4() {
  Assumptions.assumeTrue(MavenTestVersions.getActualVersion(mavenVersion).startsWith("4."))
}

fun MavenImportingTestFixture.assumeModel_4_0_0(message: String) {
  Assumptions.assumeTrue(modelVersion == MavenConstants.MODEL_VERSION_4_0_0, message)
}

fun MavenImportingTestFixture.assumeModel_4_1_0(message: String) {
  Assumptions.assumeTrue(modelVersion == MavenConstants.MODEL_VERSION_4_1_0, message)
}

suspend fun MavenImportingTestFixture.forModel40(block: suspend () -> Unit) {
  if (modelVersion == MavenConstants.MODEL_VERSION_4_0_0) block()
}

suspend fun MavenImportingTestFixture.forModel41(block: suspend () -> Unit) {
  if (modelVersion == MavenConstants.MODEL_VERSION_4_1_0) block()
}

fun MavenImportingTestFixture.assumeVersionAtLeast(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) >= 0)
}

fun MavenImportingTestFixture.assumeVersionMoreThan(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) > 0)
}

fun MavenImportingTestFixture.assumeVersionLessThan(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) < 0)
}

fun MavenImportingTestFixture.assumeVersion(version: String) {
  Assume.assumeTrue("Version $mavenVersion is not $version, therefore skipped",
                    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) == 0)
}

val MavenImportingTestFixture.defaultLanguageLevel: LanguageLevel
  get() {
    val version = MavenTestVersions.getActualVersion(mavenVersion)
    if (VersionComparatorUtil.compare("3.9.3", version) <= 0) return LanguageLevel.JDK_1_8
    if (VersionComparatorUtil.compare("3.9.0", version) <= 0) return LanguageLevel.JDK_1_7
    return LanguageLevel.JDK_1_5
  }

fun MavenTestFixture.getModule(name: String): Module {
  val m = ModuleManager.getInstance(project).findModuleByName(name)
  assertNotNull("Module $name not found", m)
  return m!!
}

fun MavenTestFixture.getSourceLanguageLevelForModule(moduleName: String): LanguageLevel? {
  return LanguageLevelUtil.getCustomLanguageLevel(getModule(moduleName))
}

fun MavenTestFixture.getTargetLanguageLevelForModule(moduleName: String): LanguageLevel? {
  val targetLevel = CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(getModule(moduleName)) ?: return null
  return LanguageLevel.parse(targetLevel)
}

fun MavenTestFixture.assertModuleLibDep(moduleName: String, depName: String) {
  val entry = ModuleRootManager.getInstance(getModule(moduleName)).orderEntries
    .filterIsInstance<LibraryOrderEntry>()
    .find { it.libraryName == depName }
  assertNotNull("Library dependency $depName not found in module $moduleName", entry)
}

fun MavenTestFixture.assertModuleLibDep(
  moduleName: String,
  depName: String,
  classesPath: String?,
  sourcePath: String? = null,
  javadocPath: String? = null,
) {
  val lib = getModuleLibDep(moduleName, depName)
  assertModuleLibDepPath(lib, OrderRootType.CLASSES, if (classesPath == null) null else listOf(classesPath))
  assertModuleLibDepPath(lib, OrderRootType.SOURCES, if (sourcePath == null) null else listOf(sourcePath))
  assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), if (javadocPath == null) null else listOf(javadocPath))
}

private fun MavenTestFixture.getModuleLibDep(moduleName: String, depName: String): LibraryOrderEntry {
  val entry = ModuleRootManager.getInstance(getModule(moduleName)).orderEntries
    .filterIsInstance<LibraryOrderEntry>()
    .find { it.presentableName == depName }
  assertNotNull("Library dependency $depName not found in module $moduleName", entry)
  return entry!!
}

private fun assertModuleLibDepPath(lib: LibraryOrderEntry, type: OrderRootType, paths: List<String>?) {
  if (paths == null) return
  assertUnorderedPathsAreEqual(listOf(*lib.getRootUrls(type)), paths)
  // also check the library because it may contain a slightly different set of urls (e.g. with duplicates)
  assertUnorderedPathsAreEqual(listOf(*lib.library!!.getUrls(type)), paths)
}

fun MavenTestFixture.assertModuleModuleDeps(moduleName: String, vararg expectedDeps: String) {
  assertModuleDeps(moduleName, ModuleOrderEntry::class.java, *expectedDeps)
}

fun MavenTestFixture.assertModuleLibDeps(moduleName: String, vararg expectedDeps: String) {
  assertModuleDeps(moduleName, LibraryOrderEntry::class.java, *expectedDeps)
}

private fun MavenTestFixture.assertModuleDeps(moduleName: String, clazz: Class<*>, vararg expectedDeps: String) {
  assertOrderedElementsAreEqual(collectModuleDepsNames(moduleName, clazz), *expectedDeps)
}

private fun MavenTestFixture.collectModuleDepsNames(moduleName: String, clazz: Class<*>): List<String> {
  val actual: MutableList<String> = ArrayList()
  for (e in getRootManager(moduleName).getOrderEntries()) {
    if (clazz.isInstance(e)) {
      actual.add(e.getPresentableName())
    }
  }
  return actual
}

private fun MavenTestFixture.getRootManager(module: String): ModuleRootManager {
  return ModuleRootManager.getInstance(getModule(module))
}

val MavenImportingTestFixture.isMaven4: Boolean
  get() = StringUtil.compareVersionNumbers(
    getActualMavenVersion(), "4.0") >= 0

fun MavenImportingTestFixture.withModel410Only(value: String?): String? {
  val isRc3 = getActualMavenVersion().equals("4.0.0-rc-3", true)
  return if (isRc3 || this.modelVersion == MavenConstants.MODEL_VERSION_4_1_0) value else null
}

fun MavenImportingTestFixture.isModel410(): Boolean {
  val isRc3 = getActualMavenVersion().equals("4.0.0-rc-3", true)
  if (isRc3) return true
  return this.isMaven4 && this.modelVersion == MavenConstants.MODEL_VERSION_4_1_0
}

fun arrayOfNotNull(vararg values: String?): Array<String> {
  return values.filterNotNull().toTypedArray()
}

fun MavenTestFixture.assertModuleLibDepScope(moduleName: String, depName: String, scope: DependencyScope?) {
  val dep = getModuleLibDep(moduleName, depName)
  assertEquals(scope, dep.scope)
}

fun MavenTestFixture.assertModuleModuleDepScope(moduleName: String, depName: String, scope: DependencyScope) {
  val dep = getModuleModuleDep(moduleName, depName)
  assertEquals(scope, dep.scope)
}

private fun MavenTestFixture.getModuleModuleDep(moduleName: String, depName: String): ModuleOrderEntry {
  return getModuleDep(moduleName, depName, ModuleOrderEntry::class.java)
}

private fun <T> MavenTestFixture.getModuleDep(moduleName: String, depName: String, clazz: Class<T>): T {
  val entry = ModuleRootManager.getInstance(getModule(moduleName)).orderEntries
    .filter { clazz.isInstance(it) }
    .find { it.presentableName == depName }
  assertNotNull("Dependency $depName not found in module $moduleName", entry)
  @Suppress("UNCHECKED_CAST")
  return entry as T
}

fun MavenTestFixture.assertProjectLibraries(vararg expectedNames: String) {
  val actualNames = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries
    .map { it.name ?: "<unnamed>" }
  assertUnorderedElementsAreEqual(actualNames, *expectedNames)
}

fun MavenTestFixture.assertProjectLibraryCoordinates(
  libraryName: String,
  groupId: String?,
  artifactId: String?,
  version: String?,
) {
  assertProjectLibraryCoordinates(libraryName, groupId, artifactId, null, JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING, version)
}

fun MavenTestFixture.assertProjectLibraryCoordinates(
  libraryName: String,
  groupId: String?,
  artifactId: String?,
  classifier: String?,
  packaging: String?,
  version: String?,
) {
  val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName(libraryName)
  assertNotNull("Library [$libraryName] not found", lib)
  val libraryProperties = (lib as LibraryEx?)!!.properties
  assertInstanceOf(libraryProperties, LibraryWithMavenCoordinatesProperties::class.java)
  val coords = (libraryProperties as LibraryWithMavenCoordinatesProperties).mavenCoordinates
  assertNotNull("Expected non-empty maven coordinates", coords)
  assertEquals("Unexpected groupId", groupId, coords!!.groupId)
  assertEquals("Unexpected artifactId", artifactId, coords.artifactId)
  assertEquals("Unexpected classifier", classifier, coords.classifier)
  assertEquals("Unexpected packaging", packaging, coords.packaging)
  assertEquals("Unexpected version", version, coords.version)
}

fun MavenTestFixture.assertMavenizedModule(name: String) {
  assertTrue(MavenProjectsManager.getInstance(project).isMavenizedModule(getModule(name)))
}

fun MavenTestFixture.assertNotMavenizedModule(name: String) {
  assertFalse(MavenProjectsManager.getInstance(project).isMavenizedModule(getModule(name)))
}

/** Name of the OS environment variable that points at the temp dir (used by repository-path tests). */
val MavenTestFixture.envVar: String
  get() = if (SystemInfo.isWindows) "TEMP" else "TMPDIR"

val MavenImportingTestFixture.modulesTag: String get() = if (isModel410()) "subprojects" else "modules"
val MavenImportingTestFixture.moduleTag: String get() = if (isModel410()) "subproject" else "module"

fun MavenImportingTestFixture.forMaven3(r: Runnable) {
  if (getActualMavenVersion().startsWith("3.")) r.run()
}

fun MavenImportingTestFixture.forMaven4(r: Runnable) {
  if (getActualMavenVersion().startsWith("4.")) r.run()
}

val MavenImportingTestFixture.mavenImporterSettings: MavenImportingSettings
  get() = projectsManager.importingSettings

fun MavenImportingTestFixture.assertRootProjects(vararg expectedNames: String?) {
  val actualNames = projectsManager.projectsTree.rootProjects.map { it.mavenId.artifactId }
  assertUnorderedElementsAreEqual(actualNames, *expectedNames)
}

private fun MavenTestFixture.getCompilerExtension(module: String): CompilerModuleExtension? =
  CompilerModuleExtension.getInstance(getRootManager(module).module)

private fun absModulePath(url: String?): String =
  if (url == null) "" else FileUtil.toSystemIndependentName(FileUtil.toCanonicalPath(VirtualFileManager.extractPath(url)))

fun MavenTestFixture.assertModuleOutput(moduleName: String, output: String, testOutput: String) {
  val e = getCompilerExtension(moduleName)!!
  assertFalse(e.isCompilerOutputPathInherited)
  assertEquals(absModulePath(output), absModulePath(e.compilerOutputUrl))
  assertEquals(absModulePath(testOutput), absModulePath(e.compilerOutputUrlForTests))
}

fun MavenTestFixture.assertModuleOutput(moduleName: String, output: Path, testOutput: Path) {
  assertModuleOutput(moduleName, output.toString(), testOutput.toString())
}

val MavenTestFixture.parentPath: Path
  get() = projectPath.parent

fun MavenTestFixture.assertProjectOutput(module: String) {
  assertTrue(getCompilerExtension(module)!!.isCompilerOutputPathInherited)
}

fun MavenTestFixture.assertExportedDeps(moduleName: String, vararg expectedDeps: String) {
  val actual = ArrayList<String?>()
  getRootManager(moduleName).orderEntries().withoutSdk().withoutModuleSourceEntries().exportedOnly().process<Any?>(
    object : RootPolicy<Any?>() {
      override fun visitModuleOrderEntry(e: ModuleOrderEntry, value: Any?): Any? { actual.add(e.moduleName); return null }
      override fun visitLibraryOrderEntry(e: LibraryOrderEntry, value: Any?): Any? { actual.add(e.libraryName); return null }
    }, null)
  assertOrderedElementsAreEqual(actual, *expectedDeps)
}

fun MavenTestFixture.assertModuleLibDep(
  moduleName: String,
  depName: String,
  classesPaths: List<String>,
  sourcePaths: List<String>,
  javadocPaths: List<String>,
) {
  val lib = getModuleLibDep(moduleName, depName)
  assertModuleLibDepPath(lib, OrderRootType.CLASSES, classesPaths)
  assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePaths)
  assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), javadocPaths)
}

// Language-level expectations (2-tier; distinct from the 3-tier defaultLanguageLevel above).
fun MavenImportingTestFixture.getExpectedSourceLanguageLevel(): LanguageLevel =
  if (mavenVersionIsOrMoreThan("3.9.3")) LanguageLevel.JDK_1_8 else LanguageLevel.JDK_1_5

fun MavenImportingTestFixture.getExpectedTargetLanguageLevel(): String =
  if (mavenVersionIsOrMoreThan("3.9.3")) "1.8" else "1.5"

fun MavenImportingTestFixture.fileContentEqual(file1: Path, file2: Path): Boolean =
  file1.readBytes().contentEquals(file2.readBytes())

fun MavenTestFixture.assumeOnLocalEnvironmentOnly(cause: String) {
  Assumptions.assumeTrue(LocalEelDescriptor == project.getEelDescriptor(),
                         "Unable to run the test in non-local environment: $cause")
}

val MavenImportingTestFixture.repositoryPathCanonical: String
  get() = FileUtil.toCanonicalPath(repositoryPath.toString())

fun MavenImportingTestFixture.updateSettingsXmlFully(content: String): VirtualFile {
  val ioFile = dir.resolve("settings.xml")
  Files.createDirectories(ioFile.parent)
  if (!Files.exists(ioFile)) Files.createFile(ioFile)
  VfsRootAccess.allowRootAccess(project, ioFile.toString())
  Files.write(ioFile, content.toByteArray(Charsets.UTF_8))
  val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ioFile)!!
  refreshFiles(listOf(f))
  mavenGeneralSettings.setUserSettingsFile(f.path)
  return f
}