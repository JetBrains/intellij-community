// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.downloadArtifacts
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.removeFromLocalRepository
import com.intellij.maven.testFramework.fixtures.repositoryPathCanonical
import com.intellij.maven.testFramework.fixtures.updateSettingsXmlFully
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.executeGoal
import org.jetbrains.idea.maven.fixtures.hasMavenInstallation
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class SnapshotDependenciesImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private var remoteRepoDir: Path? = null

  @BeforeEach
  fun setUp() {
    // disable local mirrors
    maven.updateSettingsXmlFully("<settings></settings>")

    remoteRepoDir = maven.dir.resolve("remote")
    remoteRepoDir!!.createDirectories()
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
    if (!maven.hasMavenInstallation()) return

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

    maven.createModulePom("m1", """<groupId>test</groupId>
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

    maven.createModulePom("m2", """
   <groupId>test</groupId>
   <artifactId>m2</artifactId>
   <version>
   $version</version>
   ${distributionManagementSection()}
   """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")
    maven.assertModuleModuleDeps("m1", "m2")

    // in order to force maven to resolve dependency into remote one we have to
    // clean up local repository.
    deploy("m2")
    maven.removeFromLocalRepository("test")

    maven.importProjectAsync()

    maven.assertModules("project", "m1", "m2")
    maven.assertModuleModuleDeps("m1", "m2")
  }

  @Test
  fun testNamingLibraryTheSameWayRegardlessAvailableSnapshotVersion() = runBlocking {
    if (!maven.hasMavenInstallation()) return@runBlocking

    deployArtifact("test", "foo", "1-SNAPSHOT")

    maven.importProjectAsync("""<groupId>test</groupId>
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
    maven.assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT")

    maven.removeFromLocalRepository("test")

    maven.importProjectAsync()
    maven.assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT")
  }

  @Test
  fun testAttachingCorrectJavaDocsAndSources() = runBlocking {
    if (!maven.hasMavenInstallation()) return@runBlocking

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

    maven.removeFromLocalRepository("test")

    maven.importProjectAsync("""<groupId>test</groupId>
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
    maven.assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT")

    maven.downloadArtifacts()

    maven.assertModuleLibDep("project",
                       "Maven: test:foo:1-SNAPSHOT",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar!/")

    assertTrue(maven.repositoryPath.resolve("test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar").exists())
    assertTrue(maven.repositoryPath.resolve("test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar").exists())
    assertTrue(maven.repositoryPath.resolve("test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar").exists())
  }

  @Test
  fun testCorrectlyUpdateRootEntriesWithActualPathForSnapshotDependencies() = runBlocking {
    if (!maven.hasMavenInstallation()) return@runBlocking

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
    maven.removeFromLocalRepository("test")

    maven.importProjectAsync("""<groupId>test</groupId>
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
    maven.assertModuleLibDeps("project", "Maven: test:foo:1-SNAPSHOT")

    maven.downloadArtifacts()

    maven.assertModuleLibDep("project",
                       "Maven: test:foo:1-SNAPSHOT",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar!/")


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
    maven.removeFromLocalRepository("test")

    maven.assertModuleLibDep("project",
                       "Maven: test:foo:1-SNAPSHOT",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/foo/1-SNAPSHOT/foo-1-SNAPSHOT-javadoc.jar!/")
  }

  private fun deployArtifact(groupId: String, artifactId: String, version: String, tail: String = "") {
    val moduleName = "___$artifactId"

    maven.createProjectSubFile("$moduleName/src/main/java/Foo.java",
                         """
                           /**
                            * some doc
                            */
                           public class Foo { }
                           """.trimIndent())

    val m = maven.createModulePom(moduleName,
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
    maven.executeGoal(modulePath, "deploy")
  }

  private fun repositoriesSection(): String {
    return """<repositories>
  <repository>
    <id>internal</id>
    <url>file:///""" + FileUtil.toSystemIndependentName(remoteRepoDir!!.toString()) + "</url>\n" +
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
    <url>file:///""" + FileUtil.toSystemIndependentName(remoteRepoDir!!.toString()) + "</url>\n" +
           "  </snapshotRepository>\n" +
           "</distributionManagement>"
  }
}