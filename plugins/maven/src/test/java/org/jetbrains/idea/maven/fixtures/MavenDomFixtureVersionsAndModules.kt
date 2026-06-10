// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.compiler.CompilerConfiguration
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase.Companion.getActualVersion
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.idea.maven.fixtures.MavenAssertions.assertOrderedElementsAreEqual
import org.jetbrains.idea.maven.model.MavenConstants
import org.junit.Assert.assertNotNull
import org.junit.Assume
import org.junit.jupiter.api.Assumptions

// Maven-version assumptions and module / language-level inspection.

fun MavenTestFixture.getActualMavenVersion(): String = MavenTestVersions.getActualVersion(mavenVersion)

fun MavenTestFixture.mavenVersionIsOrMoreThan(version: String): Boolean =
  StringUtil.compareVersionNumbers(version, MavenTestVersions.getActualVersion(mavenVersion)) <= 0

fun MavenTestFixture.assumeMaven3() {
  Assumptions.assumeTrue(MavenTestVersions.getActualVersion(mavenVersion).startsWith("3."))
}

fun MavenTestFixture.assumeMaven4() {
  Assumptions.assumeTrue(MavenTestVersions.getActualVersion(mavenVersion).startsWith("4."))
}

fun MavenTestFixture.assumeModel_4_0_0(message: String) {
  Assumptions.assumeTrue(modelVersion == MavenConstants.MODEL_VERSION_4_0_0, message)
}

fun MavenTestFixture.assumeModel_4_1_0(message: String) {
  Assumptions.assumeTrue(modelVersion == MavenConstants.MODEL_VERSION_4_1_0, message)
}

suspend fun MavenTestFixture.forModel40(block: suspend () -> Unit) {
  if (modelVersion == MavenConstants.MODEL_VERSION_4_0_0) block()
}

suspend fun MavenTestFixture.forModel41(block: suspend () -> Unit) {
  if (modelVersion == MavenConstants.MODEL_VERSION_4_1_0) block()
}

fun MavenTestFixture.assumeVersionAtLeast(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) >= 0)
}

fun MavenTestFixture.assumeVersionMoreThan(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) > 0)
}

fun MavenTestFixture.assumeVersionLessThan(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) < 0)
}

fun MavenTestFixture.assumeVersion(version: String) {
  Assume.assumeTrue("Version $mavenVersion is not $version, therefore skipped",
                    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) == 0)
}

val MavenTestFixture.defaultLanguageLevel: LanguageLevel
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

fun MavenTestFixture.assertModuleModuleDeps(moduleName: String, vararg expectedDeps: String) {
  assertModuleDeps(moduleName, ModuleOrderEntry::class.java, *expectedDeps)
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

val MavenTestFixture.isMaven4: Boolean
  get() = StringUtil.compareVersionNumbers(
    getActualMavenVersion(), "4.0") >= 0

fun MavenTestFixture.withModel410Only(value: String?): String? {
  val isRc3 = getActualMavenVersion().equals("4.0.0-rc-3", true)
  return if (isRc3 || this.modelVersion == MavenConstants.MODEL_VERSION_4_1_0) value else null
}

fun MavenTestFixture.isModel410(): Boolean {
  val isRc3 = getActualMavenVersion().equals("4.0.0-rc-3", true)
  if (isRc3) return true
  return this.isMaven4 && this.modelVersion == MavenConstants.MODEL_VERSION_4_1_0
}

fun arrayOfNotNull(vararg values: String?): Array<String> {
  return values.filterNotNull().toTypedArray()
}