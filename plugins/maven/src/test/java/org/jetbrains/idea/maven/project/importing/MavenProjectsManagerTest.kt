// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.ide.DataManager
import com.intellij.ide.actions.DeleteAction
import com.intellij.ide.projectView.ProjectView
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.FileContentUtil
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.actions.MavenModuleDeleteProvider
import org.jetbrains.idea.maven.project.actions.RemoveManagedFilesAction
import org.jetbrains.idea.maven.project.projectRoot.MavenModuleStructureExtension
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.junit.Test

class MavenProjectsManagerTest : MavenMultiVersionImportingTestCase() {
  
  override fun setUp() {
    super.setUp()
    initProjectsManager(false)
  }

  @Test
  fun testShouldReturnNullForUnprocessedFiles() = runBlocking {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    // shouldn't throw
    assertNull(projectsManager.findProject(projectPom))
  }

  @Test
  fun testShouldReturnNotNullForProcessedFiles() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    importProjectAsync()

    // shouldn't throw
    assertNotNull(projectsManager.findProject(projectPom))
  }

  @Test
  fun testAddingAndRemovingManagedFiles() = runBlocking {
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    importProjectAsync(m1)
    assertUnorderedElementsAreEqual(projectsTree.rootProjectsFiles, m1)
    waitForImportWithinTimeout {
      projectsManager.addManagedFiles(listOf(m2))
    }
    assertUnorderedElementsAreEqual(projectsTree.rootProjectsFiles, m1, m2)
    waitForImportWithinTimeout {
      projectsManager.removeManagedFiles(listOf(m2))
    }
    assertUnorderedElementsAreEqual(projectsTree.rootProjectsFiles, m1)
  }

  @Test
  fun testAddingAndRemovingManagedFilesAddsAndRemovesModules() = runBlocking {
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                             <groupId>test</groupId>
                                             <artifactId>m2</artifactId>
                                             <version>1</version>
                                             """.trimIndent())
    importProjectAsync(m1)
    assertModules("m1")
    waitForImportWithinTimeout {
      projectsManager.addManagedFiles(listOf(m2))
    }
    assertModules("m1", "m2")

    waitForImportWithinTimeout {
      projectsManager.removeManagedFiles(listOf(m2))
    }
    assertModules("m1")
  }

  @Test
  fun testDoNotScheduleResolveOfInvalidProjectsDeleted() = runBlocking {
    val called = BooleanArray(1)
    projectsManager.addProjectsTreeListener(object : MavenProjectsTree.Listener {
      override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>,
                                   nativeMavenProject: NativeMavenProjectHolder?) {
        called[0] = true
      }
    })
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1
                       """.trimIndent())
    importProjectAsync()
    assertModules("project")
    assertFalse(called[0]) // on import
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>2
                       """.trimIndent())
    assertFalse(called[0]) // on update
  }

  @Test
  fun testUpdatingFoldersAfterFoldersResolving() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("src1", "src2", "test1", "test2", "res1", "res2", "testres1", "testres2")
    importProjectAsync("""
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
    assertSources("project", "src/main/java", "src1", "src2")
    assertDefaultResources("project", "res1", "res2")
    assertTestSources("project", "src/test/java", "test1", "test2")
    assertDefaultTestResources("project", "testres1", "testres2")
  }

  @Test
  fun testForceReimport() = runBlocking {
    createProjectSubDir("src/main/java")
    createProjectPom("""
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
    importProjectAsync()
    assertModules("project")
    assertSources("project", "src/main/java")
    assertModuleLibDeps("project", "Maven: junit:junit:4.0")
    writeAction {
      val model = ModuleRootManager.getInstance(getModule("project")).getModifiableModel()
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
    assertSources("project")
    assertModuleLibDeps("project")
    waitForImportWithinTimeout {
      projectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
    }
    assertSources("project", "src/main/java")
    assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testNotIgnoringProjectsForDeletedInBackgroundModules() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    importProjectAsync()
    val module = getModule("m")
    assertNotNull(module)
    assertFalse(projectsManager.isIgnored(projectsManager.findProject(m)!!))
    writeAction {
      ModuleManager.getInstance(project).disposeModule(module)
    }
    assertNull(ModuleManager.getInstance(project).findModuleByName("m"))
    assertFalse(projectsManager.isIgnored(projectsManager.findProject(m)!!))
  }

  @Test
  fun testIgnoringProjectsForRemovedInUiModules() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    importProjectAsync()
    val module = getModule("m")
    assertNotNull(module)
    assertFalse(projectsManager.isIgnored(projectsManager.findProject(m)!!))
    val moduleManager = ModuleManager.getInstance(project)
    val moduleModel = moduleManager.getModifiableModel()
    ModuleDeleteProvider.removeModule(module, listOf(), moduleModel)
    val moduleStructureExtension = MavenModuleStructureExtension()
    moduleStructureExtension.moduleRemoved(module)
    moduleStructureExtension.apply()
    moduleStructureExtension.disposeUIResources()
    updateAllProjects()
    assertNull(ModuleManager.getInstance(project).findModuleByName("m"))
    assertTrue(projectsManager.isIgnored(projectsManager.findProject(m)!!))
  }

  @Test
  fun testIgnoringProjectsForDetachedInUiModules() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    importProjectAsync()
    val module = getModule("m")
    assertNotNull(module)
    assertFalse(projectsManager.isIgnored(projectsManager.findProject(m)!!))
    writeAction {
      ModuleDeleteProvider.detachModules(project, arrayOf(module))
    }
    assertNull(ModuleManager.getInstance(project).findModuleByName("m"))
    assertTrue(projectsManager.isIgnored(projectsManager.findProject(m)!!))
  }

  @Test
  fun testWhenDeleteModuleThenChangeModuleDependencyToLibraryDependency() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
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
    importProjectAsync()
    assertModuleModuleDeps("m2", "m1")
    val module1 = getModule("m1")
    configConfirmationForYesAnswer()
    val action = DeleteAction()
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        action.actionPerformed(TestActionEvent.createTestEvent(action, createTestModuleDataContext(module1)))
      }
    }
    updateAllProjects()
    assertModuleModuleDeps("m2")
    assertModuleLibDep("m2", "Maven: test:m1:1")
  }

  @Test
  fun testWhenDeleteModuleInProjectStructureThenChangeModuleDependencyToLibraryDependency() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
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
    importProjectAsync()
    assertModuleModuleDeps("m2", "m1")
    val module1 = getModule("m1")
    val module2 = getModule("m2")
    val moduleManager = ModuleManager.getInstance(project)
    val moduleModel = moduleManager.getModifiableModel()
    val otherModuleRootModels = java.util.List.of(ModuleRootManager.getInstance(module2).getModifiableModel())
    ModuleDeleteProvider.removeModule(module1, otherModuleRootModels, moduleModel)
    val moduleStructureExtension = MavenModuleStructureExtension()
    moduleStructureExtension.moduleRemoved(module1)
    moduleStructureExtension.apply()
    moduleStructureExtension.disposeUIResources()
    updateAllProjects()
    assertModuleModuleDeps("m2")
    assertModuleLibDep("m2", "Maven: test:m1:1")
  }

  @Test
  fun testDoNotIgnoreProjectWhenModuleDeletedDuringImport() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    importProjectAsync()
    assertModules("project", "m")
    UsefulTestCase.assertSize(1, projectsManager.getRootProjects())
    UsefulTestCase.assertEmpty(projectsManager.getIgnoredFilesPaths())

    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    """.trimIndent())
    updateAllProjects()
    assertModules("project")
    UsefulTestCase.assertSize(1, projectsManager.getRootProjects())
    UsefulTestCase.assertEmpty(projectsManager.getIgnoredFilesPaths())
  }

  @Test
  fun testDoNotIgnoreProjectWhenSeparateMainAndTestModulesDeletedDuringImport() = runBlocking {
    importProjectAsync("""
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
    assertModules("project", "project.main", "project.test")
    UsefulTestCase.assertSize(1, projectsManager.getRootProjects())
    UsefulTestCase.assertEmpty(projectsManager.getIgnoredFilesPaths())
    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    updateAllProjects()
    assertModules("project")
    UsefulTestCase.assertSize(1, projectsManager.getRootProjects())
    UsefulTestCase.assertEmpty(projectsManager.getIgnoredFilesPaths())
  }

  @Test
  fun testDoNotRemoveMavenProjectsOnReparse() = runBlocking {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    val log = StringBuilder()
    projectsManager.addProjectsTreeListener(object : MavenProjectsTree.Listener {
      override fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
        for (each in updated) {
          log.append("updated: ").append(each.first.getDisplayName()).append(" ")
        }
        for (each in deleted) {
          log.append("deleted: ").append(each.getDisplayName()).append(" ")
        }
      }
    })
    withContext(Dispatchers.EDT) {
      FileContentUtil.reparseFiles(project, projectsManager.getProjectsFiles(), true)
    }
    assertTrue(log.toString(), log.length == 0)
  }

  @Test
  fun testShouldRemoveMavenProjectsAndNotAddThemToIgnore() = runBlocking {
    val mavenParentPom = createProjectSubFile("maven-parent/pom.xml", """
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

    val child1Pom = createProjectSubFile("maven-parent/child1/pom.xml", """
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
    refreshFiles(listOf(mavenParentPom, child1Pom))
    writeAction { ModuleManager.getInstance(project).newModule("non-maven", JAVA_MODULE_ENTITY_TYPE_ID_NAME) }
    importProjectAsync(mavenParentPom)
    assertEquals(3, ModuleManager.getInstance(project).modules.size)
    configConfirmationForYesAnswer()
    val action = RemoveManagedFilesAction()
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          action.actionPerformed(TestActionEvent.createTestEvent(action, createTestDataContext(mavenParentPom)))
        }
      }
    }
    assertEquals(1, ModuleManager.getInstance(project).modules.size)
    assertEquals("non-maven", ModuleManager.getInstance(project).modules[0].getName())
    UsefulTestCase.assertEmpty(projectsManager.getIgnoredFilesPaths())

    //should then import project in non-ignored state again
    importProjectAsync(mavenParentPom)
    assertEquals(3, ModuleManager.getInstance(project).modules.size)
    UsefulTestCase.assertEmpty(projectsManager.ignoredFilesPaths)
  }

  @Test
  fun testSameArtifactIdDifferentTypeDependency() = runBlocking {
    createProjectPom("""
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
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
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
    val m3 = createModulePom("m3",
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
    importProjectAsync()
    assertModuleModuleDeps("m2", "m1")
    assertModuleModuleDeps("m3", "m1")
    val mavenProject2 = projectsManager.findProject(m2)
    val m21dep = mavenProject2!!.findDependencies(MavenId("test:m1:1"))[0]
    assertEquals("jar", m21dep.type)
    val mavenProject3 = projectsManager.findProject(m3)
    val m31dep = mavenProject3!!.findDependencies(MavenId("test:m1:1"))[0]
    assertEquals("ejb", m31dep.type)
  }

  @Test
  fun shouldUnsetMavenizedIfManagedFilesWasRemoved() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertModules("project")
    UsefulTestCase.assertSize(1, projectsManager.getRootProjects())
    waitForImportWithinTimeout {
      projectsManager.removeManagedFiles(listOf(projectPom))
    }
    UsefulTestCase.assertSize(0, projectsManager.getRootProjects())
  }

  @Test
  fun testShouldKeepModuleName() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertEquals("project", ModuleManager.getInstance(project).modules[0].getName())
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project1</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertEquals("project", ModuleManager.getInstance(project).modules[0].getName())
  }

  @Test
  fun testModuleNameTemplateArtifactId() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>artifactId</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertEquals("artifactId", ModuleManager.getInstance(project).modules[0].getName())
  }

  @Test
  fun testModuleNameTemplateGroupIdArtifactId() = runBlocking {
    Registry.get("maven.import.module.name.template").setValue("groupId.artifactId", getTestRootDisposable())
    importProjectAsync("""
                    <groupId>myGroup</groupId>
                    <artifactId>artifactId</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertEquals("myGroup.artifactId", ModuleManager.getInstance(project).modules[0].getName())
  }

  @Test
  fun testModuleNameTemplateFolderName() = runBlocking {
    Registry.get("maven.import.module.name.template").setValue("folderName", getTestRootDisposable())
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>ignoredArtifactId</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertNotSame("ignoredArtifactId", projectRoot.getName())
    assertEquals(projectRoot.getName(), ModuleManager.getInstance(project).modules[0].getName())
  }

  companion object {
    private fun createTestModuleDataContext(vararg modules: Module): DataContext {
      val defaultContext = DataManager.getInstance().getDataContext()
      return CustomizedDataContext.withSnapshot(defaultContext) { sink ->
        sink[LangDataKeys.MODULE_CONTEXT_ARRAY] = modules
        sink[ProjectView.UNLOADED_MODULES_CONTEXT_KEY] = listOf() // UnloadedModuleDescription
        sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = MavenModuleDeleteProvider()
      }
    }
  }
}
