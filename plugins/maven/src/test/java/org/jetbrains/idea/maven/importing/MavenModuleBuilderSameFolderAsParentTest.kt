// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.AbstractMavenModuleBuilder
import org.jetbrains.idea.maven.wizards.MavenJavaModuleBuilder
import org.junit.Test
import java.nio.file.Path

class MavenModuleBuilderSameFolderAsParentTest : MavenMultiVersionImportingTestCase() {
  private var myBuilder: AbstractMavenModuleBuilder? = null

  override fun setUp() {
    super.setUp()
    myBuilder = MavenJavaModuleBuilder()
    createJdk()
    setModuleNameAndRoot("module", projectPath)
  }

  private fun setModuleNameAndRoot(name: String, root: Path) {
    myBuilder!!.name = name
    myBuilder!!.moduleFilePath = Path.of(root.toString(), "$name.iml").toString()
    myBuilder!!.setContentEntryPath(root.toString())
  }

  private fun setParentProject(pom: VirtualFile) {
    myBuilder!!.parentProject = projectsManager.findProject(pom)
  }

  private suspend fun createNewModule(id: MavenId) {
    myBuilder!!.projectId = id
    waitForImportWithinTimeout {
      writeAction {
        val model = getInstance(project).getModifiableModel()
        myBuilder!!.createModule(model)
        model.commit()
      }
    }
  }

  @Test
  fun testSameFolderAsParent() = runBlocking {
    val customPomXml = createProjectSubFile("custompom.xml", createPomXml(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        """.trimIndent()))
    importProjectAsync(customPomXml)
    assertModules("project")
    setModuleNameAndRoot("module", projectPath)
    setParentProject(customPomXml)
    createNewModule(MavenId("org.foo", "module", "1.0"))
    val contentRoots = ArrayUtil.mergeArrays(allDefaultResources(),
                                             "src/main/java",
                                             "src/test/java")
    assertRelativeContentRoots("project", *contentRoots)
    assertRelativeContentRoots("module", "")
    val module = MavenProjectsManager.getInstance(project).findProject(getModule("module"))
    readAction {
      val domProjectModel = MavenDomUtil.getMavenDomProjectModel(project, module!!.file)
      assertEquals("custompom.xml", domProjectModel!!.getMavenParent().getRelativePath().getRawText())
    }
  }
}