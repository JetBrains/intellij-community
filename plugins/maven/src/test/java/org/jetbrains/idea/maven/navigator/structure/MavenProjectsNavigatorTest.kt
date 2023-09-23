// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure

import com.intellij.execution.impl.RunManagerImpl.Companion.getInstanceImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.importing.MavenProjectLegacyImporter
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigatorState
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.importing.FilesList
import org.jetbrains.idea.maven.project.importing.MavenImportFlow
import org.jetbrains.idea.maven.utils.MavenUtil
import org.junit.Test
import java.util.concurrent.TimeUnit

class MavenProjectsNavigatorTest : MavenMultiVersionImportingTestCase() {
  private var myNavigator: MavenProjectsNavigator? = null
  private var myStructure: MavenProjectsStructure? = null

  override fun runInDispatchThread() = false

  override fun setUp() = runBlocking {
    super.setUp()
    myProject.replaceService(ToolWindowManager::class.java, object : ToolWindowHeadlessManagerImpl(myProject) {
      override fun invokeLater(runnable: Runnable) {
        runnable.run()
      }
    }, testRootDisposable)
    initProjectsManager(false)

    withContext(Dispatchers.EDT) {
      myNavigator = MavenProjectsNavigator.getInstance(myProject)
      myNavigator!!.initForTests()
      myNavigator!!.groupModules = true

      myStructure = myNavigator!!.structureForTests
    }
  }

  override fun tearDown() {
    myNavigator = null
    myStructure = null
    super.tearDown()
  }

  @Test
  fun testActivation() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    readFiles(myProjectPom)


    projectsManager.fireActivatedInTests()
    assertEquals(1, rootNodes.size)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
  }

  @Test
  fun testReconnectingModulesWhenModuleRead() = runBlocking {
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    readFiles(myProjectPom)

    assertEquals(1, rootNodes.size)
    assertEquals(myProjectPom, rootNodes[0].virtualFile)
    assertEquals(0, rootNodes[0].projectNodesInTests.size)

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(m)

    assertEquals(1, rootNodes.size)
    assertEquals(myProjectPom, rootNodes[0].virtualFile)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
    assertEquals(m, rootNodes[0].projectNodesInTests[0].virtualFile)
  }

  @Test
  fun testReconnectingModulesWhenParentRead() = runBlocking {
    projectsManager.fireActivatedInTests()

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(m)

    assertEquals(1, rootNodes.size)
    assertEquals(m, rootNodes[0].virtualFile)

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    readFiles(myProjectPom)

    assertEquals(1, rootNodes.size)
    assertEquals(myProjectPom, rootNodes[0].virtualFile)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
    assertEquals(m, rootNodes[0].projectNodesInTests[0].virtualFile)
  }

  @Test
  fun testReconnectingModulesWhenProjectBecomesParent() = runBlocking {
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(myProjectPom, m)

    assertEquals(2, rootNodes.size)

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    readFiles(myProjectPom)

    assertEquals(1, rootNodes.size)
    assertEquals(myProjectPom, rootNodes[0].virtualFile)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)
    assertEquals(m, rootNodes[0].projectNodesInTests[0].virtualFile)
  }

  @Test
  fun testUpdatingWhenManagedFilesChange() = runBlocking {
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    readFiles(myProjectPom)
    resolveDependenciesAndImport()
    assertEquals(1, rootNodes.size)
    MavenUtil.cleanAllRunnables()

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)

    waitForImportWithinTimeout {
      projectsManager.removeManagedFiles(listOf(myProjectPom))
    }
    UsefulTestCase.assertEmpty(projectsManager.getRootProjects())
    assertEquals(0, rootNodes.size)
  }

  @Test
  fun testGroupModulesAndGroupNot() = runBlocking {
    projectsManager.fireActivatedInTests()

    myNavigator!!.groupModules = true

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <modules>
        <module>mm</module>
      </modules>
      """.trimIndent())

    val mm = createModulePom("m/mm", """
      <groupId>test</groupId>
      <artifactId>mm</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(myProjectPom, m, mm)

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
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(myProjectPom, m)

    projectsManager.waitForPluginResolution()

    projectsManager.projectsTree.ignoredFilesPaths = listOf(m.getPath())

    myNavigator!!.showIgnored = true
    assertTrue(rootNodes[0].isVisible())
    val childNodeNamesBefore = rootNodes[0].children.map { it.name }.toSet()
    assertEquals(setOf("Lifecycle", "Plugins", "m"), childNodeNamesBefore)

    myNavigator!!.showIgnored = false
    assertTrue(rootNodes[0].isVisible())
    waitForPluginNodesUpdated()
    val childNodeNamesAfter = rootNodes[0].children.map { it.name }.toSet()
    assertEquals(setOf("Lifecycle", "Plugins"), childNodeNamesAfter)
  }

  private suspend fun waitForPluginNodesUpdated() = withContext(Dispatchers.EDT) {
    val pluginsNode = rootNodes[0].pluginsNode
    PlatformTestUtil.waitWithEventsDispatching({ "Waiting for Plugins to be updated" },
                                               { !pluginsNode.pluginNodes.isEmpty() }, 10)
  }

  @Test
  fun testIgnoringParentProjectWhenNeedNoReconnectModule() = runBlocking {
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(myProjectPom, m)

    projectsTree.ignoredFilesPaths = listOf(myProjectPom.getPath())

    myNavigator!!.showIgnored = true
    assertEquals(1, rootNodes.size)
    assertEquals(1, myStructure!!.rootElement.getChildren().size)
    var projectNode = myStructure!!.rootElement.getChildren()[0] as ProjectNode
    assertEquals(myProjectPom, projectNode.virtualFile)
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
    projectsManager.fireActivatedInTests()

    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    val m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(m1, m2)

    assertEquals(2, rootNodes.size)
    assertEquals(m1, rootNodes[0].virtualFile)
    assertEquals(m2, rootNodes[1].virtualFile)

    createModulePom("m2", """
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
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(myProjectPom, m)

    assertEquals(1, rootNodes.size)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)

    val newState = MavenProjectsNavigatorState()
    newState.groupStructurally = false
    myNavigator!!.loadState(newState)

    assertEquals(2, rootNodes.size)
  }

  @Test
  fun testNavigatableForProjectNode() = runBlocking {
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    readFiles(myProjectPom)
    withContext(Dispatchers.EDT) {
      assertTrue(rootNodes[0].navigatable!!.canNavigateToSource())
    }
  }

  @Test
  fun testCanIterateOverRootNodeChildren() = runBlocking {
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    readFiles(myProjectPom)

    val rootNode = myStructure!!.rootElement
    val projectsManager = MavenProjectsManager.getInstance(myProject)
    val project = projectsManager.getProjects()[0]
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
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    readFiles(myProjectPom)

    val projectsManager = MavenProjectsManager.getInstance(myProject)
    val project = projectsManager.getProjects()[0]
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
    projectsManager.fireActivatedInTests()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())
    readFiles(myProjectPom, m)

    assertEquals(1, rootNodes.size)
    assertEquals(1, rootNodes[0].projectNodesInTests.size)

    val runManager = getInstanceImpl(myProject)
    val mavenTemplateConfiguration = MavenRunConfigurationType.getInstance().configurationFactories[0].createTemplateConfiguration(
      myProject)
    val mavenConfiguration = MavenRunConfigurationType.getInstance().configurationFactories[0].createConfiguration("myConfiguration",
                                                                                                                   mavenTemplateConfiguration)
    val configuration = RunnerAndConfigurationSettingsImpl(runManager, mavenConfiguration)
    runManager.addConfiguration(configuration)
    runManager.removeConfiguration(configuration)
  }

  private suspend fun readFiles(vararg files: VirtualFile) {
    if (isNewImportingProcess) {
      val flow = MavenImportFlow()
      val allFiles: MutableList<VirtualFile> = ArrayList(projectsManager.getProjectsFiles())
      allFiles.addAll(listOf(*files))
      val initialImportContext =
        flow.prepareNewImport(myProject,
                              FilesList(allFiles),
                              mavenGeneralSettings,
                              mavenImporterSettings,
                              emptyList(), emptyList())


      ApplicationManager.getApplication().executeOnPooledThread {
        val readContext = flow.readMavenFiles(initialImportContext, mavenProgressIndicator)
        flow.updateProjectManager(readContext)
        myNavigator!!.scheduleStructureUpdate()
      }[10, TimeUnit.SECONDS]

    }
    else {
      projectsManager.addManagedFilesWithProfilesAndUpdate(listOf(*files), MavenExplicitProfiles.NONE, null, null)
    }
  }

  private val rootNodes: List<ProjectNode>
    get() = myStructure!!.rootElement.projectNodesInTests
}
