// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXmlFully
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.runWithoutStaticSync
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.function.Function

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MiscImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  private val myEventsTestHelper = MavenEventsTestHelper()

  @BeforeEach
  fun setUp() {
    myEventsTestHelper.setUp(maven.project)
  }

  @AfterEach
  fun tearDown() {
    myEventsTestHelper.tearDown()
  }

  @Test
  fun testRestarting() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <name>1</name>
                    """.trimIndent())
    maven.assertModules("project")
    assertEquals("1", maven.projectsTree.rootProjects[0].name)
    MavenServerManager.getInstance().closeAllConnectorsAndWait()
    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <name>2</name>
                    """.trimIndent())
    maven.updateAllProjects()
    maven.assertModules("project")
    assertEquals("2", maven.projectsTree.rootProjects[0].name)
  }

  //@Test
  //fun testFallbackToSlowWorkspaceCommit() = runBlocking {
  //  try {
  //    WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE = true
  //    importProjectAsync("""
  //                    <groupId>test</groupId>
  //                    <artifactId>project</artifactId>
  //                    <version>1</version>
  //                    <name>1</name>
  //                    """.trimIndent())
  //    assertModules("project")
  //
  //    // make sure the logic in WorkspaceProjectImporter worked as expected
  //    assertFalse(WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE)
  //  }
  //  finally {
  //    WORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE = false
  //  }
  //}

  @Test

  fun testDoNotFailOnInvalidMirrors() = runBlocking {
    maven.updateSettingsXmlFully("""
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
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertModules("project")
  }

  @Test
  fun testImportingFiresRootChangesOnlyOnce() = runBlocking {
    maven.runWithoutStaticSync()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    myEventsTestHelper.assertRootsChanged(1)
    myEventsTestHelper.assertWorkspaceModelChanges(1)
  }

  @Test
  fun testDoRootChangesOnProjectReimportWhenNothingChanges() = runBlocking {
    maven.runWithoutStaticSync()
    maven.importProjectAsync("""
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
    maven.importProjectAsync("""
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
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                      <module>m2</module>
                    </modules>
                    """.trimIndent())
    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())
    maven.createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    maven.updateAllProjects()
    maven.updateModulePom("m1",
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
    maven.project.getMessageBus().connect().subscribe(
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
    maven.updateAllProjects()
    assertEquals(setOf("modified m1", "created Maven: junit:junit:4.0", "created LibraryPropertiesEntityImpl", "created LibraryMavenCoordinateEntityImpl"), changeLog)
  }

  @Test
  fun testResolvingFiresRootChangesOnlyOnce() = runBlocking {
    maven.runWithoutStaticSync()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    myEventsTestHelper.assertRootsChanged(1)
    myEventsTestHelper.assertWorkspaceModelChanges(1)
  }

  @Test
  fun testDoNotRecreateModulesBeforeResolution() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    val m = maven.getModule("project")
    assertSame(m, maven.getModule("project"))
  }

  @Test
  fun testMavenExtensionsAreLoadedAndAfterProjectsReadIsCalled() = runBlocking {
    try {
      val helper = MavenCustomRepositoryHelper(maven.dir, "plugins")
      maven.repositoryPath = helper.getTestData("plugins")
      maven.mavenGeneralSettings.isWorkOffline = true
      maven.importProjectAsync("""
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
      val projects = maven.projectsTree.projects
      assertEquals(1, projects.size)
      val mavenProject = projects[0]
      assertEquals("Name for test:project generated by MyMavenExtension.", mavenProject.finalName)
      PlatformTestUtil.assertPathsEqual(maven.projectPom.getPath(), mavenProject.properties.getProperty("workspace-info"))
    }
    finally {
      MavenServerManager.getInstance().closeAllConnectorsAndWait() // to unlock files
    }
  }

  @Test
  fun testMultiModuleWithInferredModelVersionFromNamespace() = runBlocking {
    maven.assumeMaven4()
    // with no explicit modelVersion tag
    maven.createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
        <packaging>pom</packaging>
        <modules>
          <module>module-a</module>
          <module>module-b</module>
        </modules>
      """.trimIndent(),
      omitModelVersionTag = true
    )

    // module-b: no dependencies, also without explicit modelVersion
    maven.createModulePom(
      "module-b",
      """
        <parent>
          <groupId>test</groupId>
          <artifactId>parent</artifactId>
          <version>1.0</version>
        </parent>
        <artifactId>module-b</artifactId>
      """.trimIndent(),
      omitModelVersionTag = true
    )

    // module-a: depends on module-b (inter-module dependency), without explicit modelVersion
    maven.createModulePom(
      "module-a",
      """
        <parent>
          <groupId>test</groupId>
          <artifactId>parent</artifactId>
          <version>1.0</version>
        </parent>
        <artifactId>module-a</artifactId>
        <dependencies>
          <dependency>
            <groupId>test</groupId>
            <artifactId>module-b</artifactId>
            <version>1.0</version>
          </dependency>
        </dependencies>
      """.trimIndent(),
      omitModelVersionTag = true
    )

    maven.importProjectAsync()

    // All modules should be recognized
    maven.assertModules("parent", "module-a", "module-b")

    // Inter-module dependency should be resolved locally (not as external artifact)
    maven.assertModuleModuleDeps("module-a", "module-b")

    // Verify parent-child relationships
    val parentProject = maven.projectsManager.findProject(maven.projectPom)
    assertNotNull(parentProject, "Parent project should not be null")

    val moduleAProject = maven.projectsManager.projectsTree.findProject(maven.projectRoot.findFileByRelativePath("module-a/pom.xml")!!)
    val moduleBProject = maven.projectsManager.projectsTree.findProject(maven.projectRoot.findFileByRelativePath("module-b/pom.xml")!!)
    assertNotNull(moduleAProject, "module-a project should not be null")
    assertNotNull(moduleBProject, "module-b project should not be null")

    // Both modules should be children of the parent
    val children = maven.projectsManager.projectsTree.getModules(parentProject!!)
    assertSameElements("Parent should have two children", children.map { it.mavenId.artifactId }, listOf("module-a", "module-b"))
  }
}
