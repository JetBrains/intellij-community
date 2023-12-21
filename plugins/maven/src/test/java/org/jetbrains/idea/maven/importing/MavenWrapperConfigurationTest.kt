// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.RunAll
import com.intellij.util.io.ZipUtil
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.junit.Test
import java.util.zip.ZipOutputStream

class MavenWrapperConfigurationTest : MavenImportingTestCase() {

  private val httpServerFixture = MavenHttpRepositoryServerFixture()
  private val httpServerFixtureForWrapper = MavenHttpRepositoryServerFixture()

  public override fun setUp() {
    super.setUp()
    httpServerFixture.setUp()
    httpServerFixtureForWrapper.setUp()
  }


  public override fun tearDown() {
    RunAll(
      { httpServerFixture.tearDown() },
      { httpServerFixtureForWrapper.tearDown() },
      { super.tearDown() }
    ).run()
  }

  @Test
  fun testShouldDownloadAndUseWrapperMavenSettings() = runBlocking {

    val helper = MavenCustomRepositoryHelper(dir, "local1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")

    httpServerFixture.startRepositoryFor(remoteRepoPath)
    repackCurrentMavenAndStartWrapper("""
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

    repositoryPath = localRepoPath
    val settingsXml = createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepoPath</localRepository>
       </settings>
       """.trimIndent())
    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)

    createProjectPom("""
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

    createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=${httpServerFixtureForWrapper.url()}/custom-maven.zip\n")

    MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings
      .setMavenHomeNoFire(MavenWrapper)
    removeFromLocalRepository("org/mytest/myartifact/")
    assertFalse(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)
    importProjectAsync()
    assertTrue(helper.getTestData("local1/org/mytest/myartifact/1.0/myartifact-1.0.jar").isFile)


  }

  private fun repackCurrentMavenAndStartWrapper(@Language(value = "XML", prefix = "<settings>",
                                                          suffix = "</settings>") newSettings: String) {
    val mavenHome = MavenDistributionsCache.resolveEmbeddedMavenHome();
    val repackDir = FileUtil.createTempDirectory("wrapper-repack", null, true)
    val wrapper = repackDir.resolve("wrapper")
    assertTrue(wrapper.mkdir())
    FileUtil.copyDir(mavenHome.mavenHome.toFile(), wrapper)
    FileUtil.writeToFile(wrapper.resolve("conf/settings.xml"), newSettings)
    val zipFile = repackDir.resolve("custom-maven.zip")
    ZipOutputStream(zipFile.outputStream()).use {
      ZipUtil.addDirToZipRecursively(it, zipFile, wrapper, "custom-maven", null, null)
    }
    httpServerFixtureForWrapper.startRepositoryFor(repackDir)
  }

}