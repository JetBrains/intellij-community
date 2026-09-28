// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.ide.GeneralSettings
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.mavenProjectWizardFixture
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.write
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenProjectImportProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

@TestApplication
class MavenMultiProjectImportTest {
  private val maven by mavenProjectWizardFixture()
  private lateinit var myDir: Path

  @BeforeEach
  fun setUp() {
    GeneralSettings.getInstance().confirmOpenNewProject = GeneralSettings.OPEN_PROJECT_NEW_WINDOW
  }

  @AfterEach
  fun tearDown() {
    GeneralSettings.getInstance().confirmOpenNewProject = GeneralSettings.defaultConfirmNewProject()
  }

  @Test
  fun testIndicesForDifferentProjectsShouldBeSameInstance() = runBlocking {
    myDir = Files.createTempDirectory(maven.wizards.contentRoot, "projects")
    val pom1 = writePom("projectDir1", """
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      """.trimIndent())
    importMaven(maven.project, pom1!!)

    val pom2 = writePom("projectDir2", """
      <groupId>test</groupId>
      <artifactId>project2</artifactId>
      <version>1</version>
      """.trimIndent())!!

    val module = maven.wizards.importProjectFrom(pom2.path, null, MavenProjectImportProvider())

    val project2 = module.project
    importMaven(project2, pom2)
    MavenIndicesManager.getInstance(project2).updateIndexList()
    MavenIndicesManager.getInstance(maven.project).updateIndexList()
    MavenSystemIndicesManager.getInstance().waitAllGavsUpdatesCompleted()

    assertEquals(1, MavenSystemIndicesManager.getInstance().getAllGavIndices().size)
  }

  private fun writePom(dir: String, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile? {
    val projectDir = myDir.resolve(dir)
    projectDir.createDirectories()
    val pom = projectDir.resolve("pom.xml")
    pom.write(createPomXml(MavenConstants.MODEL_VERSION_4_0_0, xml, omitModelVersionTag = false))
    return VirtualFileManager.getInstance().refreshAndFindFileByNioPath(pom)
  }

  private suspend fun importMaven(project: Project, file: VirtualFile) {
    val manager = MavenProjectsManager.getInstance(project)
    manager.initForTests()
    manager.addManagedFiles(listOf(file))
    manager.updateAllMavenProjects(MavenSyncSpec.incremental("MavenMultiProjectImportTest"))
  }
}
