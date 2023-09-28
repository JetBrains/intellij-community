// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.util.ArrayUtil
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.AbstractMavenModuleBuilder
import org.jetbrains.idea.maven.wizards.MavenJavaModuleBuilder
import org.junit.Assume
import org.junit.Test

class MavenModuleBuilderSameFolderAsParentTest : MavenMultiVersionImportingTestCase() {
  private var myBuilder: AbstractMavenModuleBuilder? = null

  override fun runInDispatchThread() = false

  override fun tearDown() {
    runAll(
      ThrowableRunnable<Throwable> { stopMavenImportManager() },
      ThrowableRunnable<Throwable> { super.tearDown() }
    )
  }

  override fun setUp() {
    super.setUp()
    myBuilder = MavenJavaModuleBuilder()
    createJdk()
    setModuleNameAndRoot("module", projectPath)
  }

  private fun setModuleNameAndRoot(name: String, root: String) {
    myBuilder!!.name = name
    myBuilder!!.moduleFilePath = "$root/$name.iml"
    myBuilder!!.setContentEntryPath(root)
  }

  private fun setParentProject(pom: VirtualFile) {
    myBuilder!!.parentProject = projectsManager.findProject(pom)
  }

  private suspend fun createNewModule(id: MavenId) {
    myBuilder!!.projectId = id
    WriteAction.runAndWait<Exception> {
      val model = getInstance(myProject).getModifiableModel()
      myBuilder!!.createModule(model)
      model.commit()
    }
    updateAllProjects()
  }

  @Test
  fun testSameFolderAsParent() = runBlocking {
    configConfirmationForYesAnswer()
    Assume.assumeFalse(Registry.`is`("maven.linear.import"))
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
    if (supportsImportOfNonExistingFolders()) {
      val contentRoots = ArrayUtil.mergeArrays(allDefaultResources(),
                                               "src/main/java",
                                               "src/test/java")
      assertRelativeContentRoots("project", *contentRoots)
    }
    else {
      assertRelativeContentRoots("project",
                                 "src/main/java",
                                 "src/main/resources",
                                 "src/test/java"
      )
    }
    assertRelativeContentRoots("module", "")
    val module = MavenProjectsManager.getInstance(myProject).findProject(getModule("module"))
    readAction {
      val domProjectModel = MavenDomUtil.getMavenDomProjectModel(myProject, module!!.file)
      assertEquals("custompom.xml", domProjectModel!!.getMavenParent().getRelativePath().getRawText())
    }
  }
}