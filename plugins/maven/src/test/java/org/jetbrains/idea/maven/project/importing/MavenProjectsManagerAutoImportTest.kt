// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.importing.MavenProjectLegacyImporter
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.io.IOException

class MavenProjectsManagerAutoImportTest : MavenMultiVersionImportingTestCase() {
  override fun runInDispatchThread() = false

  override fun setUp() {
    super.setUp()
    initProjectsManager(true)
  }

  @Test
  fun testResolvingEnvVariableInRepositoryPath() = runBlocking {
    val temp = System.getenv(getEnvVar())
    waitForImportWithinTimeout {
      updateSettingsXml("<localRepository>\${env." + getEnvVar() + "}/tmpRepo</localRepository>")
    }
    val repo = File("$temp/tmpRepo").getCanonicalFile()
    assertEquals(repo.path, mavenGeneralSettings.getEffectiveLocalRepository().path)
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
    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + FileUtil.toSystemIndependentName(repo.path) + "/junit/junit/4.0/junit-4.0.jar!/")
  }

  @Test
  fun testUpdatingProjectsOnProfilesXmlChange() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       <build>
                         <sourceDirectory>${'$'}{prop}</sourceDirectory>
                       </build>
                       """.trimIndent())
    createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>${'$'}{prop}</sourceDirectory>
                      </build>
                      """.trimIndent())
    waitForImportWithinTimeout {
      updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value1</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())
    }
    importProjectAsync()
    val roots = projectsTree.rootProjects
    val parentNode = roots[0]
    val childNode = projectsTree.getModules(roots[0])[0]
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value1")))
    waitForImportWithinTimeout {
      updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value2</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())
    }
    importProjectAsync()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value2")))
    waitForImportWithinTimeout {
      updateSettingsXml("<profiles/>")
    }
    importProjectAsync()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/\${prop}")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/\${prop}")))
    waitForImportWithinTimeout {
      updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value2</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())
    }
    importProjectAsync()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value2")))
  }

  @Test
  fun testUpdatingProjectsWhenSettingsXmlLocationIsChanged() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       <build>
                         <sourceDirectory>${'$'}{prop}</sourceDirectory>
                       </build>
                       """.trimIndent())
    createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>${'$'}{prop}</sourceDirectory>
                      </build>
                      """.trimIndent())
    waitForImportWithinTimeout {
      updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value1</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())
    }
    importProjectAsync()
    val roots = projectsTree.rootProjects
    val parentNode = roots[0]
    val childNode = projectsTree.getModules(roots[0])[0]
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value1")))
    waitForImportWithinTimeout {
      mavenGeneralSettings.setUserSettingsFile("")
    }
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/\${prop}")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/\${prop}")))
    waitForImportWithinTimeout {
      mavenGeneralSettings.setUserSettingsFile(File(myDir, "settings.xml").path)
    }
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value1")))
  }

  @Test
  fun testUpdatingMavenPathsWhenSettingsChanges() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    val repo1 = File(myDir, "localRepo1")
    waitForImportWithinTimeout {
      updateSettingsXml("""
                      <localRepository>
                      ${repo1.path}</localRepository>
                      """.trimIndent())
    }
    assertEquals(repo1, mavenGeneralSettings.getEffectiveLocalRepository())
    val repo2 = File(myDir, "localRepo2")
    waitForImportWithinTimeout {
      updateSettingsXml("""
                      <localRepository>
                      ${repo2.path}</localRepository>
                      """.trimIndent())
    }
    assertEquals(repo2, mavenGeneralSettings.getEffectiveLocalRepository())
  }

  @Test
  fun testSchedulingReimportWhenPomFileIsDeleted() = runBlocking {
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
    //myProjectsManager.performScheduledImportInTests(); // ensure no pending requests
    assertModules("project", mn("project", "m"))
    runWriteAction<IOException> { m.delete(this) }

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    scheduleProjectImportAndWaitAsync()
    assertModules("project")
  }

  @Test
  fun testHandlingDirectoryWithPomFileDeletion() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    """.trimIndent())
    createModulePom("dir/module", """
      <groupId>test</groupId>
      <artifactId>module</artifactId>
      <version>1</version>
      """.trimIndent())
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir/module</module>
                       </modules>
                       """.trimIndent())
    scheduleProjectImportAndWaitAsync()
    assertEquals(2, MavenProjectsManager.getInstance(myProject).getProjects().size)
    val dir = myProjectRoot.findChild("dir")
    WriteCommandAction.writeCommandAction(myProject).run<IOException> { dir!!.delete(null) }

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    scheduleProjectImportAndWaitAsync()
    assertEquals(1, MavenProjectsManager.getInstance(myProject).getProjects().size)
  }

  @Test
  fun testScheduleReimportWhenPluginConfigurationChangesInTagName() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                          <configuration>
                            <foo>value</foo>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    assertNoPendingProjectForReload()
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>group</groupId>
                             <artifactId>id</artifactId>
                             <version>1</version>
                             <configuration>
                               <bar>value</bar>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())
    assertHasPendingProjectForReload()
    scheduleProjectImportAndWaitAsync()
  }

  @Test
  fun testUpdatingProjectsWhenAbsentModuleFileAppears() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m</module>
                    </modules>
                    """.trimIndent())
    val roots = projectsTree.rootProjects
    val parentNode = roots[0]
    assertNotNull(parentNode)
    assertTrue(projectsTree.getModules(roots[0]).isEmpty())
    val m = createModulePom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    scheduleProjectImportAndWaitAsync()
    val children = projectsTree.getModules(roots[0])
    assertEquals(1, children.size)
    assertEquals(m, children[0].file)
  }

  @Test
  fun testScheduleReimportWhenPluginConfigurationChangesInValue() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                          <configuration>
                            <foo>value</foo>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    assertNoPendingProjectForReload()
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>group</groupId>
                             <artifactId>id</artifactId>
                             <version>1</version>
                             <configuration>
                               <foo>value2</foo>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())
    assertHasPendingProjectForReload()
    scheduleProjectImportAndWaitAsync()
  }

  @Test
  fun testSchedulingResolveOfDependentProjectWhenDependencyChanges() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())
    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    importProjectAsync()
    assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1")
    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
        </dependency>
      </dependencies>
      """.trimIndent())
    scheduleProjectImportAndWaitAsync()
    assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0")
  }

  @Test
  fun testAddingManagedFileAndChangingAggregation() = runBlocking {
    Assume.assumeTrue(isWorkspaceImport)
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
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
    scheduleProjectImportAndWaitAsync()
    assertEquals(1, projectsTree.rootProjects.size)
    assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)

    updateAllProjects()
    assertEquals(1, projectsTree.rootProjects.size)
    assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    updateAllProjects()
    assertEquals(1, projectsTree.rootProjects.size)
    assertEquals(0, projectsTree.getModules(projectsTree.rootProjects[0]).size)
  }

  @Test
  fun testSchedulingResolveOfDependentProjectWhenDependencyIsDeleted() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())
    val m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
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
    assertModules("project", "m1", "m2")
    assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0")
    WriteCommandAction.writeCommandAction(myProject).run<IOException> { m2.delete(this) }


    //configConfirmationForYesAnswer();// should update deps even if module is not removed
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    scheduleProjectImportAndWaitAsync()
    assertModules("project", "m1")
    assertModuleModuleDeps("m1")
    assertModuleLibDeps("m1", "Maven: test:m2:1")
  }

  @Test
  fun testUpdatingProjectsWhenAbsentManagedProjectFileAppears() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m</module>
                    </modules>
                    """.trimIndent())
    assertEquals(1, projectsTree.rootProjects.size)
    WriteCommandAction.writeCommandAction(myProject).run<IOException> { myProjectPom.delete(this) }

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    importProjectAsync()
    assertEquals(0, projectsTree.rootProjects.size)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    //importProjectAsync();
    scheduleProjectImportAndWaitAsync()
    assertEquals(1, projectsTree.rootProjects.size)
  }

  @Test
  fun testUpdatingProjectsWhenRenaming() = runBlocking {
    val p1 = createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val p2 = createModulePom("project2",
                             """
                                             <groupId>test</groupId>
                                             <artifactId>project2</artifactId>
                                             <version>1</version>
                                             """.trimIndent())
    importProjects(p1, p2)
    assertEquals(2, projectsTree.rootProjects.size)
    runWriteAction<IOException> { p2.rename(this, "foo.bar") }

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    scheduleProjectImportAndWaitWithoutCheckFloatingBar()
    assertEquals(1, projectsTree.rootProjects.size)
    runWriteAction<IOException> { p2.rename(this, "pom.xml") }
    scheduleProjectImportAndWaitWithoutCheckFloatingBar()
    assertEquals(2, projectsTree.rootProjects.size)
  }

  @Test
  fun testUpdatingProjectsWhenMoving() = runBlocking {
    val p1 = createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val p2 = createModulePom("project2",
                             """
                                             <groupId>test</groupId>
                                             <artifactId>project2</artifactId>
                                             <version>1</version>
                                             """.trimIndent())
    importProjects(p1, p2)
    val oldDir = p2.getParent()
    runWriteAction<RuntimeException> { VfsUtil.markDirtyAndRefresh(false, true, true, myProjectRoot) }
    val newDir = runWriteAction<VirtualFile, IOException> { myProjectRoot.createChildDirectory(this, "foo") }
    assertEquals(2, projectsTree.rootProjects.size)
    runWriteAction<IOException> { p2.move(this, newDir) }

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    scheduleProjectImportAndWaitWithoutCheckFloatingBar()
    assertEquals(1, projectsTree.rootProjects.size)
    runWriteAction<IOException> { p2.move(this, oldDir) }
    scheduleProjectImportAndWaitWithoutCheckFloatingBar()
    assertEquals(2, projectsTree.rootProjects.size)
  }

  @Test
  fun testUpdatingProjectsWhenMovingModuleFile() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    val m = createModulePom("m1",
                            """
                                            <groupId>test</groupId>
                                            <artifactId>m</artifactId>
                                            <version>1</version>
                                            """.trimIndent())
    importProjectAsync()
    val oldDir = m.getParent()
    writeAction {
      val newDir = myProjectRoot.createChildDirectory(this, "m2")
      assertEquals(1, projectsTree.rootProjects.size)
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, newDir)
      scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt()
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, oldDir)
      scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt()
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, myProjectRoot.createChildDirectory(this, "xxx"))
    }

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    scheduleProjectImportAndWaitWithoutCheckFloatingBar()
    waitForImportWithinTimeout {
      projectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
    }
    assertEquals(0, projectsTree.getModules(projectsTree.rootProjects[0]).size)
  }

  /**
   * temporary solution. since The maven deletes files during the import process (renaming the file).
   * And therefore the floating bar is always displayed.
   * Because there is no information who deleted the import file or the other user action
   * problem in MavenProjectsAware#collectSettingsFiles() / yieldAll(projectsTree.projectsFiles.map { it.path })
   */
  @RequiresBackgroundThread
  private suspend fun scheduleProjectImportAndWaitWithoutCheckFloatingBar() {
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt()
      }
    }
  }

  @RequiresEdt
  private fun scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt() {
    ExternalSystemProjectTracker.getInstance(myProject).scheduleProjectRefresh()
  }
}
