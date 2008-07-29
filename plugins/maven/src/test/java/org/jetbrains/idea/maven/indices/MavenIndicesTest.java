package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;

import java.io.File;
import java.io.FileWriter;

public class MavenIndicesTest extends MavenImportingTestCase {
  private MavenCustomRepositoryTestFixture myRepositoryFixture;
  private MavenIndices myIndices;
  private MavenEmbedder myEmbedder;
  private File myIndicesDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myRepositoryFixture = new MavenCustomRepositoryTestFixture(myDir);
    myRepositoryFixture.setUp();

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

  public void testCreatingAndUpdatingLocal() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");
  }

  public void testCreatingSeveral() throws Exception {
    MavenIndex i1 = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myRepositoryFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.update(i1, new EmptyProgressIndicator());
    myIndices.update(i2, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i1.getGroupIds(), "junit");
    assertUnorderedElementsAreEqual(i2.getGroupIds(), "jmock");
  }

  public void testAddingWithoutUpdate() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertTrue(i.getGroupIds().isEmpty());
  }

  public void testUpdatingLocalClearsPreviousIndex() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);

    myIndices.update(i, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");

    myRepositoryFixture.delete("local1");
    myRepositoryFixture.copy("local2", "local1");

    myIndices.update(i, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(i.getGroupIds(), "jmock");
  }

  public void testAddingRemote() throws Exception {
    MavenIndex i = myIndices.add("file:///" + myRepositoryFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.update(i, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");
  }

  public void testUpdatingRemote() throws Exception {
    // NexusIndexer holds 'timestamp' file and we cannot remove directory in tearDown
    if (ignore()) {
      System.out.println("Don't forget to unignore the test if you change MavenIndex class");
      return;
    }

    MavenIndex i = myIndices.add("file:///" + myRepositoryFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.update(i, new EmptyProgressIndicator());

    //shouldn't throw 'The existing index is for repository [remote] and not for repository [xxx]'
    myIndices.update(i, new EmptyProgressIndicator());
  }

  //public void testRemoving() throws Exception {
  //  MavenIndex i = myIndices.add("file:///" + myRepositoryFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
  //  myIndices.update(i, new EmptyProgressIndicator());
  //  myIndices.remove(i);
  //  assertFalse(i.getDir().exists());
  //}

  //public void testClearIndexAfterRemoving() throws Exception {
  //  MavenIndex i = myIndices.add("file:///" + myRepositoryFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
  //  myIndices.update(i, new EmptyProgressIndicator());
  //
  //  myIndices.remove(i);
  //  i = myIndices.add("file:///" + myRepositoryFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
  //  assertTrue(i.getGroupIds().isEmpty());
  //}

  public void testAddingInAbsenseOfParentDirectories() throws Exception {
    String subDir = "subDir1/subDir2/index";
    initIndices(subDir);
    myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
  }

  public void testClearingIndexDirOnLoadError() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    shutdownIndices();

    FileWriter w = new FileWriter(new File(i.getDir(), MavenIndex.INDEX_INFO_FILE));
    w.write("bad content");
    w.close();

    initIndices();

    assertTrue(myIndices.getIndices().isEmpty());
    assertFalse(i.getDir().exists());
  }

  public void testDoNotClearAlreadyLoadedIndexesOnLoadError() throws Exception {
    myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myRepositoryFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    shutdownIndices();

    FileWriter w = new FileWriter(new File(i2.getDir(), MavenIndex.INDEX_INFO_FILE));
    w.write("bad content");
    w.close();

    initIndices();

    assertEquals(1, myIndices.getIndices().size());
    assertEquals("local1", myIndices.getIndices().get(0).getRepositoryFile().getName());
  }

  public void testLoadingIndexIfCachesAreBroken() throws Exception {
    MavenIndex i1 = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myRepositoryFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.update(i1, new EmptyProgressIndicator());
    myIndices.update(i2, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i1.getGroupIds(), "junit");
    assertUnorderedElementsAreEqual(i2.getGroupIds(), "jmock");

    shutdownIndices();

    File cachesDir = i1.getCurrentDataDir();
    File groupIds = new File(cachesDir, "groupIds.dat");
    assertTrue(groupIds.exists());

    FileWriter w = new FileWriter(groupIds);
    w.write("bad content");
    w.close();

    initIndices();

    assertEquals(2, myIndices.getIndices().size());

    assertTrue(myIndices.getIndices().get(0).getGroupIds().isEmpty());
    assertEquals(-1, myIndices.getIndices().get(0).getUpdateTimestamp());

    assertUnorderedElementsAreEqual(myIndices.getIndices().get(1).getGroupIds(), "jmock");
  }

  public void testAddingIndexWithExistingDirectoryDoesNotThrowException() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());

    shutdownIndices();

    initIndices();
    i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());
  }

  public void testSavingFailureMessage() throws Exception {
    MavenIndex i = myIndices.add("xxx", MavenIndex.Kind.REMOTE);
    myIndices.update(i, new EmptyProgressIndicator());

    String message = i.getFailureMessage();
    assertNotNull(message);

    shutdownIndices();
    initIndices();
    
    assertEquals(message, myIndices.getIndices().get(0).getFailureMessage());
  }

  public void testGettingArtifactInfos() throws Exception {
    myRepositoryFixture.copy("local2", "local1");
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit", "jmock");

    assertUnorderedElementsAreEqual(i.getArtifactIds("junit"), "junit");
    assertUnorderedElementsAreEqual(i.getArtifactIds("jmock"), "jmock");
    assertUnorderedElementsAreEqual(i.getArtifactIds("unknown"));

    assertUnorderedElementsAreEqual(i.getVersions("junit", "junit"), "3.8.1", "3.8.2", "4.0");
    assertUnorderedElementsAreEqual(i.getVersions("junit", "jmock"));
    assertUnorderedElementsAreEqual(i.getVersions("unknown", "unknown"));
  }

  public void testGettingArtifactInfosFromNotUpdatedRepositories() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertUnorderedElementsAreEqual(i.getGroupIds()); // shouldn't throw
  }

  public void testGettingArtifactInfosAfterReload() throws Exception {
    myIndices.update(myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL),
                     new EmptyProgressIndicator());

    shutdownIndices();
    initIndices();

    assertUnorderedElementsAreEqual(myIndices.getIndices().get(0).getGroupIds(), "junit");
  }

  public void testHasArtifactInfo() throws Exception {
    myRepositoryFixture.copy("local2", "local1");
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.update(i, new EmptyProgressIndicator());

    assertTrue(i.hasGroupId("junit"));
    assertTrue(i.hasGroupId("jmock"));
    assertFalse(i.hasGroupId("xxx"));

    assertTrue(i.hasArtifactId("junit", "junit"));
    assertTrue(i.hasArtifactId("jmock", "jmock"));
    assertFalse(i.hasArtifactId("junit", "jmock"));

    assertTrue(i.hasVersion("junit", "junit", "4.0"));
    assertTrue(i.hasVersion("jmock", "jmock", "1.0.0"));
    assertFalse(i.hasVersion("junit", "junit", "666"));
  }
}
