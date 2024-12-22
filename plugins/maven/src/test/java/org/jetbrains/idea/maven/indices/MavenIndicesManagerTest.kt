// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.maven.indices.MavenIndicesManager.Companion.addArchetype
import org.jetbrains.idea.maven.indices.MavenIndicesManager.Companion.getInstance
import org.jetbrains.idea.maven.indices.MavenIndicesManager.MavenIndexerListener
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MavenIndicesManagerTest : MavenIndicesTestCase() {
  public override fun runInDispatchThread(): Boolean {
    return true
  }

  private var myIndicesFixture: MavenIndicesTestFixture? = null

  override fun setUp() {
    super.setUp()
    myIndicesFixture = MavenIndicesTestFixture(dir, project, getTestRootDisposable())
    myIndicesFixture!!.setUp()
  }

  override fun tearDown() {
    try {
      myIndicesFixture!!.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
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
  fun testAddingFilesToIndex() {
    val localRepo = myIndicesFixture!!.repositoryHelper.getTestDataLegacy("local2")

    MavenProjectsManager.getInstance(project).getGeneralSettings().setLocalRepository(localRepo.path)
    myIndicesFixture!!.indicesManager.scheduleUpdateIndicesListAndWait()
    myIndicesFixture!!.indicesManager.waitForGavUpdateCompleted()
    val localIndex = myIndicesFixture!!.indicesManager.getCommonGavIndex()
    assertTrue(localIndex.getArtifactIds("junit").isEmpty())

    //copy junit to repository
    val artifactDir = myIndicesFixture!!.repositoryHelper.getTestDataLegacy("local1/junit")
    FileUtil.copyDir(artifactDir, localRepo)

    val artifactFile = myIndicesFixture!!.repositoryHelper.getTestDataLegacy("local2/junit/junit/4.0/junit-4.0.pom")

    val latch = CountDownLatch(1)
    val addedFiles: MutableSet<File?> = ConcurrentHashMap.newKeySet<File?>()
    val failedToAddFiles: MutableSet<File?> = ConcurrentHashMap.newKeySet<File?>()
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe<MavenIndexerListener>(MavenIndicesManager.INDEXER_TOPIC, object : MavenIndexerListener {
        override fun gavIndexUpdated(repo: MavenRepositoryInfo, added: Set<File>, failedToAdd: Set<File>) {
          addedFiles.addAll(added)
          failedToAddFiles.addAll(failedToAdd)
          latch.countDown()
        }
      })

    val indexingScheduled =
      getInstance(project).scheduleArtifactIndexing(null, artifactFile.toPath(), localRepo.absolutePath)
    assertTrue("Failed to schedule indexing", indexingScheduled)

    latch.await(1, TimeUnit.MINUTES)

    assertEmpty(failedToAddFiles)
    assertSize(1, addedFiles)

    val indexedUri = Path.of(addedFiles.iterator().next()!!.absolutePath).toUri().toString()
    assertTrue("Junit pom not indexed", indexedUri.endsWith("local2/junit/junit/4.0/junit-4.0.pom"))

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
    assertTrue(actualNames.toString(), actualNames.contains(id.groupId + ":" + id.artifactId))
  }
}
