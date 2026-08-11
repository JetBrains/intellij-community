// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.ZipUtil
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.removeFromLocalRepository
import com.intellij.maven.testFramework.fixtures.updateProjectSubFile
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipOutputStream
import kotlin.io.path.isRegularFile

@TestApplication
class MavenWrapperConfigurationTest {
  private val maven by mavenImportingFixture()

  private val httpServerFixture = MavenHttpRepositoryServerFixture()
  private val httpServerFixtureForWrapper = MavenHttpRepositoryServerFixture()

  @BeforeEach
  fun setUpServers() {
    httpServerFixture.setUp()
    httpServerFixtureForWrapper.setUp()
  }

  @AfterEach
  fun tearDownServers() {
    RunAll(
      { httpServerFixture.tearDown() },
      { httpServerFixtureForWrapper.tearDown() },
    ).run()
  }

  @Test
  fun testShouldUseAnotherWrapperIfPropertyChanged() = runBlocking {
    val repack1 = repackCurrentMaven("profile1.zip") {
      FileUtil.writeToFile(it.resolve("conf/settings.xml"), """
      <?xml version="1.0"?>
      <settings>
      <profiles>
        <profile>
          <id>profile1</id>
          <activation>
              <activeByDefault>true</activeByDefault>
          </activation>
        </profile>
      </profiles>
      </settings>

     """.trimIndent())
    }

    val repack2 = repackCurrentMaven(repack1.parentFile, "profile2.zip") {
      FileUtil.writeToFile(it.resolve("conf/settings.xml"), """
      <?xml version="1.0"?>
      <settings>
      <profiles>
        <profile>
          <id>profile2</id>
          <activation>
              <activeByDefault>true</activeByDefault>
          </activation>
        </profile>
      </profiles>
      </settings>

     """.trimIndent())
    }

    httpServerFixtureForWrapper.startRepositoryFor(repack1.parent)
    val wrapperProperties = maven.createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                                                 "distributionUrl=${httpServerFixtureForWrapper.url()}/profile1.zip\n")
    MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.generalSettings.setMavenHomeNoFire(MavenWrapper)

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
    """)

    assertOrderedElementsAreEqual(maven.projectsManager.projects[0].activatedProfilesIds.enabledProfiles, listOf("profile1"))

    maven.updateProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=${httpServerFixtureForWrapper.url()}/profile2.zip\n")
    maven.refreshFiles(listOf(wrapperProperties))
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>2</version>
    """)
    assertOrderedElementsAreEqual(maven.projectsManager.projects[0].activatedProfilesIds.enabledProfiles, listOf("profile2"))

  }

  @Test
  fun testShouldDownloadAndUseWrapperMavenSettings() = runBlocking {
    val helper = MavenCustomRepositoryHelper(maven.dir, "local1", "remote")
    val remoteRepoPath = helper.getTestData("remote")
    val localRepoPath = helper.getTestData("local1")

    httpServerFixture.startRepositoryFor(remoteRepoPath.toString())
    val newName = "custom-maven.zip"
    val repack = repackCurrentMaven(newName) {
      FileUtil.writeToFile(it.resolve("conf/settings.xml"), """
      <?xml version="1.0"?>
      <settings>
      <profiles>
        <profile>
          <activation>
              <activeByDefault>true</activeByDefault>
          </activation>
          <repositories>
            <repository>
              <id>my-http-repository</id>
              <name>my-http-repository</name>
              <url>${httpServerFixture.url()}</url>
            </repository>
          </repositories>
        </profile>
      </profiles>
      </settings>

     """.trimIndent())
    }
    httpServerFixtureForWrapper.startRepositoryFor(repack.parent)

    maven.repositoryPath = localRepoPath
    val settingsXml = maven.createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepoPath</localRepository>
       </settings>
       """.trimIndent())
    maven.mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                           <dependency>
                              <groupId>org.mytest</groupId>
                              <artifactId>myartifact</artifactId>
                              <version>1.0</version>
                           </dependency>
                       </dependencies>
                       """.trimIndent())

    val wrapperProperties = maven.createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=${httpServerFixtureForWrapper.url()}/custom-maven.zip\n")
    maven.refreshFiles(listOf(settingsXml, wrapperProperties))

    MavenWorkspaceSettingsComponent.getInstance(maven.project).settings.generalSettings.setMavenHomeNoFire(MavenWrapper)
    maven.removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
    maven.importProjectAsync()
    assertTrue(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isRegularFile())
  }

  /**
   * @newName - filenam
   * @return file with fresh new packed maven zip

   */
  private fun repackCurrentMaven(newName: String, beforePack: (File) -> Unit): File {
    val repackDir = FileUtil.createTempDirectory("wrapper-repack", null, true)
    return repackCurrentMaven(repackDir, newName, beforePack)
  }

  private fun repackCurrentMaven(parentDir: File, newName: String, beforePack: (File) -> Unit): File {
    val mavenHome = MavenDistributionsCache.resolveEmbeddedMavenHome();
    val wrapper = parentDir.resolve("$newName-repack-dir")
    assertTrue(wrapper.mkdir())
    FileUtil.copyDir(mavenHome.mavenHome.toFile(), wrapper)
    beforePack(wrapper)
    val zipFile = parentDir.resolve(newName)
    ZipOutputStream(zipFile.outputStream()).use {
      ZipUtil.addDirToZipRecursively(it, zipFile, wrapper, newName.removeSuffix(".zip"), null, null)
    }
    return wrapper
  }

}
