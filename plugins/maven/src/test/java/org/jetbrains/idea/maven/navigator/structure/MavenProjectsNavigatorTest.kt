// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure

import com.intellij.execution.impl.RunManagerImpl.Companion.getInstanceImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.awaitConfiguration
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigatorState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsNavigatorTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private var myNavigator: MavenProjectsNavigator? = null
  private var myStructure: MavenProjectsStructure? = null

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.project.replaceService(ToolWindowManager::class.java, object : ToolWindowHeadlessManagerImpl(maven.project) {
      override fun invokeLater(runnable: Runnable) {
        runnable.run()
      }
    }, maven.disposable)
    maven.initProjectsManager(false)

    withContext(Dispatchers.EDT) {
      myNavigator = MavenProjectsNavigator.getInstance(maven.project)
      myNavigator!!.initForTests()
      myNavigator!!.groupModules = true

      myStructure = myNavigator!!.structureForTests()
    }
  }

  @AfterEach
  fun tearDown() {
    myNavigator = null
    myStructure = null
  }

  @Test
  fun testActivation() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    readFiles(maven.projectPom)


    maven.projectsManager.fireActivatedInTests()
    assertEquals(1, rootNodes.size)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
  }

  @Test
  fun testReconnectingModulesWhenModuleRead() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    readFiles(maven.projectPom)

    assertEquals(1, rootNodes.size)
    assertEquals(maven.projectPom, rootNodes[0].virtualFile)
    assertEquals(0, rootNodes[0].projectNodesInTests.size)

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.refreshFiles(listOf(m))
    readFiles(m)

    assertEquals(1, rootNodes.size)
    assertEquals(maven.projectPom, rootNodes[0].virtualFile)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
    assertEquals(m, rootNodes[0].projectNodesInTests[0].virtualFile)
  }

  @Test
  fun testReconnectingModulesWhenParentRead() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(m)

    assertEquals(1, rootNodes.size)
    assertEquals(m, rootNodes[0].virtualFile)

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    readFiles(maven.projectPom)

    assertEquals(1, rootNodes.size)
    assertEquals(maven.projectPom, rootNodes[0].virtualFile)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
    assertEquals(m, rootNodes[0].projectNodesInTests[0].virtualFile)
  }

  @Test
  fun testReconnectingModulesWhenProjectBecomesParent() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.refreshFiles(listOf(m))
    readFiles(maven.projectPom, m)

    assertEquals(2, rootNodes.size)

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    readFiles(maven.projectPom)

    assertEquals(1, rootNodes.size)
    assertEquals(maven.projectPom, rootNodes[0].virtualFile)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
    assertEquals(m, rootNodes[0].projectNodesInTests[0].virtualFile)
  }

  @Test
  fun testUpdatingWhenManagedFilesChange() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    readFiles(maven.projectPom)
    assertEquals(1, rootNodes.size)
    maven.awaitConfiguration()

    maven.waitForImportWithinTimeout {
      maven.projectsManager.removeManagedFiles(listOf(maven.projectPom))
    }
    UsefulTestCase.assertEmpty(maven.projectsManager.getRootProjects())
    assertEquals(0, rootNodes.size)
  }

  @Test
  fun testGroupModulesAndGroupNot() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    myNavigator!!.groupModules = true

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <modules>
        <module>mm</module>
      </modules>
      """.trimIndent())

    val mm = maven.createModulePom("m/mm", """
      <groupId>test</groupId>
      <artifactId>mm</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(maven.projectPom, m, mm)

    assertEquals(1, rootNodes.size)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
    assertEquals(1, rootNodes[0].projectNodesInTests[0].projectNodesInTests.size)

    myNavigator!!.groupModules = false
    assertEquals(3, rootNodes.size)

    myNavigator!!.groupModules = true
    assertEquals(1, rootNodes.size)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
    assertEquals(1, rootNodes[0].projectNodesInTests[0].projectNodesInTests.size)
  }

  @Test
  fun testIgnoringProjects() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(maven.projectPom, m)

    maven.projectsManager.projectsTree.ignoredFilesPaths = listOf(m.getPath())

    myNavigator!!.showIgnored = true
    assertTrue(rootNodes[0].isVisible())
    val childNodeNamesBefore = rootNodes[0].children.map { it.name }.toSet()
    assertEquals(setOf("Lifecycle", "Plugins", "Repositories", "m"), childNodeNamesBefore)

    myNavigator!!.showIgnored = false
    assertTrue(rootNodes[0].isVisible())
    waitForPluginNodesUpdated()
    val childNodeNamesAfter = rootNodes[0].children.map { it.name }.toSet()
    assertEquals(setOf("Lifecycle", "Plugins", "Repositories"), childNodeNamesAfter)
  }

  private suspend fun waitForPluginNodesUpdated() = withContext(Dispatchers.EDT) {
    val pluginsNode = rootNodes[0].pluginsNode
    PlatformTestUtil.waitWithEventsDispatching({ "Waiting for Plugins to be updated" },
                                               { !pluginsNode.pluginNodes.isEmpty() }, 10)
  }

  @Test
  fun testIgnoringParentProjectWhenNeedNoReconnectModule() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(maven.projectPom, m)

    maven.projectsManager.projectsTree.ignoredFilesPaths = listOf(maven.projectPom.getPath())

    myNavigator!!.showIgnored = true
    assertEquals(1, rootNodes.size)
    assertEquals(1, myStructure!!.rootElement.getChildren().size)
    var projectNode = myStructure!!.rootElement.getChildren()[0] as ProjectNode
    assertEquals(maven.projectPom, projectNode.virtualFile)
    assertEquals(1, projectNode.projectNodesInTests.size)

    myNavigator!!.showIgnored = false
    assertEquals(2, rootNodes.size)
    assertEquals(1, myStructure!!.rootElement.getChildren().size) // only one of them is visible
    projectNode = myStructure!!.rootElement.getChildren()[0] as ProjectNode
    assertEquals(m, projectNode.virtualFile)
    assertEquals(0, projectNode.projectNodesInTests.size)
  }

  @Test
  fun testReorderingProjectsWhenNameChanges() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    val m1 = maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(m1, m2)

    assertEquals(2, rootNodes.size)
    assertEquals(m1, rootNodes[0].virtualFile)
    assertEquals(m2, rootNodes[1].virtualFile)

    maven.updateModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>am2</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(m2)

    assertEquals(2, rootNodes.size)
    assertEquals(m2, rootNodes[0].virtualFile)
    assertEquals(m1, rootNodes[1].virtualFile)
  }

  @Test
  fun testReloadingState() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(maven.projectPom, m)

    assertEquals(1, rootNodes.size)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)

    val newState = MavenProjectsNavigatorState()
    newState.groupStructurally = false
    myNavigator!!.loadState(newState)

    assertEquals(2, rootNodes.size)
  }

  @Test
  fun testNavigatableForProjectNode() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    readFiles(maven.projectPom)
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        assertTrue(rootNodes[0].navigatable!!.canNavigateToSource())
      }
    }
  }

  @Test
  fun testCanIterateOverRootNodeChildren() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    readFiles(maven.projectPom)

    val rootNode = myStructure!!.rootElement
    val project = maven.projectsManager.getProjects()[0]
    val node = ProjectNode(myStructure, project)
    rootNode.add(node)
    val children = rootNode.doGetChildren()
    rootNode.remove(node)
    for (child in children) {
      assertNotNull(child)
    }
  }

  @Test
  fun testCanIterateOverProjectNodeChildren() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    readFiles(maven.projectPom)

    val project = maven.projectsManager.getProjects()[0]
    val node = ProjectNode(myStructure, project)
    val projectNode = rootNodes[0]
    projectNode.add(node)
    val children = projectNode.doGetChildren()
    projectNode.remove(node)
    for (child in children) {
      assertNotNull(child)
    }
  }

  @Test
  fun testAddAndRemoveMavenRunConfiguration() = runBlocking {
    maven.projectsManager.fireActivatedInTests()

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(maven.projectPom, m)

    assertEquals(1, rootNodes.size)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)

    val runManager = getInstanceImpl(maven.project)
    val mavenTemplateConfiguration = MavenRunConfigurationType.getInstance().configurationFactories[0].createTemplateConfiguration(
      maven.project)
    val mavenConfiguration = MavenRunConfigurationType.getInstance().configurationFactories[0].createConfiguration("myConfiguration",
                                                                                                                   mavenTemplateConfiguration)
    val configuration = RunnerAndConfigurationSettingsImpl(runManager, mavenConfiguration)
    runManager.addConfiguration(configuration)
    runManager.removeConfiguration(configuration)
  }


  @Test
  fun testRepositoriesListForSimpleProject() = runBlocking {
    maven.updateSettingsXml("")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    readFiles(maven.projectPom)
    maven.projectsManager.fireActivatedInTests()
    assertEquals(1, rootNodes.size)
    val repositoriesNodes = rootNodes[0].listOfRepositoryNodes
    assertEquals(2, repositoriesNodes.size)
    assertEquals("local", repositoriesNodes[0].name)
    assertEquals("central", repositoriesNodes[1].name)
  }

  @Test
  fun testRepositoriesListWithNewRepo() = runBlocking {
    maven.updateSettingsXml("")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       
                       <repositories>
                        <repository>
                          <id>repo-id</id>
                          <name>repo-name</name>
                          <url>https://example.com/repository</url>
                          <snapshots>
                            <enabled>true</enabled>
                          </snapshots>
                        </repository>
                      </repositories>
                       """.trimIndent())

    readFiles(maven.projectPom)
    maven.projectsManager.fireActivatedInTests()
    assertEquals(1, rootNodes.size)
    val repositoriesNodes = rootNodes[0].listOfRepositoryNodes
    assertEquals(3, repositoriesNodes.size)
    assertEquals("local", repositoriesNodes[0].name)
    assertEquals("central", repositoriesNodes[1].name)
    assertEquals("repo-id", repositoriesNodes[2].name)
  }

  private suspend fun readFiles(vararg files: VirtualFile) {
    maven.projectsManager.addManagedFilesWithProfiles(listOf(*files), MavenExplicitProfiles.NONE, null, null, true)
    maven.awaitConfiguration()
  }

  private val rootNodes: List<ProjectNode>
    get() = myStructure!!.rootElement.projectNodesInTests
}
