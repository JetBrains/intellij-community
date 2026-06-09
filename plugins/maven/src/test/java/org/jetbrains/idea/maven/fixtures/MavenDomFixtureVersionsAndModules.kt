// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.Assumptions

// Maven-version assumptions and module / language-level inspection.

fun MavenDomTestFixture.getActualMavenVersion(): String = MavenTestVersions.getActualVersion(mavenVersion)

fun MavenDomTestFixture.mavenVersionIsOrMoreThan(version: String): Boolean =
  StringUtil.compareVersionNumbers(version, MavenTestVersions.getActualVersion(mavenVersion)) <= 0

fun MavenDomTestFixture.assumeMaven3() {
  Assumptions.assumeTrue(MavenTestVersions.getActualVersion(mavenVersion).startsWith("3."))
}

fun MavenDomTestFixture.assumeMaven4() {
  Assumptions.assumeTrue(MavenTestVersions.getActualVersion(mavenVersion).startsWith("4."))
}

fun MavenDomTestFixture.assumeModel_4_0_0(message: String) {
  Assumptions.assumeTrue(modelVersion == MavenConstants.MODEL_VERSION_4_0_0, message)
}

fun MavenDomTestFixture.assumeModel_4_1_0(message: String) {
  Assumptions.assumeTrue(modelVersion == MavenConstants.MODEL_VERSION_4_1_0, message)
}

fun MavenDomTestFixture.assumeVersionAtLeast(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) >= 0)
}

fun MavenDomTestFixture.assumeVersionMoreThan(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) > 0)
}

fun MavenDomTestFixture.assumeVersionLessThan(version: String) {
  Assumptions.assumeTrue(
    VersionComparatorUtil.compare(MavenTestVersions.getActualVersion(mavenVersion), MavenTestVersions.getActualVersion(version)) < 0)
}

val MavenDomTestFixture.defaultLanguageLevel: LanguageLevel
  get() {
    val version = MavenTestVersions.getActualVersion(mavenVersion)
    if (VersionComparatorUtil.compare("3.9.3", version) <= 0) return LanguageLevel.JDK_1_8
    if (VersionComparatorUtil.compare("3.9.0", version) <= 0) return LanguageLevel.JDK_1_7
    return LanguageLevel.JDK_1_5
  }

fun MavenDomTestFixture.getModule(name: String): Module {
  val m = ModuleManager.getInstance(project).findModuleByName(name)
  assertNotNull("Module $name not found", m)
  return m!!
}

fun MavenDomTestFixture.getSourceLanguageLevelForModule(moduleName: String): LanguageLevel? {
  return LanguageLevelUtil.getCustomLanguageLevel(getModule(moduleName))
}

fun MavenDomTestFixture.getTargetLanguageLevelForModule(moduleName: String): LanguageLevel? {
  val targetLevel = CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(getModule(moduleName)) ?: return null
  return LanguageLevel.parse(targetLevel)
}

fun MavenDomTestFixture.assertModuleLibDep(moduleName: String, depName: String) {
  val entry = ModuleRootManager.getInstance(getModule(moduleName)).orderEntries
    .filterIsInstance<LibraryOrderEntry>()
    .find { it.libraryName == depName }
  assertNotNull("Library dependency $depName not found in module $moduleName", entry)
}
