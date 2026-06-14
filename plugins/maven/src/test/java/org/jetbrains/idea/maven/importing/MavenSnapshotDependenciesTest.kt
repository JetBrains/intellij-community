// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.fileContentEqual
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.removeFromLocalRepository
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectResolutionContributor
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import kotlin.io.path.isRegularFile

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSnapshotDependenciesTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private val httpServerFixture = MavenHttpRepositoryServerFixture()

  public @BeforeEach
  fun setUp() {
    httpServerFixture.setUp()
  }

  public @AfterEach
  fun tearDown() {
    runAll(
      { httpServerFixture.tearDown() },
    )
  }

  @Test
  fun `test incremental sync update snapshot dependency`() = runBlocking {
    val helper = MavenCustomRepositoryHelper(maven.dir, "local1")
    helper.addTestData("remote_snapshot/1", "remote")
    val remoteRepoPath = helper.getTestData("remote")
    val localRepoPath = helper.getTestData("local1")
    httpServerFixture.startRepositoryFor(remoteRepoPath.toString())
    maven.repositoryPath = localRepoPath
    val settingsXml = maven.createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepoPath</localRepository>
       </settings>
       """.trimIndent())
    maven.mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    maven.mavenGeneralSettings.isAlwaysUpdateSnapshots = true
    maven.removeFromLocalRepository("org/mytest/myartifact/")

    val jarSnapshot = "local1/org/mytest/myartifact/1.0-SNAPSHOT/myartifact-1.0-SNAPSHOT.jar"
    val jarVersion3 = "local1/org/mytest/myartifact/1.0-SNAPSHOT/myartifact-1.0-20240912.201701-3.jar"
    val jarVersion4 = "local1/org/mytest/myartifact/1.0-SNAPSHOT/myartifact-1.0-20240912.201843-4.jar"

    assertFalse(helper.getTestData(jarSnapshot).isRegularFile())
    assertFalse(helper.getTestData(jarVersion3).isRegularFile())
    assertFalse(helper.getTestData(jarVersion4).isRegularFile())
    maven.importProjectAsync("""
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

    assertTrue(helper.getTestData(jarSnapshot).isRegularFile())
    assertTrue(helper.getTestData(jarVersion3).isRegularFile())
    assertFalse(helper.getTestData(jarVersion4).isRegularFile())
    assertTrue(maven.fileContentEqual(helper.getTestData(jarSnapshot), helper.getTestData(jarVersion3)))

    helper.delete("remote")
    helper.addTestData("remote_snapshot/2", "remote")

    maven.updateAllProjects()

    assertTrue(helper.getTestData(jarSnapshot).isRegularFile())
    assertTrue(helper.getTestData(jarVersion3).isRegularFile())
    assertTrue(helper.getTestData(jarVersion4).isRegularFile())
    assertTrue(maven.fileContentEqual(helper.getTestData(jarSnapshot), helper.getTestData(jarVersion4)))
  }

  @Test
  fun `test maven cache is not cleared`() = runBlocking {
    val testCacheKey: Key<String> = Key.create("MavenProject.TEST_CACHE_KEY")
    val mavenProjectToCachedValue = mutableMapOf<MavenProject, String>()

    class MyMavenProjectResolutionContributor : MavenProjectResolutionContributor {
      override suspend fun onMavenProjectResolved(project: Project, mavenProject: MavenProject, embedder: MavenEmbedderWrapper) {
        mavenProject.putCachedValue(testCacheKey, "testValue")
      }
    }

    class MyTestMavenWorkspaceConfigurator : MavenWorkspaceConfigurator {
      override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
        context.mavenProjectsWithModules.forEach {
          val mavenProject = it.mavenProject
          val value = mavenProject.getCachedValue(testCacheKey) ?: return@forEach
          mavenProjectToCachedValue.put(mavenProject, value)
        }
      }
    }

    ExtensionTestUtil.addExtensions(MavenProjectResolutionContributor.EP_NAME, listOf(MyMavenProjectResolutionContributor()), maven.testRootDisposable)
    ExtensionTestUtil.addExtensions(MavenWorkspaceConfigurator.EXTENSION_POINT_NAME, listOf(MyTestMavenWorkspaceConfigurator()), maven.testRootDisposable)
    maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val mavenProjects = maven.projectsManager.projects
    assertSize(1, mavenProjects)
    val mavenProject = mavenProjects[0]
    assertEquals("testValue", mavenProjectToCachedValue[mavenProject])
    assertNull(mavenProject.getCachedValue(testCacheKey))
  }
}
