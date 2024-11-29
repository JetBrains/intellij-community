// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.io.path.exists

class SnapshotDependenciesImportingTest : MavenMultiVersionImportingTestCase() {
  private var remoteRepoDir: File? = null

  override fun setUp() {
    super.setUp()
    // disable local mirrors
    updateSettingsXmlFully("<settings></settings>")
  }

  override fun setUpInWriteAction() {
    super.setUpInWriteAction()

    remoteRepoDir = File(dir, "remote")
    remoteRepoDir!!.mkdirs()
  }

  @Test
  fun testSnapshotVersionDependencyToModule() = runBlocking {
    performTestWithDependencyVersion("1-SNAPSHOT")
  }

  @Test
  fun testSnapshotRangeDependencyToModule() = runBlocking {
    performTestWithDependencyVersion("SNAPSHOT")
  }

  private suspend fun performTestWithDependencyVersion(version: String) {
    if (!hasMavenInstallation()) return

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

    createModulePom("m1", """<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>
${repositoriesSection()}<dependencies>
  <dependency>
    <groupId>test</groupId>
    <artifactId>m2</artifactId>
    <version>
$version</version>
  </dependency>
</dependencies>
""")

    createModulePom("m2", """
   <groupId>test</groupId>
   <artifactId>m2</artifactId>
   <version>
   $version</version>
   ${distributionManagementSection()}
   """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2")
    assertModuleModuleDeps("m1", "m2")

    // in order to force maven to resolve dependency into remote one we have to
    // clean up local repository.
    deploy("m2")
    removeFromLocalRepository("test")

    importProjectAsync()

    assertModules("project", "m1", "m2")
    assertModuleModuleDeps("m1", "m2")
  }

  @Test
  fun testNamingLibraryTheSameWayRegardlessAvailableSnapshotVersion() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    deployArtifact("test", "foo", "1-SNAPSHOT")

    importProjectAsync("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
${repositoriesSection()}<dependencies>
  <dependency>
    <groupId>test</groupId>
    <artifactId>foo</artifactId>
    <version>1-SNAPSHOT</version>
  </dependency>
</dependencies>
""")
    assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT")

    removeFromLocalRepository("test")

    importProjectAsync()
    assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT")
  }

  @Test
  fun testAttachingCorrectJavaDocsAndSources() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    deployArtifact("test", "foo", "1-SNAPSHOT",
                   """
                     <build>
                       <plugins>
                         <plugin>
                           <artifactId>maven-source-plugin</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>jar</goal>
                               </goals>
                             </execution>
                           </executions>
                         </plugin>
                         <plugin>
                           <artifactId>maven-javadoc-plugin</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>jar</goal>
                               </goals>
                             </execution>
                           </executions>
                         </plugin>
                       </plugins>
                     </build>
                     """.trimIndent())

    removeFromLocalRepository("test")

    importProjectAsync("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
${repositoriesSection()}<dependencies>
  <dependency>
    <groupId>test</groupId>
    <artifactId>foo</artifactId>
    <version>1-SNAPSHOT</version>
  </dependency>
</dependencies>
""")
    assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT")

    downloadArtifacts()

    assertModuleLibDep("project",
                       "Maven: test:foo:1-SNAPSHOT",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar!/",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar!/",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar!/")

    assertTrue(repositoryFile.resolve("/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar").exists())
    assertTrue(repositoryFile.resolve("/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar").exists())
    assertTrue(repositoryFile.resolve("/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar").exists())
  }

  @Test
  fun testCorrectlyUpdateRootEntriesWithActualPathForSnapshotDependencies() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    deployArtifact("test", "foo", "1-SNAPSHOT",
                   """
                     <build>
                       <plugins>
                         <plugin>
                           <artifactId>maven-source-plugin</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>jar</goal>
                               </goals>
                             </execution>
                           </executions>
                         </plugin>
                         <plugin>
                           <artifactId>maven-javadoc-plugin</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>jar</goal>
                               </goals>
                             </execution>
                           </executions>
                         </plugin>
                       </plugins>
                     </build>
                     """.trimIndent())
    removeFromLocalRepository("test")

    importProjectAsync("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
${repositoriesSection()}<dependencies>
  <dependency>
    <groupId>test</groupId>
    <artifactId>foo</artifactId>
    <version>1-SNAPSHOT</version>
  </dependency>
</dependencies>
""")
    assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT")

    downloadArtifacts()

    assertModuleLibDep("project",
                       "Maven: test:foo:1-SNAPSHOT",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar!/",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar!/",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar!/")


    deployArtifact("test", "foo", "1-SNAPSHOT",
                   """
                     <build>
                       <plugins>
                         <plugin>
                           <artifactId>maven-source-plugin</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>jar</goal>
                               </goals>
                             </execution>
                           </executions>
                         </plugin>
                         <plugin>
                           <artifactId>maven-javadoc-plugin</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>jar</goal>
                               </goals>
                             </execution>
                           </executions>
                         </plugin>
                       </plugins>
                     </build>
                     """.trimIndent())
    removeFromLocalRepository("test")

    assertModuleLibDep("project",
                       "Maven: test:foo:1-SNAPSHOT",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar!/",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar!/",
                       "jar://" + repositoryPath + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar!/")
  }

  private fun deployArtifact(groupId: String, artifactId: String, version: String, tail: String = "") {
    val moduleName = "___$artifactId"

    createProjectSubFile("$moduleName/src/main/java/Foo.java",
                         """
                           /**
                            * some doc
                            */
                           public class Foo { }
                           """.trimIndent())

    val m = createModulePom(moduleName,
                            """
                                   <groupId>
                                   $groupId</groupId>
                                   <artifactId>
                                   $artifactId</artifactId>
                                   <version>
                                   $version</version>
                                   ${distributionManagementSection()}$tail
                                   """.trimIndent())

    deploy(moduleName)
    FileUtil.delete(File(m.getParent().getPath()))
  }

  private fun deploy(modulePath: String) {
    executeGoal(modulePath, "deploy")
  }

  private fun repositoriesSection(): String {
    return """<repositories>
  <repository>
    <id>internal</id>
    <url>file:///""" + FileUtil.toSystemIndependentName(
      remoteRepoDir!!.path) + "</url>\n" +
           "    <snapshots>\n" +
           "      <enabled>true</enabled>\n" +
           "      <updatePolicy>always</updatePolicy>\n" +
           "    </snapshots>\n" +
           "  </repository>\n" +
           "</repositories>"
  }

  private fun distributionManagementSection(): String {
    return """<distributionManagement>
  <snapshotRepository>
    <id>internal</id>
    <url>file:///""" + FileUtil.toSystemIndependentName(
      remoteRepoDir!!.path) + "</url>\n" +
           "  </snapshotRepository>\n" +
           "</distributionManagement>"
  }
}