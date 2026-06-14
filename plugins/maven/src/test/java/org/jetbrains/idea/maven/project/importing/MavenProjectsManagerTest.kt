// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.ide.DataManager
import com.intellij.ide.actions.DeleteAction
import com.intellij.ide.projectView.ProjectView
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.configConfirmationForYesAnswer
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.createStdProjectFolders
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.FileContentUtil
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.actions.MavenModuleDeleteProvider
import org.jetbrains.idea.maven.project.actions.RemoveManagedFilesAction
import org.jetbrains.idea.maven.project.projectRoot.MavenModuleStructureExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsManagerTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  
  @BeforeEach
  fun setUp() {
    maven.initProjectsManager(false)
  }

  @Test
  fun testShouldReturnNullForUnprocessedFiles() = runBlocking {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    // shouldn't throw
    assertNull(maven.projectsManager.findProject(maven.projectPom))
  }

  @Test
  fun testShouldReturnNotNullForProcessedFiles() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    maven.importProjectAsync()

    // shouldn't throw
    assertNotNull(maven.projectsManager.findProject(maven.projectPom))
  }

  @Test
  fun testAddingAndRemovingManagedFiles() = runBlocking {
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.importProjectAsync(m1)
    assertUnorderedElementsAreEqual(maven.projectsTree.rootProjectsFiles, m1)
    maven.waitForImportWithinTimeout {
      maven.projectsManager.addManagedFiles(listOf(m2))
    }
    assertUnorderedElementsAreEqual(maven.projectsTree.rootProjectsFiles, m1, m2)
    maven.waitForImportWithinTimeout {
      maven.projectsManager.removeManagedFiles(listOf(m2))
    }
    assertUnorderedElementsAreEqual(maven.projectsTree.rootProjectsFiles, m1)
  }

  @Test
  fun testAddingAndRemovingManagedFilesAddsAndRemovesModules() = runBlocking {
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                             <groupId>test</groupId>
                                             <artifactId>m2</artifactId>
                                             <version>1</version>
                                             """.trimIndent())
    maven.importProjectAsync(m1)
    maven.assertModules("m1")
    maven.waitForImportWithinTimeout {
      maven.projectsManager.addManagedFiles(listOf(m2))
    }
    maven.assertModules("m1", "m2")

    maven.waitForImportWithinTimeout {
      maven.projectsManager.removeManagedFiles(listOf(m2))
    }
    maven.assertModules("m1")
  }

  @Test
  fun testDoNotScheduleResolveOfInvalidProjectsDeleted() = runBlocking {
    val called = BooleanArray(1)
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, object : MavenProjectsTree.Listener {
      override fun projectsResolved(projects: List<MavenProject>) {
        called[0] = true
      }
    })
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1
                       """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project")
    assertFalse(called[0]) // on import
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>2
                       """.trimIndent())
    assertFalse(called[0]) // on update
  }

  @Test
  fun testUpdatingFoldersAfterFoldersResolving() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("src1", "src2", "test1", "test2", "res1", "res2", "testres1", "testres2")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>someId1</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/src1</source>
                                  <source>${'$'}{basedir}/src2</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>someId2</id>
                              <phase>generate-resources</phase>
                              <goals>
                                <goal>add-resource</goal>
                              </goals>
                              <configuration>
                                <resources>
                                  <resource>
                                     <directory>${'$'}{basedir}/res1</directory>
                                  </resource>
                                  <resource>
                                     <directory>${'$'}{basedir}/res2</directory>
                                  </resource>
                                </resources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>someId3</id>
                              <phase>generate-test-sources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/test1</source>
                                  <source>${'$'}{basedir}/test2</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>someId4</id>
                              <phase>generate-test-resources</phase>
                              <goals>
                                <goal>add-test-resource</goal>
                              </goals>
                              <configuration>
                                <resources>
                                  <resource>
                                     <directory>${'$'}{basedir}/testres1</directory>
                                  </resource>
                                  <resource>
                                     <directory>${'$'}{basedir}/testres2</directory>
                                  </resource>
                                </resources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    maven.assertSources("project", "src/main/java", "src1", "src2")
    maven.assertDefaultResources("project", "res1", "res2")
    maven.assertTestSources("project", "src/test/java", "test1", "test2")
    maven.assertDefaultTestResources("project", "testres1", "testres2")
  }

  @Test
  fun testForceReimport() = runBlocking {
    maven.createProjectSubDir("src/main/java")
    maven.createProjectPom("""
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
    maven.importProjectAsync()
    maven.assertModules("project")
    maven.assertSources("project", "src/main/java")
    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")
    edtWriteAction {
      val model = ModuleRootManager.getInstance(maven.getModule("project")).getModifiableModel()
      val contentRoot = model.getContentEntries()[0]
      for (eachSourceFolders in contentRoot.getSourceFolders()) {
        contentRoot.removeSourceFolder(eachSourceFolders!!)
      }
      for (each in model.getOrderEntries()) {
        if (each is LibraryOrderEntry && MavenRootModelAdapter.isMavenLibrary(each.getLibrary())) {
          model.removeOrderEntry(each)
        }
      }
      model.commit()
    }
    maven.assertSources("project")
    maven.assertModuleLibDeps("project")
    maven.waitForImportWithinTimeout {
      maven.projectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
    }
    maven.assertSources("project", "src/main/java")
    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testNotIgnoringProjectsForDeletedInBackgroundModules() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.importProjectAsync()
    val module = maven.getModule("m")
    assertNotNull(module)
    assertFalse(maven.projectsManager.isIgnored(maven.projectsManager.findProject(m)!!))
    edtWriteAction {
      ModuleManager.getInstance(maven.project).disposeModule(module)
    }
    assertNull(ModuleManager.getInstance(maven.project).findModuleByName("m"))
    assertFalse(maven.projectsManager.isIgnored(maven.projectsManager.findProject(m)!!))
  }

  @Test
  fun testIgnoringProjectsForRemovedInUiModules() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.importProjectAsync()
    val module = maven.getModule("m")
    assertNotNull(module)
    assertFalse(maven.projectsManager.isIgnored(maven.projectsManager.findProject(m)!!))
    val moduleManager = ModuleManager.getInstance(maven.project)
    val moduleModel = moduleManager.getModifiableModel()
    ModuleDeleteProvider.removeModule(module, listOf(), moduleModel)
    val moduleStructureExtension = MavenModuleStructureExtension()
    moduleStructureExtension.moduleRemoved(module)
    moduleStructureExtension.apply()
    moduleStructureExtension.disposeUIResources()
    maven.updateAllProjects()
    assertNull(ModuleManager.getInstance(maven.project).findModuleByName("m"))
    assertTrue(maven.projectsManager.isIgnored(maven.projectsManager.findProject(m)!!))
  }

  @Test
  fun testIgnoringProjectsForDetachedInUiModules() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.importProjectAsync()
    val module = maven.getModule("m")
    assertNotNull(module)
    assertFalse(maven.projectsManager.isIgnored(maven.projectsManager.findProject(m)!!))
    edtWriteAction {
      ModuleDeleteProvider.detachModules(maven.project, arrayOf(module))
    }
    assertNull(ModuleManager.getInstance(maven.project).findModuleByName("m"))
    assertTrue(maven.projectsManager.isIgnored(maven.projectsManager.findProject(m)!!))
  }

  @Test
  fun testWhenDeleteModuleThenChangeModuleDependencyToLibraryDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                           <dependency>
                                               <groupId>test</groupId>
                                               <artifactId>m1</artifactId>
                                               <version>1</version>
                                           </dependency>
                                       </dependencies>
                                       """.trimIndent())
    maven.importProjectAsync()
    maven.assertModuleModuleDeps("m2", "m1")
    val module1 = maven.getModule("m1")
    maven.configConfirmationForYesAnswer()
    val action = DeleteAction()
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        action.actionPerformed(TestActionEvent.createTestEvent(action, createTestModuleDataContext(module1)))
      }
    }
    maven.updateAllProjects()
    maven.assertModuleModuleDeps("m2")
    maven.assertModuleLibDep("m2", "Maven: test:m1:1")
  }

  @Test
  fun testWhenDeleteModuleInProjectStructureThenChangeModuleDependencyToLibraryDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                           <dependency>
                                               <groupId>test</groupId>
                                               <artifactId>m1</artifactId>
                                               <version>1</version>
                                           </dependency>
                                       </dependencies>
                                       """.trimIndent())
    maven.importProjectAsync()
    maven.assertModuleModuleDeps("m2", "m1")
    val module1 = maven.getModule("m1")
    val module2 = maven.getModule("m2")
    val moduleManager = ModuleManager.getInstance(maven.project)
    val moduleModel = moduleManager.getModifiableModel()
    val otherModuleRootModels = java.util.List.of(ModuleRootManager.getInstance(module2).getModifiableModel())
    ModuleDeleteProvider.removeModule(module1, otherModuleRootModels, moduleModel)
    val moduleStructureExtension = MavenModuleStructureExtension()
    moduleStructureExtension.moduleRemoved(module1)
    moduleStructureExtension.apply()
    moduleStructureExtension.disposeUIResources()
    maven.updateAllProjects()
    maven.assertModuleModuleDeps("m2")
    maven.assertModuleLibDep("m2", "Maven: test:m1:1")
  }

  @Test
  fun testDoNotIgnoreProjectWhenModuleDeletedDuringImport() = runBlocking {
    maven.assumeModel_4_0_0("IDEA-379195")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project", "m")
    UsefulTestCase.assertSize(1, maven.projectsManager.getRootProjects())
    UsefulTestCase.assertEmpty(maven.projectsManager.getIgnoredFilesPaths())

    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    """.trimIndent())
    maven.updateAllProjects()
    maven.assertModules("project")
    UsefulTestCase.assertSize(1, maven.projectsManager.getRootProjects())
    UsefulTestCase.assertEmpty(maven.projectsManager.getIgnoredFilesPaths())
  }

  @Test
  fun testDoNotIgnoreProjectWhenSeparateMainAndTestModulesDeletedDuringImport() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <maven.compiler.release>8</maven.compiler.release>
                      <maven.compiler.testRelease>11</maven.compiler.testRelease>
                    </properties>
                     <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.10.0</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
    )
    maven.assertModules("project", "project.main", "project.test")
    UsefulTestCase.assertSize(1, maven.projectsManager.getRootProjects())
    UsefulTestCase.assertEmpty(maven.projectsManager.getIgnoredFilesPaths())
    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.updateAllProjects()
    maven.assertModules("project")
    UsefulTestCase.assertSize(1, maven.projectsManager.getRootProjects())
    UsefulTestCase.assertEmpty(maven.projectsManager.getIgnoredFilesPaths())
  }

  @Test
  fun testDoNotRemoveMavenProjectsOnReparse() = runBlocking {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    val log = StringBuilder()
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, object : MavenProjectsTree.Listener {
      override fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
        for (each in updated) {
          log.append("updated: ").append(each.first.displayName).append(" ")
        }
        for (each in deleted) {
          log.append("deleted: ").append(each.displayName).append(" ")
        }
      }
    })
    withContext(Dispatchers.EDT) {
      FileContentUtil.reparseFiles(maven.project, maven.projectsManager.getProjectsFiles(), true)
    }
    assertTrue(log.length == 0, log.toString())
  }

  @Test
  fun testShouldRemoveMavenProjectsAndNotAddThemToIgnore() = runBlocking {
    val mavenParentPom = maven.createProjectSubFile("maven-parent/pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>test</groupId>
          <artifactId>parent-maven</artifactId>
          <packaging>pom</packaging>
          <version>1.0-SNAPSHOT</version>
          <modules>
              <module>child1</module>
          </modules>
      </project>""".trimIndent())

    val child1Pom = maven.createProjectSubFile("maven-parent/child1/pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <parent>
              <artifactId>parent-maven</artifactId>
              <groupId>test</groupId>
              <version>1.0-SNAPSHOT</version>
          </parent>
          <modelVersion>4.0.0</modelVersion>    
          <artifactId>child1</artifactId>
      </project>
      """.trimIndent())
    maven.refreshFiles(listOf(mavenParentPom, child1Pom))
    edtWriteAction { ModuleManager.getInstance(maven.project).newModule("non-maven", JAVA_MODULE_ENTITY_TYPE_ID_NAME) }
    maven.importProjectAsync(mavenParentPom)
    assertEquals(3, ModuleManager.getInstance(maven.project).modules.size)
    maven.configConfirmationForYesAnswer()
    val action = RemoveManagedFilesAction()
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          action.actionPerformed(TestActionEvent.createTestEvent(action, createTestDataContext(mavenParentPom)))
        }
      }
    }
    assertEquals(1, ModuleManager.getInstance(maven.project).modules.size)
    assertEquals("non-maven", ModuleManager.getInstance(maven.project).modules[0].getName())
    UsefulTestCase.assertEmpty(maven.projectsManager.getIgnoredFilesPaths())

    //should then import project in non-ignored state again
    maven.importProjectAsync(mavenParentPom)
    assertEquals(3, ModuleManager.getInstance(maven.project).modules.size)
    UsefulTestCase.assertEmpty(maven.projectsManager.ignoredFilesPaths)
  }

  @Test
  fun testSameArtifactIdDifferentTypeDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                         <module>m3</module>
                       </modules>
                       """.trimIndent())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                           <dependency>
                                               <groupId>test</groupId>
                                               <artifactId>m1</artifactId>
                                               <version>1</version>
                                           </dependency>
                                       </dependencies>
                                       """.trimIndent())
    val m3 = maven.createModulePom("m3",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m3</artifactId>
                                       <version>1</version>
                                       <dependencies>
                                           <dependency>
                                               <groupId>test</groupId>
                                               <artifactId>m1</artifactId>
                                               <version>1</version>
                                               <type>ejb</type>
                                           </dependency>
                                       </dependencies>
                                       """.trimIndent())
    maven.importProjectAsync()
    maven.assertModuleModuleDeps("m2", "m1")
    maven.assertModuleModuleDeps("m3", "m1")
    val mavenProject2 = maven.projectsManager.findProject(m2)
    val m21dep = mavenProject2!!.findDependencies(MavenId("test:m1:1"))[0]
    assertEquals("jar", m21dep.type)
    val mavenProject3 = maven.projectsManager.findProject(m3)
    val m31dep = mavenProject3!!.findDependencies(MavenId("test:m1:1"))[0]
    assertEquals("ejb", m31dep.type)
  }

  @Test
  fun shouldUnsetMavenizedIfManagedFilesWasRemoved() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertModules("project")
    UsefulTestCase.assertSize(1, maven.projectsManager.getRootProjects())
    maven.waitForImportWithinTimeout {
      maven.projectsManager.removeManagedFiles(listOf(maven.projectPom))
    }
    UsefulTestCase.assertSize(0, maven.projectsManager.getRootProjects())
  }

  @Test
  fun testShouldKeepModuleName() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertEquals("project", ModuleManager.getInstance(maven.project).modules[0].getName())
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project1</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertEquals("project", ModuleManager.getInstance(maven.project).modules[0].getName())
  }

  @Test
  fun testModuleNameTemplateArtifactId() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>artifactId</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertEquals("artifactId", ModuleManager.getInstance(maven.project).modules[0].getName())
  }

  @Test
  fun testModuleNameTemplateGroupIdArtifactId() = runBlocking {
    Registry.get("maven.import.module.name.template").setValue("groupId.artifactId", maven.testRootDisposable)
    maven.importProjectAsync("""
                    <groupId>myGroup</groupId>
                    <artifactId>artifactId</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertEquals("myGroup.artifactId", ModuleManager.getInstance(maven.project).modules[0].getName())
  }

  @Test
  fun testModuleNameTemplateFolderName() = runBlocking {
    Registry.get("maven.import.module.name.template").setValue("folderName", maven.testRootDisposable)
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>ignoredArtifactId</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertNotSame("ignoredArtifactId", maven.projectRoot.getName())
    assertEquals(maven.projectRoot.getName(), ModuleManager.getInstance(maven.project).modules[0].getName())
  }

  private fun createTestDataContext(pomFile: VirtualFile): DataContext {
    val defaultContext = DataManager.getInstance().getDataContext()
    return CustomizedDataContext.withSnapshot(defaultContext) { sink ->
      sink[CommonDataKeys.PROJECT] = maven.project
      sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = arrayOf(pomFile)
    }
  }

  private fun createTestModuleDataContext(vararg modules: Module): DataContext {
    val defaultContext = DataManager.getInstance().getDataContext()
    return CustomizedDataContext.withSnapshot(defaultContext) { sink ->
      sink[CommonDataKeys.PROJECT] = maven.project
      sink[LangDataKeys.MODULE_CONTEXT_ARRAY] = modules
      sink[ProjectView.UNLOADED_MODULES_CONTEXT_KEY] = listOf() // UnloadedModuleDescription
      sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = MavenModuleDeleteProvider()
    }
  }
}
