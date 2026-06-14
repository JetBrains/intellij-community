// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertRelativeContentRoots
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.AbstractMavenModuleBuilder
import org.jetbrains.idea.maven.wizards.MavenJavaModuleBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Path

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModuleBuilderSameFolderAsParentTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private var myBuilder: AbstractMavenModuleBuilder? = null

  @BeforeEach
  fun setUp() {
    myBuilder = MavenJavaModuleBuilder()
    setModuleNameAndRoot("module", maven.projectPath)
  }

  private fun setModuleNameAndRoot(name: String, root: Path) {
    myBuilder!!.name = name
    myBuilder!!.moduleFilePath = Path.of(root.toString(), "$name.iml").toString()
    myBuilder!!.setContentEntryPath(root.toString())
  }

  private fun setParentProject(pom: VirtualFile) {
    myBuilder!!.parentProject = maven.projectsManager.findProject(pom)
  }

  private suspend fun createNewModule(id: MavenId) {
    myBuilder!!.projectId = id
    maven.waitForImportWithinTimeout {
      edtWriteAction {
        val model = getInstance(maven.project).getModifiableModel()
        myBuilder!!.createModule(model)
        model.commit()
      }
    }
  }

  @Test
  fun testSameFolderAsParent() = runBlocking {
    val customPomXml = maven.createProjectSubFile("custompom.xml", maven.createPomXml(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        """.trimIndent()))
    maven.importProjectAsync(customPomXml)
    maven.assertModules("project")
    setModuleNameAndRoot("module", maven.projectPath)
    setParentProject(customPomXml)
    createNewModule(MavenId("org.foo", "module", "1.0"))
    maven.assertSources("project", "src/main/java")
    maven.assertTestSources("project", "src/test/java")
    maven.assertDefaultResources("project")
    maven.assertDefaultTestResources("project")

    maven.assertRelativeContentRoots("module", "")

    val module = MavenProjectsManager.getInstance(maven.project).findProject(maven.getModule("module"))
    readAction {
      val domProjectModel = MavenDomUtil.getMavenDomProjectModel(maven.project, module!!.file)
      assertEquals("custompom.xml", domProjectModel!!.getMavenParent().getRelativePath().getRawText())
    }
  }
}