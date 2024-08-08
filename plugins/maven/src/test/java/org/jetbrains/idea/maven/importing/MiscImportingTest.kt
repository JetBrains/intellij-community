// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.importing.workspaceModel.WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.Test
import java.io.File
import java.util.*
import java.util.function.Function

class MiscImportingTest : MavenMultiVersionImportingTestCase() {
  private val myEventsTestHelper = MavenEventsTestHelper()

  override fun setUp() {
    super.setUp()
    myEventsTestHelper.setUp(project)
  }

  override fun tearDown() {
    try {
      myEventsTestHelper.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun testRestarting() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <name>1</name>
                    """.trimIndent())
    assertModules("project")
    assertEquals("1", projectsTree.rootProjects[0].name)
    MavenServerManager.getInstance().closeAllConnectorsAndWait()
    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <name>2</name>
                    """.trimIndent())
    updateAllProjects()
    assertModules("project")
    assertEquals("2", projectsTree.rootProjects[0].name)
  }

  @Test
  fun testFallbackToSlowWorkspaceCommit() = runBlocking {
    try {
      WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE = true
      importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <name>1</name>
                      """.trimIndent())
      assertModules("project")

      // make sure the logic in WorkspaceProjectImporter worked as expected
      assertFalse(WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE)
    }
    finally {
      WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE = false
    }
  }

  @Test

  fun testDoNotFailOnInvalidMirrors() = runBlocking {
    updateSettingsXmlFully("""
                             <settings>
                             <mirrors>
                               <mirror>
                               </mirror>
                               <mirror>
                                 <id/>
                                 <url/>
                                 <mirrorOf/>
                               </mirror>
                               <mirror>
                                 <id/>
                                 <url>foo</url>
                                 <mirrorOf>*</mirrorOf>
                               </mirror>
                               <mirror>
                                 <id>foo</id>
                                 <url/>
                                 <mirrorOf>*</mirrorOf>
                               </mirror>
                             </mirrors>
                             </settings>
                             """.trimIndent())
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertModules("project")
  }

  @Test
  fun testImportingAllAvailableFilesIfNotInitialized() = runBlocking {
    createModule("m1")
    createModule("m2")
    createProjectSubDirs("m1/src/main/java",
                         "m2/src/main/java")
    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())
    createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    assertSources("m1")
    assertSources("m2")
    assertFalse(projectsManager.isMavenizedProject)
    waitForImportWithinTimeout {
      projectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
    }
    assertSources("m1", "src/main/java")
    assertSources("m2", "src/main/java")
  }

  @Test
  fun testImportingFiresRootChangesOnlyOnce() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    myEventsTestHelper.assertRootsChanged(1)
    myEventsTestHelper.assertWorkspaceModelChanges(1)
  }

  @Test
  fun testDoRootChangesOnProjectReimportWhenNothingChanges() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())
    myEventsTestHelper.assertRootsChanged(1)
    myEventsTestHelper.assertWorkspaceModelChanges(1)
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())
    myEventsTestHelper.assertRootsChanged(0)
    myEventsTestHelper.assertWorkspaceModelChanges(0)
  }

  @Test
  fun testSendWorkspaceEventsOnlyForChangedEntities() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                      <module>m2</module>
                    </modules>
                    """.trimIndent())
    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())
    createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    updateAllProjects()
    updateModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <dependencies>
                        <dependency>
                          <groupId>junit</groupId>
                          <artifactId>junit</artifactId>
                          <version>4.0</version>
                        </dependency>
                      </dependencies>
                      """.trimIndent())
    val changeLog = HashSet<String>()
    project.getMessageBus().connect().subscribe(
      WorkspaceModelTopics.CHANGED,
      object : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
          val iterator = (event as VersionedStorageChangeInternal).getAllChanges().iterator()
          val getName: Function<WorkspaceEntity?, String> = Function { entity ->
            if (entity is WorkspaceEntityWithSymbolicId) {
              entity.symbolicId.presentableName
            }
            else {
              entity!!.javaClass.getSimpleName()
            }
          }
          while (iterator.hasNext()) {
            val change = iterator.next()
            if (change.newEntity == null) {
              changeLog.add(
                "deleted " + getName.apply(change.oldEntity))
            }
            else if (change.oldEntity == null) {
              changeLog.add(
                "created " + getName.apply(change.newEntity))
            }
            else {
              changeLog.add(
                "modified " + getName.apply(change.newEntity))
            }
          }
        }
      })
    updateAllProjects()
    assertEquals(setOf("modified m1", "created Maven: junit:junit:4.0", "created LibraryPropertiesEntityImpl"), changeLog)
  }

  @Test
  fun testResolvingFiresRootChangesOnlyOnce() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    myEventsTestHelper.assertRootsChanged(1)
    myEventsTestHelper.assertWorkspaceModelChanges(1)
  }

  @Test
  fun testDoNotRecreateModulesBeforeResolution() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    val m = getModule("project")
    assertSame(m, getModule("project"))
  }

  @Test
  fun testTakingProxySettingsIntoAccount() = runBlocking {
    needFixForMaven4()
    val helper = MavenCustomRepositoryHelper(dir, "local1")
    repositoryPath = helper.getTestDataPath("local1")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())
    removeFromLocalRepository("junit")
    updateAllProjects()
    val jarFile = File(repositoryFile, "junit/junit/4.0/junit-4.0.jar")
    assertTrue(jarFile.exists())
    projectsManager.listenForExternalChanges()
    waitForImportWithinTimeout {
      updateSettingsXml("""
                        <proxies>
                         <proxy>
                            <id>my</id>
                            <active>true</active>
                            <protocol>http</protocol>
                            <host>invalid.host.in.intellij.net</host>
                            <port>3128</port>
                          </proxy>
                        </proxies>
                        """.trimIndent())
    }
    removeFromLocalRepository("junit")
    assertFalse(jarFile.exists())
    try {
      updateAllProjects()
    }
    finally {
      // LightweightHttpWagon does not clear settings if they were not set before a proxy was configured.
      System.clearProperty("http.proxyHost")
      System.clearProperty("http.proxyPort")
    }
    assertFalse(jarFile.exists())
    restoreSettingsFile()
    updateAllProjects()
    assertTrue(jarFile.exists())
  }

  @Test
  fun testMavenExtensionsAreLoadedAndAfterProjectsReadIsCalled() = runBlocking {
    try {
      val helper = MavenCustomRepositoryHelper(dir, "plugins")
      repositoryPath = helper.getTestDataPath("plugins")
      mavenGeneralSettings.isWorkOffline = true
      importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <build>
                        <extensions>
                          <extension>
                            <groupId>intellij.test</groupId>
                            <artifactId>maven-extension</artifactId>
                            <version>1.0</version>
                          </extension>
                        </extensions>
                      </build>
                      """.trimIndent())
      val projects = projectsTree.projects
      assertEquals(1, projects.size)
      val mavenProject = projects[0]
      assertEquals("Name for test:project generated by MyMavenExtension.", mavenProject.finalName)
      PlatformTestUtil.assertPathsEqual(projectPom.getPath(), mavenProject.properties.getProperty("workspace-info"))
    }
    finally {
      MavenServerManager.getInstance().closeAllConnectorsAndWait() // to unlock files
    }
  }

  @Test
  fun testUserPropertiesCanBeCustomizedByMavenImporters() = runBlocking {
    val disposable: Disposable = Disposer.newDisposable()
    try {
      maskExtensions(MavenImporter.EXTENSION_POINT_NAME,
                     listOf<MavenImporter>(NameSettingMavenImporter("name-from-properties")),
                     disposable)
      importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <name>${'$'}{myName}</name>
                      """.trimIndent())
    }
    finally {
      Disposer.dispose(disposable)
    }

    val project = projectsManager.findProject(MavenId("test", "project", "1"))
    assertNotNull(project)
    assertEquals("name-from-properties", project!!.name)
  }

  private class NameSettingMavenImporter(private val myName: String) : MavenImporter("gid", "id") {
    override fun customizeUserProperties(project: Project, mavenProject: MavenProject, properties: Properties) {
      properties.setProperty("myName", myName)
    }

    override fun isApplicable(mavenProject: MavenProject): Boolean {
      return true
    }
  }
}
