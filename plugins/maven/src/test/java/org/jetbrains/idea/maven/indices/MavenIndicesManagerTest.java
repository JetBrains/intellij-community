// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.WaitFor;
import org.jetbrains.idea.maven.model.MavenArchetype;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

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


  public void testEnsuringRemoteRepositoryIndex() {
    Pair<String, String> remote1 = Pair.create("id1", "http://foo/bar");
    Pair<String, String> remote2 = Pair.create("id1", "  http://foo\\bar\\\\  ");
    Pair<String, String> remote3 = Pair.create("id3", "http://foo\\bar\\baz");
    Pair<String, String> remote4 = Pair.create("id4", "http://foo/bar"); // same url
    Pair<String, String> remote5 = Pair.create("id4", "http://foo/baz"); // same id

    assertEquals(1, myIndicesFixture.getIndicesManager().ensureIndicesExist(Collections.singleton(remote1)).size());
    assertEquals(1, myIndicesFixture.getIndicesManager().ensureIndicesExist(asList(remote1, remote2)).size());
    assertEquals(2, myIndicesFixture.getIndicesManager().ensureIndicesExist(asList(remote1, remote2, remote3)).size());
    assertEquals(2, myIndicesFixture.getIndicesManager().ensureIndicesExist(asList(remote1, remote2, remote3, remote4)).size());
    assertEquals(3, myIndicesFixture.getIndicesManager().ensureIndicesExist(asList(remote1, remote2, remote3, remote4, remote5))
      .size());
  }

  public void testDefaultArchetypes() {
    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-quickstart:RELEASE");
  }

  public void testIndexedArchetypes() throws Exception {
    myIndicesFixture.getRepositoryHelper().addTestData("archetypes");
    myIndicesFixture.getIndicesManager()
      .createIndexForLocalRepo(myProject, myIndicesFixture.getRepositoryHelper().getTestData("archetypes"));

    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-foobar:1.0");
  }

  public void testIndexedArchetypesWithSeveralIndicesAfterReopening() throws Exception {
    myIndicesFixture.getRepositoryHelper().addTestData("archetypes");
    /*myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject,
                                                            Collections.singleton(Pair.create("id", "foo://bar.baz")));*/

    myIndicesFixture.getIndicesManager()
      .createIndexForLocalRepo(myProject, myIndicesFixture.getRepositoryHelper().getTestData("archetypes"));


    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-foobar:1.0");

    myIndicesFixture.tearDown();
    myIndicesFixture.setUp();

    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-foobar:1.0");
  }

  public void testAddingArchetypes() throws Exception {
    myIndicesFixture.getIndicesManager().addArchetype(new MavenArchetype("myGroup",
                                                                         "myArtifact",
                                                                         "666",
                                                                         null,
                                                                         null));

    assertArchetypeExists("myGroup:myArtifact:666");

    myIndicesFixture.tearDown();
    myIndicesFixture.setUp();

    assertArchetypeExists("myGroup:myArtifact:666");
  }

  public void testAddingFilesToIndex() throws IOException {
    File localRepo = myIndicesFixture.getRepositoryHelper().getTestData("local2");
    MavenIndex localIndex = myIndicesFixture.getIndicesManager()
      .createIndexForLocalRepo(myProject, localRepo);
    //copy junit to repository
    File artifactDir = myIndicesFixture.getRepositoryHelper().getTestData("local1/junit");
    FileUtil.copyDir(artifactDir, localRepo);
    assertFalse(localIndex.hasGroupId("junit"));
    File artifactFile = myIndicesFixture.getRepositoryHelper().getTestData("local1/junit/junit/4.0/junit-4.0.pom");
    MavenIndicesManager.getInstance(myProject).fixArtifactIndex(artifactFile, localRepo);
    new WaitFor(500) {
      @Override
      protected boolean condition() {
        return localIndex.hasGroupId("junit");
      }
    };

    assertTrue(localIndex.hasGroupId("junit"));
    assertTrue(localIndex.hasArtifactId("junit", "junit"));
    assertTrue(localIndex.hasVersion("junit", "junit", "4.0"));
    assertFalse(localIndex.hasVersion("junit", "junit", "3.8.2")); // copied but not used
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
