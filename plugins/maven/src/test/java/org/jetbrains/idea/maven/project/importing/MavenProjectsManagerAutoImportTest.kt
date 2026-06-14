// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.idea.IJIgnore
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertHasPendingProjectForReload
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertNoPendingProjectForReload
import com.intellij.maven.testFramework.fixtures.assertUnorderedPathsAreEqual
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.scheduleProjectImportAndWait
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsManagerAutoImportTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  // Forwarders to keep the legacy bodies one-to-one (these were inherited members of the base test class).
  private val projectsTree get() = maven.projectsTree
  private val projectPath get() = maven.projectPath
  private val projectRoot get() = maven.projectRoot
  private val mavenGeneralSettings get() = maven.mavenGeneralSettings
  private val envVar: String get() = if (SystemInfo.isWindows) "TEMP" else "TMPDIR"
  private suspend fun assertHasPendingProjectForReload() = maven.assertHasPendingProjectForReload()
  private fun assertModuleLibDep(moduleName: String, depName: String, classesPath: String) = maven.assertModuleLibDep(moduleName, depName, classesPath)
  private fun assertModuleLibDeps(moduleName: String, vararg deps: String) = maven.assertModuleLibDeps(moduleName, *deps)
  private suspend fun importProjects(vararg files: VirtualFile) = maven.importProjectsAsync(*files)
  private fun <E : Throwable?> runWriteAction(runnable: ThrowableRunnable<E>) = WriteCommandAction.writeCommandAction(maven.project).run(runnable)
  private fun <R, E : Throwable?> runWriteAction(computable: ThrowableComputable<R, E>): R = WriteCommandAction.writeCommandAction(maven.project).compute(computable)

  @BeforeEach
  fun setUp() {
    maven.initProjectsManager(true)
  }

  @Test
  fun testResolvingEnvVariableInRepositoryPath() = runBlocking {
    val temp = System.getenv(envVar)
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("<localRepository>\${env.$envVar}/tmpRepo</localRepository>")
    }
    val repoPath = Path.of(temp, "tmpRepo")
    repoPath.createDirectories()
    val repo = repoPath.toRealPath().toCanonicalPath()
    assertEquals(repo, MavenSettingsCache.getInstance(maven.project).getEffectiveUserLocalRepo().toCanonicalPath())
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
    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://$repo/junit/junit/4.0/junit-4.0.jar!/")
  }

  @Test
  fun testUpdatingProjectsOnProfilesXmlChange() = runBlocking {
    maven.createProjectPom("""
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
    maven.createModulePom("m",
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
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("""
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
    maven.importProjectAsync()
    val roots = projectsTree.rootProjects
    val parentNode = roots[0]
    val childNode = projectsTree.getModules(roots[0])[0]
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value1")))
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("""
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
    maven.importProjectAsync()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value2")))
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("<profiles/>")
    }
    maven.importProjectAsync()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/\${prop}")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/\${prop}")))
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("""
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
    maven.importProjectAsync()
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value2")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value2")))
  }

  @Test
  fun testUpdatingProjectsWhenSettingsXmlLocationIsChanged() = runBlocking {
    maven.createProjectPom("""
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
    maven.createModulePom("m",
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
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("""
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
    maven.importProjectAsync()
    val roots = projectsTree.rootProjects
    val parentNode = roots[0]
    val childNode = projectsTree.getModules(roots[0])[0]
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value1")))
    maven.waitForImportWithinTimeout {
      mavenGeneralSettings.setUserSettingsFile("")
    }
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/\${prop}")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/\${prop}")))
    maven.waitForImportWithinTimeout {
      mavenGeneralSettings.setUserSettingsFile(Paths.get(maven.dir.toString(), "settings.xml").toString())
    }
    assertUnorderedPathsAreEqual(parentNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/value1")))
    assertUnorderedPathsAreEqual(childNode.sources, listOf(FileUtil.toSystemDependentName("$projectPath/m/value1")))
  }

  @Test
  fun testUpdatingMavenPathsWhenSettingsChanges() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    val repo1 = Path.of(maven.dir.toString(), "localRepo1")
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("""
                      <localRepository>
                      ${repo1.toString()}</localRepository>
                      """.trimIndent())
    }
    assertEquals(repo1, MavenSettingsCache.getInstance(maven.project).getEffectiveUserLocalRepo())
    val repo2 = Path.of(maven.dir.toString(), "localRepo2")
    maven.waitForImportWithinTimeout {
      maven.updateSettingsXml("""
                      <localRepository>
                      ${repo2.toString()}</localRepository>
                      """.trimIndent())
    }
    assertEquals(repo2, MavenSettingsCache.getInstance(maven.project).getEffectiveUserLocalRepo())
  }

  @Test
  fun testSchedulingReimportWhenPomFileIsDeleted() = runBlocking {
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
    //myProjectsManager.performScheduledImportInTests(); // ensure no pending requests
    maven.assertModules("project", maven.mn("project", "m"))
    runWriteAction<IOException> { m.delete(this) }

    maven.scheduleProjectImportAndWait()
    maven.assertModules("project")
  }

  @Test
  fun testHandlingDirectoryWithPomFileDeletion() = runBlocking {
    maven.importProjectAsync("""
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
    replaceContent(maven.projectPom, maven.createPomXml("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir/module</module>
                       </modules>
                       """.trimIndent()))
    maven.scheduleProjectImportAndWait()
    assertEquals(2, MavenProjectsManager.getInstance(maven.project).getProjects().size)
    val dir = maven.projectRoot.findChild("dir")
    WriteCommandAction.writeCommandAction(maven.project).run<IOException> { dir!!.delete(null) }

    maven.scheduleProjectImportAndWait()
    assertEquals(1, MavenProjectsManager.getInstance(maven.project).getProjects().size)
  }

  @Test
  fun testScheduleReimportWhenPluginConfigurationChangesInTagName() = runBlocking {
    maven.importProjectAsync("""
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
    maven.assertNoPendingProjectForReload()
    replaceContent(maven.projectPom, maven.createPomXml("""
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
    maven.scheduleProjectImportAndWait()
  }

  @Test
  fun testUpdatingProjectsWhenAbsentModuleFileAppears() = runBlocking {
    maven.importProjectAsync("""
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
    maven.scheduleProjectImportAndWait()
    val children = projectsTree.getModules(roots[0])
    assertEquals(1, children.size)
    assertEquals(m, children[0].file)
  }

  @Test
  fun testScheduleReimportWhenPluginConfigurationChangesInValue() = runBlocking {
    maven.importProjectAsync("""
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
    maven.assertNoPendingProjectForReload()
    replaceContent(maven.projectPom, maven.createPomXml("""
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
    maven.scheduleProjectImportAndWait()
  }

  @Test
  fun testSchedulingResolveOfDependentProjectWhenDependencyChanges() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    maven.createModulePom("m1", """
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
    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.importProjectAsync()
    maven.assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1")
    replaceContent(m2, maven.createPomXml("""
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
    maven.scheduleProjectImportAndWait()
    maven.assertModuleModuleDeps("m1", "m2")

    maven.updateAllProjects()
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0")
  }

  @Test
  fun testAddingManagedFileAndChangingAggregation() = runBlocking {
    maven.importProjectAsync("""
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
    maven.scheduleProjectImportAndWait()
    assertEquals(1, projectsTree.rootProjects.size)
    assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)

    maven.updateAllProjects()
    assertEquals(1, projectsTree.rootProjects.size)
    assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)

    createPom("", """
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.updateAllProjects()
    assertEquals(1, projectsTree.rootProjects.size)
    assertEquals(0, projectsTree.getModules(projectsTree.rootProjects[0]).size)
  }

  @Test
  fun testSchedulingResolveOfDependentProjectWhenDependencyIsDeleted() = runBlocking {
    val p = maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    maven.createModulePom("m1", """
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
    val m2 = maven.createModulePom("m2", """
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
    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")
    maven.assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0")

    WriteCommandAction.writeCommandAction(maven.project).run<IOException> {
      p.writeText(maven.createPomXml("""
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

    maven.scheduleProjectImportAndWait()
    maven.assertModules("project", "m1")
    maven.assertModuleModuleDeps("m1")
    assertModuleLibDeps("m1", "Maven: test:m2:1")
  }

  @IJIgnore(issue = "IDEA-370031")
  @Test
  fun testUpdatingProjectsWhenAbsentManagedProjectFileAppears() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m</module>
                    </modules>
                    """.trimIndent())
    assertEquals(1, projectsTree.rootProjects.size)
    WriteCommandAction.writeCommandAction(maven.project).run<IOException> { maven.projectPom.delete(this) }

    maven.importProjectAsync()
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
    //maven.importProjectAsync();
    maven.scheduleProjectImportAndWait()
    assertEquals(1, projectsTree.rootProjects.size)
  }

  @Test
  fun testUpdatingProjectsWhenRenaming() = runBlocking {
    val p1 = maven.createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val p2 = maven.createModulePom("project2",
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
    val p1 = maven.createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val p2 = maven.createModulePom("project2",
                             """
                                             <groupId>test</groupId>
                                             <artifactId>project2</artifactId>
                                             <version>1</version>
                                             """.trimIndent())
    importProjects(p1, p2)
    val oldDir = p2.getParent()
    runWriteAction<RuntimeException> { VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot) }
    val newDir = runWriteAction<VirtualFile, IOException> { maven.projectRoot.createChildDirectory(this, "foo") }
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
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    val m = maven.createModulePom("m1",
                            """
                                            <groupId>test</groupId>
                                            <artifactId>m</artifactId>
                                            <version>1</version>
                                            """.trimIndent())
    maven.importProjectAsync()
    val oldDir = m.getParent()
    edtWriteAction {
      val newDir = maven.projectRoot.createChildDirectory(this, "m2")
      assertEquals(1, projectsTree.rootProjects.size)
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, newDir)
      scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt()
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, oldDir)
      scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt()
      assertEquals(1, projectsTree.getModules(projectsTree.rootProjects[0]).size)
      m.move(this, maven.projectRoot.createChildDirectory(this, "xxx"))
    }

    scheduleProjectImportAndWaitWithoutCheckFloatingBar()
    maven.waitForImportWithinTimeout {
      maven.projectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
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
    maven.waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt()
      }
    }
  }

  @RequiresEdt
  private fun scheduleProjectImportAndWaitWithoutCheckFloatingBarEdt() {
    ExternalSystemProjectTracker.getInstance(maven.project).scheduleProjectRefresh()
  }

  private fun createPom(relativePath: String, xml: String): VirtualFile {
    val dir = maven.createProjectSubDir(relativePath)
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
    WriteCommandAction.runWriteCommandAction(maven.project, ThrowableComputable<Any?, IOException?> {
      VfsUtil.saveText(file, content)
      null
    } as ThrowableComputable<*, IOException?>)
  }
}
