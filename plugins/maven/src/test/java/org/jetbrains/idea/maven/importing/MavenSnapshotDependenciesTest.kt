// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.utils.MavenHttpRepositoryServerFixture
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.runAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.junit.Test
import kotlin.io.path.isRegularFile

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

    assertFalse(helper.getTestData(jarSnapshot).isRegularFile())
    assertFalse(helper.getTestData(jarVersion3).isRegularFile())
    assertFalse(helper.getTestData(jarVersion4).isRegularFile())
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

    assertTrue(helper.getTestData(jarSnapshot).isRegularFile())
    assertTrue(helper.getTestData(jarVersion3).isRegularFile())
    assertFalse(helper.getTestData(jarVersion4).isRegularFile())
    assertTrue(fileContentEqual(helper.getTestData(jarSnapshot), helper.getTestData(jarVersion3)))

    helper.delete("remote")
    helper.addTestData("remote_snapshot/2", "remote")

    updateAllProjects()

    assertTrue(helper.getTestData(jarSnapshot).isRegularFile())
    assertTrue(helper.getTestData(jarVersion3).isRegularFile())
    assertTrue(helper.getTestData(jarVersion4).isRegularFile())
    assertTrue(fileContentEqual(helper.getTestData(jarSnapshot), helper.getTestData(jarVersion4)))
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

    class MyTestMavenImporter : MavenImporter("testPluginGroupID", "testPluginArtifactID") {
      override fun isApplicable(mavenProject: MavenProject?) = true

      override fun process(modifiableModelsProvider: IdeModifiableModelsProvider, module: Module, rootModel: MavenRootModelAdapter, mavenModel: MavenProjectsTree, mavenProject: MavenProject, changes: MavenProjectChanges, mavenProjectToModuleName: Map<MavenProject, String>, postTasks: List<MavenProjectsProcessorTask>) {
        val value = mavenProject.getCachedValue(testCacheKey)!!
        mavenProjectToCachedValue.put(mavenProject, value)
      }
    }

    ExtensionTestUtil.addExtensions(MavenProjectResolutionContributor.EP_NAME, listOf(MyMavenProjectResolutionContributor()), testRootDisposable)
    ExtensionTestUtil.addExtensions(MavenImporter.EXTENSION_POINT_NAME, listOf(MyTestMavenImporter()), testRootDisposable)
    importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val mavenProjects = projectsManager.projects
    assertSize(1, mavenProjects)
    val mavenProject = mavenProjects[0]
    assertEquals("testValue", mavenProjectToCachedValue[mavenProject])
    assertNull(mavenProject.getCachedValue(testCacheKey))
  }
}
