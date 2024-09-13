// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.testFramework.common.runAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.junit.Test

class MavenSnapshotDependenciesTest : MavenMultiVersionImportingTestCase() {
  private val httpServerFixture = MavenHttpRepositoryServerFixture()

  public override fun setUp() {
    super.setUp()
    httpServerFixture.setUp()
  }

  public override fun tearDown() {
    runAll(
      { httpServerFixture.tearDown() },
      { super.tearDown() },
    )
  }

  @Test
  fun `test incremental sync update snapshot dependency`() = runBlocking {
    needFixForMaven4() // TODO: fix for Maven 4
    val helper = MavenCustomRepositoryHelper(dir, "local1")
    helper.addTestData("remote_snapshot/1", "remote")
    val remoteRepoPath = helper.getTestDataPath("remote")
    val localRepoPath = helper.getTestDataPath("local1")
    httpServerFixture.startRepositoryFor(remoteRepoPath)
    repositoryPath = localRepoPath
    val settingsXml = createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepoPath</localRepository>
       </settings>
       """.trimIndent())
    mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    mavenGeneralSettings.isAlwaysUpdateSnapshots = true
    removeFromLocalRepository("org/mytest/myartifact/")

    val jarSnapshot = "local1/org/mytest/myartifact/1.0-SNAPSHOT/myartifact-1.0-SNAPSHOT.jar"
    val jarVersion3 = "local1/org/mytest/myartifact/1.0-SNAPSHOT/myartifact-1.0-20240912.201701-3.jar"
    val jarVersion4 = "local1/org/mytest/myartifact/1.0-SNAPSHOT/myartifact-1.0-20240912.201843-4.jar"

    assertFalse(helper.getTestData(jarSnapshot).isFile)
    assertFalse(helper.getTestData(jarVersion3).isFile)
    assertFalse(helper.getTestData(jarVersion4).isFile)
    importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>org.mytest</groupId>
                           <artifactId>myartifact</artifactId>
                           <version>1.0-SNAPSHOT</version>
                         </dependency>
                       </dependencies>
                       <repositories>
                           <repository>
                             <id>my-http-repository</id>
                             <name>my-http-repository</name>
                             <url>${httpServerFixture.url()}</url>
                           </repository>
                       </repositories>
                       """.trimIndent())

    assertTrue(helper.getTestData(jarSnapshot).isFile)
    assertTrue(helper.getTestData(jarVersion3).isFile)
    assertFalse(helper.getTestData(jarVersion4).isFile)
    assertTrue(fileContentEqual(helper.getTestData(jarSnapshot), helper.getTestData(jarVersion3)))

    helper.delete("remote")
    helper.addTestData("remote_snapshot/2", "remote")

    updateAllProjects()

    assertTrue(helper.getTestData(jarSnapshot).isFile)
    assertTrue(helper.getTestData(jarVersion3).isFile)
    assertTrue(helper.getTestData(jarVersion4).isFile)
    assertTrue(fileContentEqual(helper.getTestData(jarSnapshot), helper.getTestData(jarVersion4)))
  }

}