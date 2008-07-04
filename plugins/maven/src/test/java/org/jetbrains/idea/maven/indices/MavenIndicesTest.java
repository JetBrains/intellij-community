package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;

import java.io.File;
import java.io.FileWriter;

public class MavenIndicesTest extends MavenImportingTestCase {
  private MavenWithDataTestFixture myDataTestFixture;
  private MavenIndices myIndices;
  private MavenEmbedder myEmbedder;
  private File myIndicesDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myDataTestFixture = new MavenWithDataTestFixture(myDir);
    myDataTestFixture.setUp();

    initIndices();
  }

  @Override
  protected void tearDown() throws Exception {
    shutdownIndices();
    super.tearDown();
  }

  private void initIndices() throws Exception {
    initIndices("indices");
  }

  private void initIndices(String relativeDir) {
    myEmbedder = MavenEmbedderFactory.createEmbedderForExecute(getMavenCoreSettings()).getEmbedder();
    myIndicesDir = new File(myDir, relativeDir);
    myIndices = new MavenIndices(myEmbedder, myIndicesDir);
  }

  private void shutdownIndices() throws MavenEmbedderException {
    myIndices.close();
    myEmbedder.stop();
  }

  public void testAddingLocal() throws Exception {
    MavenIndex i = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "junit");
  }

  public void testAddingSeveral() throws Exception {
    MavenIndex i1 = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.update(i1, new EmptyProgressIndicator());
    myIndices.update(i2, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "junit", "jmock");
  }

  public void testAddingWithoutUpdate() throws Exception {
    myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertTrue(myIndices.getGroupIds().isEmpty());
  }

  public void testUpdatingLocalClearsPreviousIndex() throws Exception {
    MavenIndex i = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);

    myIndices.update(i, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "junit");

    myIndices.change(i, myDataTestFixture.getTestDataPath("local2"));
    myIndices.update(i, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "jmock");
  }

  public void testChanging() throws Exception {
    MavenIndex i = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);

    myIndices.update(i, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "junit");

    myIndices.change(i, myDataTestFixture.getTestDataPath("local2"));
    myIndices.update(i, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "jmock");
  }

  public void testSavingOnChange() throws Exception {
    MavenIndex i = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);

    myIndices.change(i, myDataTestFixture.getTestDataPath("local2"));

    shutdownIndices();
    initIndices();

    assertEquals(1, myIndices.getIndices().size());
    assertEquals("local2", myIndices.getIndices().get(0).getRepositoryFile().getName());
  }

  public void testAddingRemote() throws Exception {
    MavenIndex i = myIndices.add("file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.update(i, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "junit");
  }

  public void testUpdatingRemote() throws Exception {
    // NexusIndexer holds 'timestamp' file and we cannot remove directory in tearDown
    if (ignore()) {
      System.out.println("Don't forget to unignore the test if you change MavenIndex class");
      return;
    }
    
    MavenIndex i = myIndices.add("file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.update(i, new EmptyProgressIndicator());

    //shouldn't throw 'The existing index is for repository [remote] and not for repository [xxx]'
    myIndices.update(i, new EmptyProgressIndicator());
  }

  public void testChangingWithSameID() throws Exception {
    MavenIndex i = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());

    myIndices.change(i, myDataTestFixture.getTestDataPath("local2"));
    myIndices.update(i, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "jmock");
  }

  public void testRemoving() throws Exception {
    MavenIndex i = myIndices.add("file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.update(i, new EmptyProgressIndicator());

    myIndices.remove(i);
    assertTrue(myIndices.getGroupIds().isEmpty());
  }

  public void testClearIndexAfterRemoving() throws Exception {
    MavenIndex i = myIndices.add("file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.update(i, new EmptyProgressIndicator());

    myIndices.remove(i);
    i = myIndices.add("file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    assertTrue(myIndices.getGroupIds().isEmpty());
  }

  public void testAddingInAbsenseOfParentDirectories() throws Exception {
    String subDir = "subDir1/subDir2/index";
    initIndices(subDir);
    myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
  }

  public void testClearingIndexDirOnLoadError() throws Exception {
    MavenIndex i = myIndices.add(myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    shutdownIndices();

    FileWriter w = new FileWriter(new File(i.getDir(), MavenIndex.INDEX_INFO_FILE));
    w.write("bad content");
    w.close();

    initIndices();

    assertTrue(myIndices.getIndices().isEmpty());
    assertFalse(i.getDir().exists());
  }

  public void testDoNotClearAlreadyLoadedIndexesOnLoadError() throws Exception {
    myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    shutdownIndices();

    FileWriter w = new FileWriter(new File(i2.getDir(), MavenIndex.INDEX_INFO_FILE));
    w.write("bad content");
    w.close();

    initIndices();

    assertEquals(1, myIndices.getIndices().size());
    assertEquals("local1", myIndices.getIndices().get(0).getRepositoryFile().getName());
  }

  public void testLoadingAndRepairingIndexIfCachesAreBroken() throws Exception {
    MavenIndex i1 = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.update(i1, new EmptyProgressIndicator());
    myIndices.update(i2, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "junit", "jmock");

    shutdownIndices();

    File cachesDir = i1.getCurrentDataDir();
    File groupIds = new File(cachesDir, "groupIds.dat");
    assertTrue(groupIds.exists());

    FileWriter w = new FileWriter(groupIds);
    w.write("bad content");
    w.close();

    initIndices();

    assertEquals(2, myIndices.getIndices().size());
    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "jmock");
  }
  
  public void testAddingIndexWithExistingDirectoryDoesNotThrowException() throws Exception {
    MavenIndex i = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());

    shutdownIndices();

    initIndices();
    i = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());
  }

  public void testGettingArtifactInfos() throws Exception {
    MavenIndex i1 = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.update(i1, new EmptyProgressIndicator());
    myIndices.update(i2, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "junit", "jmock");

    assertUnorderedElementsAreEqual(myIndices.getArtifactIds("junit"), "junit");
    assertUnorderedElementsAreEqual(myIndices.getArtifactIds("jmock"), "jmock");
    assertUnorderedElementsAreEqual(myIndices.getArtifactIds("unknown"));

    assertUnorderedElementsAreEqual(myIndices.getVersions("junit", "junit"), "3.8.1", "3.8.2", "4.0");
    assertUnorderedElementsAreEqual(myIndices.getVersions("junit", "jmock"));
    assertUnorderedElementsAreEqual(myIndices.getVersions("unknown", "unknown"));
  }

  public void testGettingArtifactInfosFromUnUpdatedRepositories() throws Exception {
    myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertUnorderedElementsAreEqual(myIndices.getGroupIds()); // shouldn't throw
  }

  public void testGettingArtifactInfosAfterReload() throws Exception {
    MavenIndex i = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());

    shutdownIndices();
    initIndices();

    assertUnorderedElementsAreEqual(myIndices.getGroupIds(), "junit");
  }

  public void testHasArtifactInfo() throws Exception {
    MavenIndex i1 = myIndices.add(myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.update(i1, new EmptyProgressIndicator());
    myIndices.update(i2, new EmptyProgressIndicator());

    assertTrue(myIndices.hasGroupId("junit"));
    assertTrue(myIndices.hasGroupId("jmock"));
    assertFalse(myIndices.hasGroupId("xxx"));

    assertTrue(myIndices.hasArtifactId("junit", "junit"));
    assertTrue(myIndices.hasArtifactId("jmock", "jmock"));
    assertFalse(myIndices.hasArtifactId("junit", "jmock"));

    assertTrue(myIndices.hasVersion("junit", "junit", "4.0"));
    assertTrue(myIndices.hasVersion("jmock", "jmock", "1.0.0"));
    assertFalse(myIndices.hasVersion("junit", "junit", "666"));
  }
}
