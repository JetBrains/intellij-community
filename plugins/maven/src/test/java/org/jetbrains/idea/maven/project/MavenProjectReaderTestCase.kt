// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenModel

abstract class MavenProjectReaderTestCase : MavenMultiVersionImportingTestCase() {
  protected suspend fun readProject(file: VirtualFile, vararg profiles: String): MavenModel {
    val readResult = readProject(file, NullProjectLocator(), *profiles)
    assertProblems(readResult)
    return readResult.mavenModel
  }

  protected suspend fun readProject(file: VirtualFile,
                                    locator: MavenProjectReaderProjectLocator,
                                    vararg profiles: String): MavenProjectReaderResult {
    val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    val reader = MavenProjectReader(project, mavenEmbedderWrappers, mavenGeneralSettings, MavenExplicitProfiles(listOf(*profiles)), locator)
    val result = mavenEmbedderWrappers.use { reader.readProjectAsync(file) }
    return result
  }

  protected suspend fun evaluateEffectivePom(file: VirtualFile): String? {
    val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    val result = mavenEmbedderWrappers.use {
      mavenEmbedderWrappers.getEmbedder(projectPath).evaluateEffectivePom(file, emptyList(), emptyList())
    }
    return result
  }

  protected class NullProjectLocator : MavenProjectReaderProjectLocator {
    override fun findProjectFile(coordinates: MavenId): VirtualFile? {
      return null
    }
  }

  protected fun assertProblems(readerResult: MavenProjectReaderResult, vararg expectedProblems: String?) {
    val actualProblems: MutableList<String?> = ArrayList()
    for (each in readerResult.readingProblems) {
      actualProblems.add(each.description)
    }
    assertOrderedElementsAreEqual(actualProblems, *expectedProblems)
  }
}
