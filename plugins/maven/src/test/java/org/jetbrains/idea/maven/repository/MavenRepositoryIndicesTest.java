package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.core.MavenFactory;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.List;

public class MavenRepositoryIndicesTest extends MavenTestCase {
  private MavenWithDataTestFixture myDataTestFixture;
  private MavenRepositoryIndices myIndex;
  private MavenEmbedder myEmbedder;
  private File myIndexDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myDataTestFixture = new MavenWithDataTestFixture(myDir);
    myDataTestFixture.setUp();

    initIndex();
  }

  @Override
  protected void tearDown() throws Exception {
    shutdownIndex();
    super.tearDown();
  }

  private void initIndex() throws Exception {
    myEmbedder = MavenFactory.createEmbedderForExecute(getMavenCoreSettings());
    myIndexDir = new File(myDir, "index");
    myIndex = new MavenRepositoryIndices(myEmbedder, myIndexDir);
  }

  private void shutdownIndex() throws MavenEmbedderException {
    myIndex.close();
    myEmbedder.stop();
  }

  public void testAddingLocal() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local", myDataTestFixture.getTestDataPath("local1"), false);
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator());

    List<ArtifactInfo> result = myIndex.findByArtifactId("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);
  }

  public void testAddingSeveral() throws Exception {
    MavenRepositoryInfo i1 = new MavenRepositoryInfo("local1", myDataTestFixture.getTestDataPath("local1"), false);
    MavenRepositoryInfo i2 = new MavenRepositoryInfo("local2", myDataTestFixture.getTestDataPath("local2"), false);
    myIndex.add(i1);
    myIndex.add(i2);
    myIndex.update(i1, new EmptyProgressIndicator());
    myIndex.update(i2, new EmptyProgressIndicator());

    List<ArtifactInfo> result = myIndex.findByArtifactId("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);

    result = myIndex.findByArtifactId("jmock*");
    assertEquals(3, result.size());
    assertEquals("1.2.0", result.get(0).version);
  }

  public void testAddingWithoutUpdate() throws Exception {
    myIndex.add(new MavenRepositoryInfo("local", myDataTestFixture.getTestDataPath("local1"), false));

    assertEquals(0, myIndex.findByArtifactId("junit*").size());
  }

  public void testUpdaintLocalClearsPreviousIndex() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local", myDataTestFixture.getTestDataPath("local1"), false);
    myIndex.add(i);

    myIndex.update(i, new EmptyProgressIndicator());
    assertEquals(3, myIndex.findByArtifactId("junit*").size());

    myIndex.update(i, new EmptyProgressIndicator());
    assertEquals(3, myIndex.findByArtifactId("junit*").size());
  }

  public void testAddingRemote() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("remote", "file:///" + myDataTestFixture.getTestDataPath("remote"), true);
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator());

    List<ArtifactInfo> result = myIndex.findByArtifactId("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);
  }

  public void testChanging() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local1", myDataTestFixture.getTestDataPath("local1"), false);
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator());

    assertEquals(3, myIndex.findByArtifactId("junit*").size());
    assertEquals(0, myIndex.findByArtifactId("jmock*").size());

    myIndex.change(i, "local2", myDataTestFixture.getTestDataPath("local2"), false);
    myIndex.update(i, new EmptyProgressIndicator());

    assertEquals(0, myIndex.findByArtifactId("junit*").size());
    assertEquals(3, myIndex.findByArtifactId("jmock*").size());
  }

  public void testChangingWithSameID() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local", myDataTestFixture.getTestDataPath("local1"), false);
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator());

    myIndex.change(i, "local", myDataTestFixture.getTestDataPath("local2"), false);
    myIndex.update(i, new EmptyProgressIndicator());

    assertEquals(3, myIndex.findByArtifactId("jmock*").size());
  }

  public void testRemoving() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("remote", "file:///" + myDataTestFixture.getTestDataPath("remote"), true);
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator());

    myIndex.remove(i);
    assertEquals(0, myIndex.findByArtifactId("junit*").size());
  }

  public void testDoNotSaveIndexAfterRemoving() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("remote", "file:///" + myDataTestFixture.getTestDataPath("remote"), true);
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator());

    myIndex.remove(i);
    myIndex.add(i);
    assertEquals(0, myIndex.findByArtifactId("junit*").size());
  }

  public void testSaving() throws Exception {
    MavenRepositoryInfo i1 = new MavenRepositoryInfo("local", myDataTestFixture.getTestDataPath("local2"), false);
    MavenRepositoryInfo i2 = new MavenRepositoryInfo("remote", "file:///" + myDataTestFixture.getTestDataPath("remote"), true);
    myIndex.add(i1);
    myIndex.add(i2);
    myIndex.update(i1, new EmptyProgressIndicator());
    myIndex.update(i2, new EmptyProgressIndicator());

    myIndex.save();

    shutdownIndex();
    initIndex();
    myIndex.load();

    List<ArtifactInfo> result = myIndex.findByArtifactId("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);

    result = myIndex.findByArtifactId("jmock*");
    assertEquals(3, result.size());
    assertEquals("1.2.0", result.get(0).version);
  }

  public void testClearingIndexesOnLoadError() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local", myDataTestFixture.getTestDataPath("local2"), false);
    myIndex.add(i);
    myIndex.save();
    shutdownIndex();

    FileWriter w = new FileWriter(new File(myIndexDir, MavenRepositoryIndices.INDICES_LIST_FILE));
    w.write("bad content");
    w.close();

    initIndex();

    try {
      myIndex.load();
      fail("must has thrown an exception");
    }
    catch (MavenRepositoryException e) {
    }

    assertTrue(myIndex.getInfos().isEmpty());
    assertFalse(myIndexDir.exists());
  }

  public void testClearingAlreadyLoadedIndexesOnLoadError() throws Exception {
    myIndex.add(new MavenRepositoryInfo("local1", myDataTestFixture.getTestDataPath("local1"), false));
    myIndex.add(new MavenRepositoryInfo("local2", myDataTestFixture.getTestDataPath("local2"), false));
    myIndex.save();
    shutdownIndex();

    File listFile = new File(myIndexDir, MavenRepositoryIndices.INDICES_LIST_FILE);
    byte[] bytes = FileUtil.loadFileBytes(listFile);
    for (int i = bytes.length / 2; i < bytes.length; i++) {
      bytes[i] = 0;
    }

    FileOutputStream s = new FileOutputStream(listFile);
    s.write(bytes);
    s.close();

    initIndex();

    try {
      myIndex.load();
      fail("must has thrown an exception");
    }
    catch (MavenRepositoryException e) {
    }

    assertTrue(myIndex.getInfos().isEmpty());
    assertFalse(myIndexDir.exists());
  }

  public void testAddingIndexWithExistingDirectoryDoesNotThrowException() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local", myDataTestFixture.getTestDataPath("local1"), false);
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator());

    myIndex.save();
    shutdownIndex();
    
    initIndex();
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator()); // shouldn't throw
  }

  public void testGettingArtifactInfos() throws Exception {
    MavenRepositoryInfo i1 = new MavenRepositoryInfo("local1", myDataTestFixture.getTestDataPath("local1"), false);
    MavenRepositoryInfo i2 = new MavenRepositoryInfo("local2", myDataTestFixture.getTestDataPath("local2"), false);
    myIndex.add(i1);
    myIndex.add(i2);
    myIndex.update(i1, new EmptyProgressIndicator());
    myIndex.update(i2, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndex.getGroupIds(), "junit", "jmock");

    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("junit"), "junit");
    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("jmock"), "jmock");
    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("unknown"));

    assertUnorderedElementsAreEqual(myIndex.getVersions("junit", "junit"), "3.8.1", "3.8.2", "4.0");
    assertUnorderedElementsAreEqual(myIndex.getVersions("junit", "jmock"));
    assertUnorderedElementsAreEqual(myIndex.getVersions("unknown", "unknown"));
  }

  public void testGettingArtifactInfosFromUnUpdatedRepositories() throws Exception {
    myIndex.add(new MavenRepositoryInfo("local1", myDataTestFixture.getTestDataPath("local1"), false));
    assertUnorderedElementsAreEqual(myIndex.getGroupIds()); // shouldn't throw
  }

  public void testGettingArtifactInfosAfterReload() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local", myDataTestFixture.getTestDataPath("local1"), false);
    myIndex.add(i);
    myIndex.update(i, new EmptyProgressIndicator());

    myIndex.save();
    shutdownIndex();
    initIndex();
    myIndex.load();
    
    assertUnorderedElementsAreEqual(myIndex.getGroupIds(), "junit");
  }
}
