package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MavenIndicesTest extends MavenImportingTestCase {
  private MavenCustomRepositoryTestFixture myRepositoryFixture;
  private MavenIndices myIndices;
  private MavenEmbedder myEmbedder;
  private File myIndicesDir;
  private boolean isBroken;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myRepositoryFixture = new MavenCustomRepositoryTestFixture(myDir, "local1", "local2", "remote");
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
    myIndices = new MavenIndices(myEmbedder, myIndicesDir, new MavenIndex.IndexListener() {
      public void indexIsBroken(MavenIndex index) {
        isBroken = true;
      }
    });
  }

  private void shutdownIndices() throws MavenEmbedderException {
    myIndices.close();
    myEmbedder.stop();
  }

  public void testCreatingAndUpdatingLocal() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");
  }

  public void testCreatingSeveral() throws Exception {
    MavenIndex i1 = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = myIndices.add(myRepositoryFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i1, true, new EmptyProgressIndicator());
    myIndices.updateOrRepair(i2, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i1.getGroupIds(), "junit");
    assertUnorderedElementsAreEqual(i2.getGroupIds(), "jmock");
  }

  public void testAddingWithoutUpdate() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    assertTrue(i.getGroupIds().isEmpty());
  }

  public void testUpdatingLocalClearsPreviousIndex() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);

    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");

    myRepositoryFixture.delete("local1");
    myRepositoryFixture.copy("local2", "local1");

    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(i.getGroupIds(), "jmock");
  }

  public void testAddingRemote() throws Exception {
    MavenIndex i = myIndices.add("file:///" + myRepositoryFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i.getGroupIds(), "junit");
  }

  public void testUpdatingRemote() throws Exception {
    MavenIndex i = myIndices.add("file:///" + myRepositoryFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());

    //shouldn't throw 'The existing index is for repository [remote] and not for repository [xxx]'
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());
  }

  public void testDoNotAddSameIndexTwice() throws Exception {
    MavenIndex local = myIndices.add(myRepositoryFixture.getTestDataPath("foo"), MavenIndex.Kind.LOCAL);

    assertSame(local, myIndices.add(myRepositoryFixture.getTestDataPath("FOO"), MavenIndex.Kind.LOCAL));
    assertSame(local, myIndices.add(myRepositoryFixture.getTestDataPath("foo") + "/\\", MavenIndex.Kind.LOCAL));
    assertSame(local, myIndices.add("  " + myRepositoryFixture.getTestDataPath("foo") + "  ", MavenIndex.Kind.LOCAL));

    MavenIndex remote = myIndices.add("http://foo.bar", MavenIndex.Kind.REMOTE);

    assertSame(remote, myIndices.add("HTTP://FOO.BAR", MavenIndex.Kind.REMOTE));
    assertSame(remote, myIndices.add("http://foo.bar/\\", MavenIndex.Kind.REMOTE));
    assertSame(remote, myIndices.add("  http://foo.bar  ", MavenIndex.Kind.REMOTE));
  }

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
    myIndices.updateOrRepair(i1, true, new EmptyProgressIndicator());
    myIndices.updateOrRepair(i2, true, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(i1.getGroupIds(), "junit");
    assertUnorderedElementsAreEqual(i2.getGroupIds(), "jmock");

    shutdownIndices();
    damageFile(i1, "groupIds.dat", true);
    initIndices();

    assertEquals(2, myIndices.getIndices().size());

    assertTrue(myIndices.getIndices().get(0).getGroupIds().isEmpty());
    assertUnorderedElementsAreEqual(myIndices.getIndices().get(1).getGroupIds(), "jmock");

    myIndices.updateOrRepair(myIndices.getIndices().get(0), false, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(myIndices.getIndices().get(0).getGroupIds(), "junit");
  }

  public void testDoNotLoadSameIndexTwice() throws Exception {
    MavenIndex index = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    File dir = index.getDir();
    shutdownIndices();

    File copy = new File(dir.getParentFile(), "INDEX_COPY");
    FileUtil.copyDir(dir, copy);

    initIndices();

    assertEquals(1, myIndices.getIndices().size());
    assertFalse(copy.exists());
  }

  public void testAddingIndexWithExistingDirectoryDoesNotThrowException() throws Exception {
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());

    shutdownIndices();

    initIndices();
    i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());
  }

  public void testSavingFailureMessage() throws Exception {
    MavenIndex i = myIndices.add("xxx", MavenIndex.Kind.REMOTE);
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());

    String message = i.getFailureMessage();
    assertNotNull(message);

    shutdownIndices();
    initIndices();

    assertEquals(message, myIndices.getIndices().get(0).getFailureMessage());
  }

  public void testRepairingIndicesOnReadError() throws Exception {
    MavenIndex index = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(index, true, new EmptyProgressIndicator());

    shutdownIndices();
    damageFile(index, "groupIds.dat", false);
    initIndices();

    index = myIndices.getIndices().get(0);

    index.getGroupIds();
    assertTrue(isBroken);

    assertTrue(index.getGroupIds().isEmpty());

    myIndices.updateOrRepair(myIndices.getIndices().get(0), false, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(index.getGroupIds(), "junit");
  }

  public void testRepairingIndicesOnReadWhileAddingArtifact() throws Exception {
    // cannot make addArtifact throw an exception
    if (ignore()) return;

    MavenIndex index = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(index, true, new EmptyProgressIndicator());

    shutdownIndices();
    damageFile(index, "artifactIds-map.dat", false);
    initIndices();

    index = myIndices.getIndices().get(0);

    index.addArtifact(new MavenId("junit", "junit", "xxx"));
    assertTrue(isBroken);

    assertTrue(index.getGroupIds().isEmpty());

    myIndices.updateOrRepair(myIndices.getIndices().get(0), false, new EmptyProgressIndicator());
    assertUnorderedElementsAreEqual(index.getGroupIds(), "junit");
  }

  private void damageFile(MavenIndex index, String fileName, boolean fullDamage) throws IOException {
    File cachesDir = index.getCurrentDataDir();
    File file = new File(cachesDir, fileName);
    assertTrue(file.exists());

    if (fullDamage) {
      FileWriter w = new FileWriter(file);
      w.write("bad content");
      w.close();
    }
    else {
      byte[] content = FileUtil.loadFileBytes(file);
      for (int i = content.length / 2; i < content.length; i++) {
        content[i] = -1;
      }
      FileUtil.writeToFile(file, content);
    }
  }

  public void testGettingArtifactInfos() throws Exception {
    myRepositoryFixture.copy("local2", "local1");
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());

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
    myIndices.updateOrRepair(myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL),
                             true,
                             new EmptyProgressIndicator());

    shutdownIndices();
    initIndices();

    assertUnorderedElementsAreEqual(myIndices.getIndices().get(0).getGroupIds(), "junit");
  }

  public void testHasArtifactInfo() throws Exception {
    myRepositoryFixture.copy("local2", "local1");
    MavenIndex i = myIndices.add(myRepositoryFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndices.updateOrRepair(i, true, new EmptyProgressIndicator());

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
