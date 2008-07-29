package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.MavenImportingTestCase;

import static java.util.Arrays.asList;
import java.util.Collections;
import java.util.List;
import java.io.File;

public class MavenIndicesManagerTest extends MavenImportingTestCase {
  private MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = new MavenIndicesTestFixture(myDir, myProject);
    myIndicesFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myIndicesFixture.tearDown();
    super.tearDown();
  }

  public void testEnsuringLocalRepositoryIndex() throws Exception {
    File dir1 = myIndicesFixture.getRepositoryFixture().getTestData("dir/foo");
    File dir2 = myIndicesFixture.getRepositoryFixture().getTestData("dir\\foo");
    File dir3 = myIndicesFixture.getRepositoryFixture().getTestData("dir\\foo\\");
    File dir4 = myIndicesFixture.getRepositoryFixture().getTestData("dir/bar");

    List<MavenIndex> indices1 = myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, dir1, Collections.<String>emptySet());
    assertEquals(1, indices1.size());
    assertTrue(myIndicesFixture.getIndicesManager().getIndices().contains(indices1.get(0)));

    assertEquals(indices1, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, dir2, Collections.<String>emptySet()));
    assertEquals(indices1, myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, dir3, Collections.<String>emptySet()));

    List<MavenIndex> indices2 = myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, dir4, Collections.<String>emptySet());
    assertFalse(indices1.get(0).equals(indices2.get(0)));
  }

  public void testEnsuringRemoteRepositoryIndex() throws Exception {
    File local = myIndicesFixture.getRepositoryFixture().getTestData("dir");
    String remote1 = "http://foo/bar";
    String remote2 = "  http://foo\\bar\\\\  ";
    String remote3 = "http://foo\\bar\\baz";

    List<MavenIndex> indices1 = myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, local, Collections.singleton(remote1));
    assertEquals(2, indices1.size());

    List<MavenIndex> indices2 = myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, local, asList(remote1, remote2));
    assertEquals(2, indices2.size());

    List<MavenIndex> indices3 = myIndicesFixture.getIndicesManager().ensureIndicesExist(myProject, local, asList(remote1, remote2, remote3));
    assertEquals(3, indices3.size());
  }
}