// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.project.MavenEmbedderWrappersManager
import org.jetbrains.idea.maven.project.MavenProjectReader
import org.jetbrains.idea.maven.project.MavenProjectReaderProjectLocator
import org.jetbrains.idea.maven.project.MavenProjectReaderResult
import org.jetbrains.idea.maven.project.MavenSettingsCache

// Ported from MavenProjectReaderTestCase.

class NullProjectLocator : MavenProjectReaderProjectLocator {
  override fun findProjectFile(coordinates: MavenId): VirtualFile? = null
}

suspend fun MavenImportingTestFixture.readProject(file: VirtualFile, vararg profiles: String): MavenModel {
  val readResult = readProject(file, NullProjectLocator(), *profiles)
  assertProblems(readResult)
  return readResult.mavenModel
}

suspend fun MavenImportingTestFixture.readProject(
  file: VirtualFile,
  locator: MavenProjectReaderProjectLocator,
  vararg profiles: String,
): MavenProjectReaderResult {
  MavenSettingsCache.getInstance(project).reloadAsync()
  val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
  val reader = MavenProjectReader(project, mavenEmbedderWrappers, mavenGeneralSettings, locator)
  val result = mavenEmbedderWrappers.use { reader.readProjectAsync(file) }
  return result
}

suspend fun MavenImportingTestFixture.evaluateEffectivePom(file: VirtualFile): String? {
  val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
  val result = mavenEmbedderWrappers.use {
    mavenEmbedderWrappers.getEmbedder(projectPath).evaluateEffectivePom(file, emptyList(), emptyList())
  }
  return result
}

fun MavenImportingTestFixture.assertProblems(readerResult: MavenProjectReaderResult, vararg expectedProblems: String?) {
  val actualProblems: MutableList<String?> = ArrayList()
  for (each in readerResult.readingProblems) {
    actualProblems.add(each.description)
  }
  assertOrderedElementsAreEqual(actualProblems, *expectedProblems)
}
