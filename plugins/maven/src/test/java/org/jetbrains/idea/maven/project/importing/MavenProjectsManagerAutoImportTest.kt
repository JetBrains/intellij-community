// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.idea.IJIgnore
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.junit.Test
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

class MavenProjectsManagerAutoImportTest : MavenMultiVersionImportingTestCase() {

  override fun setUp() {
    super.setUp()
    initProjectsManager(true)
  }

  @Test
  fun testResolvingEnvVariableInRepositoryPath() = runBlocking {
    val temp = System.getenv(envVar)
    waitForImportWithinTimeout {
      updateSettingsXml("<localRepository>\${env.$envVar}/tmpRepo</localRepository>")
    }
    val repoPath = Path.of(temp, "tmpRepo")
    repoPath.createDirectories()
    val repo = repoPath.toRealPath().toCanonicalPath()
    assertEquals(repo, MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo().toCanonicalPath())
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
                       "jar://$repo/junit/junit/4.0/junit-4.0.jar!/")
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
      mavenGeneralSettings.setUserSettingsFile(Paths.get(dir.toString(), "settings.xml").toString())
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
    val repo1 = Path.of(dir.toString(), "localRepo1")
    waitForImportWithinTimeout {
      updateSettingsXml("""
                      <localRepository>
                      ${repo1.toString()}</localRepository>
                      """.trimIndent())
    }
    assertEquals(repo1, MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo())
    val repo2 = Path.of(dir.toString(), "localRepo2")
    waitForImportWithinTimeout {
      updateSettingsXml("""
                      <localRepository>
                      ${repo2.toString()}</localRepository>
                      """.trimIndent())
    }
    assertEquals(repo2, MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo())
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

    scheduleProjectImportAndWait()
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
    createPom("dir/module", """
      <groupId>test</groupId>
      <artifactId>module</artifactId>
      <version>1</version>
      """.trimIndent())
    replaceContent(projectPom, createPomXml("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir/module</module>
                       </modules>
                       """.trimIndent()))
    scheduleProjectImportAndWait()
    assertEquals(2, MavenProjectsManager.getInstance(project).getProjects().size)
    val dir = projectRoot.findChild("dir")
    WriteCommandAction.writeCommandAction(project).run<IOException> { dir!!.delete(null) }

    scheduleProjectImportAndWait()
    assertEquals(1, MavenProjectsManager.getInstance(project).getProjects().size)
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
    replaceContent(projectPom, createPomXml("""
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
                       """.trimIndent()))
    assertHasPendingProjectForReload()
    scheduleProjectImportAndWait()
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
    val m = createPom("m",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """.trimIndent())
    scheduleProjectImportAndWait()
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
    replaceContent(projectPom, createPomXml("""
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
                       """.trimIndent()))
    assertHasPendingProjectForReload()
    scheduleProjectImportAndWait()
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
    val m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    importProjectAsync()
    assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1")
    replaceContent(m2, createPomXml("""
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
      """.trimIndent()))
    scheduleProjectImportAndWait()
    assertModuleModuleDeps("m1", "m2")

    updateAllProjects()
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0")
  }

  @Test
  fun testAddingManagedFileAndChangingAggregation() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m</module>
                    </modules>
                    """.trimIndent())
    val m = createPom("m",
                            """
                            <groupId>test</groupId>
                            <artifactId>m</artifactId>
                            <version>1</version>
                            """.trimIndent())
    scheduleProjectImportAndWait()
    assertEquals(1, projectsTree.rootProjects.size)
    assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)

    updateAllProjects()
    assertEquals(1, projectsTree.rootProjects.size)
    assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)

    createPom("", """
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
    val p = createProjectPom("""
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

    WriteCommandAction.writeCommandAction(project).run<IOException> {
      p.writeText(createPomXml("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent()))
      m2.delete(this)
    }

    scheduleProjectImportAndWait()
    assertModules("project", "m1")
    assertModuleModuleDeps("m1")
    assertModuleLibDeps("m1", "Maven: test:m2:1")
  }

  @IJIgnore(issue = "IDEA-370031")
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
    WriteCommandAction.writeCommandAction(project).run<IOException> { projectPom.delete(this) }

    importProjectAsync()
    assertEquals(0, projectsTree.rootProjects.size)
    createPom("", """"
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    //importProjectAsync();
    scheduleProjectImportAndWait()
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
    runWriteAction<RuntimeException> { VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot) }
    val newDir = runWriteAction<VirtualFile, IOException> { projectRoot.createChildDirectory(this, "foo") }
    assertEquals(2, projectsTree.rootProjects.size)
    runWriteAction<IOException> { p2.move(this, newDir) }

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
    edtWriteAction {
      val newDir = projectRoot.createChildDirectory(this, "m2")
      assertEquals(1, projectsTree.rootProjects.size)
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, newDir)
      scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt()
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, oldDir)
      scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt()
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, projectRoot.createChildDirectory(this, "xxx"))
    }

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
    ExternalSystemProjectTracker.getInstance(project).scheduleProjectRefresh()
  }

  private fun createPom(relativePath: String, xml: String): VirtualFile {
    val dir = createProjectSubDir(relativePath)
    val pomName = "pom.xml"
    var f = dir.findChild(pomName)
    if (f == null) {
      try {
        f = WriteAction.computeAndWait<VirtualFile, IOException> { dir.createChildData(null, pomName) }!!
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }
    }
    replaceContent(f, xml)
    return f
  }

  private fun replaceContent(file: VirtualFile, content: String) {
    WriteCommandAction.runWriteCommandAction(project, ThrowableComputable<Any?, IOException?> {
      VfsUtil.saveText(file, content)
      null
    } as ThrowableComputable<*, IOException?>)
  }
}
