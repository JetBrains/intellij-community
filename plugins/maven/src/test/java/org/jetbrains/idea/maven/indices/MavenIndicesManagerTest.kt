// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices

import com.intellij.maven.testFramework.fixtures.MavenIndicesTestFixture
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.indices.MavenIndicesManager.Companion.addArchetype
import org.jetbrains.idea.maven.indices.MavenIndicesManager.Companion.getInstance
import org.jetbrains.idea.maven.indices.MavenIndicesManager.MavenIndexerListener
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenIndicesManagerTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private var myIndicesFixture: MavenIndicesTestFixture? = null

  @BeforeEach
  fun setUp() {
    myIndicesFixture = MavenIndicesTestFixture(maven.dir, maven.project, maven.testRootDisposable)
    myIndicesFixture!!.setUp()
  }

  @AfterEach
  fun tearDown() {
    myIndicesFixture!!.tearDown()
  }

  @Test
  fun testDefaultArchetypes() {
    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-quickstart:RELEASE")
  }

  @Test
  fun testIndexedArchetypes() {
    //myIndicesFixture.getRepositoryHelper().addTestData("archetypes");
    //File archetypes = myIndicesFixture.getRepositoryHelper().getTestData("archetypes");
    //MavenProjectsManager.getInstance(getProject()).getGeneralSettings().setLocalRepository(archetypes.getPath());
    //myIndicesFixture.getIndicesManager().updateIndicesListSync();
    //var localIndex = myIndicesFixture.getIndicesManager().getCommonGavIndex();
    //Assert.assertNotNull(localIndex);
    //
    //assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-foobar:1.0");
  }

  @Test
  fun testAddingArchetypes() {
    val mavenArchetype = MavenArchetype("myGroup", "myArtifact", "666", null, null)
    addArchetype(mavenArchetype)

    assertArchetypeExists("myGroup:myArtifact:666")
  }

  @Test
  @OptIn(ExperimentalPathApi::class)
  fun testAddingFilesToIndex() = runBlocking{
    val localRepo = myIndicesFixture!!.repositoryHelper.getTestData("local2")

    MavenProjectsManager.getInstance(maven.project).getGeneralSettings().setLocalRepository(localRepo.toString())
    MavenSettingsCache.getInstance(maven.project).reloadAsync()
    myIndicesFixture!!.indicesManager.scheduleUpdateIndicesListAndWait()
    myIndicesFixture!!.indicesManager.waitForGavUpdateCompleted()
    val localIndex = myIndicesFixture!!.indicesManager.getCommonGavIndex()
    assertTrue(localIndex.getArtifactIds("junit").isEmpty())

    //copy junit to repository
    val artifactDir = myIndicesFixture!!.repositoryHelper.getTestData("local1/junit")
    artifactDir.copyToRecursively(localRepo, followLinks = false, overwrite = false)

    val artifactFile = myIndicesFixture!!.repositoryHelper.getTestData("local2/junit/junit/4.0/junit-4.0.pom")

    val latch = CountDownLatch(1)
    val addedFiles: MutableSet<File?> = ConcurrentHashMap.newKeySet<File?>()
    val failedToAddFiles: MutableSet<File?> = ConcurrentHashMap.newKeySet<File?>()
    ApplicationManager.getApplication().getMessageBus().connect(maven.testRootDisposable)
      .subscribe<MavenIndexerListener>(MavenIndicesManager.INDEXER_TOPIC, object : MavenIndexerListener {
        override fun gavIndexUpdated(repo: MavenRepositoryInfo, added: Set<File>, failedToAdd: Set<File>) {
          addedFiles.addAll(added)
          failedToAddFiles.addAll(failedToAdd)
          latch.countDown()
        }
      })

    val indexingScheduled = getInstance(maven.project).scheduleArtifactIndexing(null, artifactFile, localRepo.toString())
    assertTrue(indexingScheduled, "Failed to schedule indexing")

    latch.await(1, TimeUnit.MINUTES)

    assertEmpty(failedToAddFiles)
    assertSize(1, addedFiles)

    val indexedUri = Path.of(addedFiles.iterator().next()!!.absolutePath).toUri().toString()
    assertTrue(indexedUri.endsWith("local2/junit/junit/4.0/junit-4.0.pom"), "Junit pom not indexed")

    myIndicesFixture!!.indicesManager.waitForGavUpdateCompleted()
    myIndicesFixture!!.indicesManager.waitForLuceneUpdateCompleted()
    val versions = localIndex.getVersions("junit", "junit")
    assertFalse(versions.isEmpty())
    assertTrue(versions.contains("4.0"))
    assertFalse(versions.contains("3.8.2"))
  }

  private fun assertArchetypeExists(archetypeId: String?) {
    val achetypes = myIndicesFixture!!.archetypeManager.getArchetypes()
    val actualNames: MutableList<String?> = ArrayList<String?>()
    for (each in achetypes) {
      actualNames.add(each.groupId + ":" + each.artifactId)
    }
    val id = MavenId(archetypeId)
    assertTrue(actualNames.contains(id.groupId + ":" + id.artifactId), actualNames.toString())
  }
}
