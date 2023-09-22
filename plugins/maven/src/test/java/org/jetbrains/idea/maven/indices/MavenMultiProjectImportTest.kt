// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.ide.projectWizard.ProjectWizardTestCase
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.maven.testFramework.utils.importMavenProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.write
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.importing.MavenProjectImporter.Companion.isImportToWorkspaceModelEnabled
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.wizards.MavenProjectImportProvider
import org.junit.Assume
import java.nio.file.Path
import java.util.List

class MavenMultiProjectImportTest : ProjectWizardTestCase<AbstractProjectWizard?>() {
  private var myDir: Path? = null

  private val isWorkspaceImport: Boolean
    get() = isImportToWorkspaceModelEnabled(myProject)

  override fun tearDown() {
    RunAll(
      ThrowableRunnable {
        super.tearDown()
      },
      ThrowableRunnable { MavenServerManager.getInstance().shutdown(true) }
    ).run()
  }

  fun testIndicesForDifferentProjectsShouldBeSameInstance() {
    Assume.assumeTrue(isWorkspaceImport)
    myDir = tempDir.newPath("", true)
    val pom1 = createPomXml("projectDir1", """
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      """.trimIndent())
    importMaven(myProject, pom1!!)

    val pom2 = createPomXml("projectDir2", """
      <groupId>test</groupId>
      <artifactId>project2</artifactId>
      <version>1</version>
      """.trimIndent())

    val provider = MavenProjectImportProvider()
    val module = importProjectFrom(pom2!!.getPath(), null, provider)
    val project2 = module.getProject()
    importMaven(project2, pom2)
    MavenIndicesManager.getInstance(project2).updateIndicesListSync()
    MavenIndicesManager.getInstance(myProject).updateIndicesListSync()

    val firstIndices = MavenIndicesManager.getInstance(myProject).getIndex()
    val secondIndices = MavenIndicesManager.getInstance(project2).getIndex()
    Assertions.assertThat(firstIndices.indices).hasSize(2)
    Assertions.assertThat(secondIndices.indices).hasSize(2)
    assertSame(firstIndices.localIndex, secondIndices.localIndex)
    assertSame(firstIndices.remoteIndices[0], secondIndices.remoteIndices[0])
  }

  private fun createPomXml(dir: String, @Language(value = "XML", prefix = "<project>", suffix = "</project>") pomxml: String): VirtualFile? {
    val projectDir = myDir!!.resolve(dir)
    projectDir.toFile().mkdirs()
    val pom = projectDir.resolve("pom.xml")
    pom.write(MavenTestCase.createPomXml(pomxml))
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pom)
  }


  private fun importMaven(project: Project, file: VirtualFile) {
    val manager = MavenProjectsManager.getInstance(project)
    manager.initForTests()
    manager.waitForImportCompletion()
    manager.resetManagedFilesAndProfilesInTests(listOf(file), MavenExplicitProfiles.NONE)
    importMavenProjects(manager, List.of(file))

    val promise = manager.waitForImportCompletion()
    PlatformTestUtil.waitForPromise(promise)
  }
}
