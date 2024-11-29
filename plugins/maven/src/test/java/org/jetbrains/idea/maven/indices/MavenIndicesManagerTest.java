// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MavenIndicesManagerTest extends MavenIndicesTestCase {
  @Override
  public boolean runInDispatchThread() {
    return true;
  }

  private MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = new MavenIndicesTestFixture(getDir().toPath(), getProject(), getTestRootDisposable());
    myIndicesFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIndicesFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Test
  public void testDefaultArchetypes() {
    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-quickstart:RELEASE");
  }

  @Test
  public void testIndexedArchetypes() throws Exception {

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
  public void testAddingArchetypes() {
    MavenArchetype mavenArchetype = new MavenArchetype("myGroup", "myArtifact", "666", null, null);
    MavenIndicesManager.addArchetype(mavenArchetype);

    assertArchetypeExists("myGroup:myArtifact:666");
  }

  @Test
  public void testAddingFilesToIndex() throws IOException, MavenProcessCanceledException, InterruptedException {
    File localRepo = myIndicesFixture.getRepositoryHelper().getTestData("local2");

    MavenProjectsManager.getInstance(getProject()).getGeneralSettings().setLocalRepository(localRepo.getPath());
    myIndicesFixture.getIndicesManager().scheduleUpdateIndicesListAndWait();
    myIndicesFixture.getIndicesManager().waitForGavUpdateCompleted();
    MavenGAVIndex localIndex = myIndicesFixture.getIndicesManager().getCommonGavIndex();
    assertTrue(localIndex.getArtifactIds("junit").isEmpty());

    //copy junit to repository
    File artifactDir = myIndicesFixture.getRepositoryHelper().getTestData("local1/junit");
    FileUtil.copyDir(artifactDir, localRepo);

    File artifactFile = myIndicesFixture.getRepositoryHelper().getTestData("local2/junit/junit/4.0/junit-4.0.pom");

    var latch = new CountDownLatch(1);
    Set<File> addedFiles = ConcurrentHashMap.newKeySet();
    Set<File> failedToAddFiles = ConcurrentHashMap.newKeySet();
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(MavenIndicesManager.INDEXER_TOPIC, new MavenIndicesManager.MavenIndexerListener() {
        @Override
        public void gavIndexUpdated(MavenRepositoryInfo repo, Set<? extends File> added, Set<? extends File> failedToAdd) {
          addedFiles.addAll(added);
          failedToAddFiles.addAll(failedToAdd);
          latch.countDown();
        }
      });

    var indexingScheduled =
      MavenIndicesManager.getInstance(getProject()).scheduleArtifactIndexing(null, artifactFile.toPath(), localRepo.getAbsolutePath());
    assertTrue("Failed to schedule indexing", indexingScheduled);

    latch.await(1, TimeUnit.MINUTES);

    assertEmpty(failedToAddFiles);
    assertSize(1, addedFiles);

    String indexedUri = Path.of(addedFiles.iterator().next().getAbsolutePath()).toUri().toString();
    assertTrue("Junit pom not indexed", indexedUri.endsWith("local2/junit/junit/4.0/junit-4.0.pom"));

    myIndicesFixture.getIndicesManager().waitForGavUpdateCompleted();
    myIndicesFixture.getIndicesManager().waitForLuceneUpdateCompleted();
    Set<String> versions = localIndex.getVersions("junit", "junit");
    assertFalse(versions.isEmpty());
    assertTrue(versions.contains("4.0"));
    assertFalse(versions.contains("3.8.2"));
  }

  private void assertArchetypeExists(String archetypeId) {
    Set<MavenArchetype> achetypes = myIndicesFixture.getArchetypeManager().getArchetypes();
    List<String> actualNames = new ArrayList<>();
    for (MavenArchetype each : achetypes) {
      actualNames.add(each.groupId + ":" + each.artifactId);
    }
    MavenId id = new MavenId(archetypeId);
    assertTrue(actualNames.toString(), actualNames.contains(id.getGroupId() + ":" + id.getArtifactId()));
  }
}
