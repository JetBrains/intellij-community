/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.Pair;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.File;
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
    myIndicesFixture = new MavenIndicesTestFixture(myDir, myProject);
    myIndicesFixture.setUp();
    MavenServerManager.getInstance().setUseMaven2(true);
  }

  @Override
  protected void tearDown() throws Exception {
    myIndicesFixture.tearDown();
    super.tearDown();
  }

  public void testEnsuringLocalRepositoryIndex() throws Exception {
    File dir1 = myIndicesFixture.getRepositoryHelper().getTestData("dir/foo");
    File dir2 = myIndicesFixture.getRepositoryHelper().getTestData("dir\\foo");
    File dir3 = myIndicesFixture.getRepositoryHelper().getTestData("dir\\foo\\");
    File dir4 = myIndicesFixture.getRepositoryHelper().getTestData("dir/bar");

    List<MavenIndex> indices1 = myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, dir1,
                                                                                        Collections.<Pair<String, String>>emptyList());
    assertEquals(1, indices1.size());
    assertTrue(myIndicesFixture.getIndicesManager().getIndices().contains(indices1.get(0)));

    assertEquals(indices1, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, dir2,
                                                                                   Collections.<Pair<String, String>>emptyList()));
    assertEquals(indices1, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, dir3,
                                                                                   Collections.<Pair<String, String>>emptyList()));

    List<MavenIndex> indices2 = myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, dir4,
                                                                                        Collections.<Pair<String, String>>emptyList());
    assertFalse(indices1.get(0).equals(indices2.get(0)));
  }

  public void testEnsuringRemoteRepositoryIndex() throws Exception {
    File local = myIndicesFixture.getRepositoryHelper().getTestData("dir");
    Pair<String, String> remote1 = Pair.create("id1", "http://foo/bar");
    Pair<String, String> remote2 = Pair.create("id1", "  http://foo\\bar\\\\  ");
    Pair<String, String> remote3 = Pair.create("id3", "http://foo\\bar\\baz");
    Pair<String, String> remote4 = Pair.create("id4", "http://foo/bar"); // same url
    Pair<String, String> remote5 = Pair.create("id4", "http://foo/baz"); // same id

    assertEquals(2, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, local, Collections.singleton(remote1)).size());
    assertEquals(2, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, local, asList(remote1, remote2)).size());
    assertEquals(3, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, local, asList(remote1, remote2, remote3)).size());
    assertEquals(3, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, local, asList(remote1, remote2, remote3, remote4)).size());
    assertEquals(4, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, local, asList(remote1, remote2, remote3, remote4, remote5)).size());
  }

  public void testDefaultArchetypes() throws Exception {
    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-quickstart:RELEASE");
  }

  public void testIndexedArchetypes() throws Exception {
    myIndicesFixture.getRepositoryHelper().addTestData("archetypes");
    myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, myIndicesFixture.getRepositoryHelper().getTestData("archetypes"),
                                                            Collections.<Pair<String, String>>emptyList());

    assertArchetypeExists("org.apache.maven.archetypes:maven-archetype-foobar:1.0");
  }

  public void testIndexedArchetypesWithSeveralIndicesAfterReopening() throws Exception {
    myIndicesFixture.getRepositoryHelper().addTestData("archetypes");
    myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, myIndicesFixture.getRepositoryHelper().getTestData("archetypes"),
                                                            Collections.singleton(Pair.create("id", "foo://bar.baz")));

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

  private void assertArchetypeExists(String archetypeId) {
    Set<MavenArchetype> achetypes = myIndicesFixture.getIndicesManager().getArchetypes();
    List<String> actualNames = new ArrayList<>();
    for (MavenArchetype each : achetypes) {
      actualNames.add(each.groupId + ":" + each.artifactId + ":" + each.version);
    }
    assertTrue(actualNames.toString(), actualNames.contains(archetypeId));
  }
}
