// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.WaitFor;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MavenIndicesManagerTest extends MavenIndicesTestCase {
  private MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = new MavenIndicesTestFixture(myDir.toPath(), myProject);
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
    myIndicesFixture.getRepositoryHelper().addTestData("archetypes");
    File archetypes = myIndicesFixture.getRepositoryHelper().getTestData("archetypes");
    MavenProjectsManager.getInstance(myProject).getGeneralSettings().setLocalRepository(archetypes.getPath());
    myIndicesFixture.getIndicesManager().scheduleUpdateIndicesList(null);
    MavenIndexHolder indexHolder = myIndicesFixture.getIndicesManager().getIndex();
    MavenIndex localIndex = indexHolder.getLocalIndex();
    Assert.assertNotNull(localIndex);
    localIndex.updateOrRepair(true, MavenProjectsManager.getInstance(myProject).getGeneralSettings(), getMavenProgressIndicator());

    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-foobar:1.0");
  }

  @Test
  public void testAddingArchetypes() {
    MavenArchetype mavenArchetype = new MavenArchetype("myGroup", "myArtifact", "666", null, null);
    myIndicesFixture.getIndicesManager().addArchetype(mavenArchetype);

    assertArchetypeExists("myGroup:myArtifact:666");
  }

  @Test
  public void testAddingFilesToIndex() throws IOException, MavenProcessCanceledException {
    File localRepo = myIndicesFixture.getRepositoryHelper().getTestData("local2");

    MavenProjectsManager.getInstance(myProject).getGeneralSettings().setLocalRepository(localRepo.getPath());
    myIndicesFixture.getIndicesManager().scheduleUpdateIndicesList(null);
    MavenIndexHolder indexHolder = myIndicesFixture.getIndicesManager().getIndex();
    MavenIndex localIndex = indexHolder.getLocalIndex();

    //copy junit to repository
    File artifactDir = myIndicesFixture.getRepositoryHelper().getTestData("local1/junit");
    FileUtil.copyDir(artifactDir, localRepo);
    assertTrue(localIndex.getArtifactIds("junit").isEmpty());
    File artifactFile = myIndicesFixture.getRepositoryHelper().getTestData("local1/junit/junit/4.0/junit-4.0.pom");
    MavenIndicesManager.getInstance(myProject).addArtifactIndexAsync(null, artifactFile);
    new WaitFor(5000) {
      @Override
      protected boolean condition() {
        return !localIndex.getArtifactIds("junit").isEmpty();
      }
    };

    Set<String> versions = localIndex.getVersions("junit", "junit");
    assertFalse(versions.isEmpty());
    assertTrue(versions.contains("4.0"));
    assertFalse(versions.contains("3.8.2")); // copied but not used
  }

  private void assertArchetypeExists(String archetypeId) {
    Set<MavenArchetype> achetypes = myIndicesFixture.getIndicesManager().getArchetypes();
    List<String> actualNames = new ArrayList<>();
    for (MavenArchetype each : achetypes) {
      actualNames.add(each.groupId + ":" + each.artifactId + ":" + each.version);
    }
    assertTrue(actualNames.toString(), actualNames.contains(archetypeId));
  }
}
