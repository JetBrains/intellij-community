package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.core.MavenFactory;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.List;

public class MavenIndicesTest extends MavenImportingTestCase {
  private MavenWithDataTestFixture myDataTestFixture;
  private MavenIndices myIndex;
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
    myIndex = new MavenIndices(myEmbedder, myIndexDir);
  }

  private void shutdownIndex() throws MavenEmbedderException {
    myIndex.close();
    myEmbedder.stop();
  }

  public void testAddingLocal() throws Exception {
    MavenIndex i = new MavenIndex("local", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    List<ArtifactInfo> result = myIndex.findByArtifactId("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);
  }

  public void testAddingSeveral() throws Exception {
    MavenIndex i1 = new MavenIndex("local1", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = new MavenIndex("local2", myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndex.add(i1);
    myIndex.add(i2);
    myIndex.update(i1, myProject, new EmptyProgressIndicator());
    myIndex.update(i2, myProject, new EmptyProgressIndicator());

    List<ArtifactInfo> result = myIndex.findByArtifactId("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);

    result = myIndex.findByArtifactId("jmock*");
    assertEquals(3, result.size());
    assertEquals("1.2.0", result.get(0).version);
  }

  public void testAddingWithoutUpdate() throws Exception {
    myIndex.add(new MavenIndex("local", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL));

    assertEquals(0, myIndex.findByArtifactId("junit*").size());
  }

  public void testUpdatingLocalClearsPreviousIndex() throws Exception {
    MavenIndex i = new MavenIndex("local", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndex.add(i);

    myIndex.update(i, myProject, new EmptyProgressIndicator());
    assertEquals(3, myIndex.findByArtifactId("junit*").size());

    myIndex.update(i, myProject, new EmptyProgressIndicator());
    assertEquals(3, myIndex.findByArtifactId("junit*").size());
  }

  public void testAddingRemote() throws Exception {
    MavenIndex i = new MavenIndex("remote", "file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    List<ArtifactInfo> result = myIndex.findByArtifactId("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);
  }

  public void testAddingProjectIndex() throws Exception {
    importProject("<groupId>group</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>version</version>");

    createModulePom("m1",
                    "<groupId>group1</groupId>" +
                    "<artifactId>module1</artifactId>" +
                    "<version>version1</version>");

    createModulePom("m2",
                    "<groupId>group2</groupId>" +
                    "<artifactId>module2</artifactId>" +
                    "<version>version2</version>");

    MavenIndex i = new MavenIndex("project", myProject.getBaseDir().getPath(), MavenIndex.Kind.PROJECT);
    myIndex.add(i);

    assertTrue(myIndex.getGroupIds().isEmpty());

    myIndex.update(i, myProject, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndex.getGroupIds(), "group", "group1", "group2");
    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("group"), "project");
    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("group1"), "module1");
    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("group2"), "module2");
    assertUnorderedElementsAreEqual(myIndex.getVersions("group", "project"), "version");
    assertUnorderedElementsAreEqual(myIndex.getVersions("group1", "module1"), "version1");
    assertUnorderedElementsAreEqual(myIndex.getVersions("group2", "module2"), "version2");
  }

  public void testSavingAndRestoringProjectIndex() throws Exception {
    importProject("<groupId>group</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>version</version>");

    MavenIndex i = new MavenIndex("project", myProject.getBaseDir().getPath(), MavenIndex.Kind.PROJECT);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    myIndex.save();
    shutdownIndex();
    initIndex();
    myIndex.load();

    assertUnorderedElementsAreEqual(myIndex.getGroupIds(), "group");
    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("group"), "project");
    assertUnorderedElementsAreEqual(myIndex.getVersions("group", "project"), "version");
  }

  public void testChanging() throws Exception {
    MavenIndex i = new MavenIndex("local1", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    assertEquals(3, myIndex.findByArtifactId("junit*").size());
    assertEquals(0, myIndex.findByArtifactId("jmock*").size());

    myIndex.change(i, "local2", myDataTestFixture.getTestDataPath("local2"));
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    assertEquals(0, myIndex.findByArtifactId("junit*").size());
    assertEquals(3, myIndex.findByArtifactId("jmock*").size());
  }

  public void testChangingWithSameID() throws Exception {
    MavenIndex i = new MavenIndex("local", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    myIndex.change(i, "local", myDataTestFixture.getTestDataPath("local2"));
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    assertEquals(3, myIndex.findByArtifactId("jmock*").size());
  }

  public void testRemoving() throws Exception {
    MavenIndex i = new MavenIndex("remote", "file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    myIndex.remove(i);
    assertEquals(0, myIndex.findByArtifactId("junit*").size());
  }

  public void testDoNotSaveIndexAfterRemoving() throws Exception {
    MavenIndex i = new MavenIndex("remote", "file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    myIndex.remove(i);
    myIndex.add(i);
    assertEquals(0, myIndex.findByArtifactId("junit*").size());
  }

  public void testSaving() throws Exception {
    MavenIndex i1 = new MavenIndex("local", myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = new MavenIndex("remote", "file:///" + myDataTestFixture.getTestDataPath("remote"), MavenIndex.Kind.REMOTE);
    myIndex.add(i1);
    myIndex.add(i2);
    myIndex.update(i1, myProject, new EmptyProgressIndicator());
    myIndex.update(i2, myProject, new EmptyProgressIndicator());

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
    MavenIndex i = new MavenIndex("local", myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndex.add(i);
    myIndex.save();
    shutdownIndex();

    FileWriter w = new FileWriter(new File(myIndexDir, MavenIndices.INDICES_LIST_FILE));
    w.write("bad content");
    w.close();

    initIndex();

    try {
      myIndex.load();
      fail("must has thrown an exception");
    }
    catch (MavenIndexException e) {
    }

    assertTrue(myIndex.getIndices().isEmpty());
    assertFalse(myIndexDir.exists());
  }

  public void testClearingAlreadyLoadedIndexesOnLoadError() throws Exception {
    myIndex.add(new MavenIndex("local1", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL));
    myIndex.add(new MavenIndex("local2", myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL));
    myIndex.save();
    shutdownIndex();

    File listFile = new File(myIndexDir, MavenIndices.INDICES_LIST_FILE);
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
    catch (MavenIndexException e) {
    }

    assertTrue(myIndex.getIndices().isEmpty());
    assertFalse(myIndexDir.exists());
  }

  public void testAddingIndexWithExistingDirectoryDoesNotThrowException() throws Exception {
    MavenIndex i = new MavenIndex("local", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    myIndex.save();
    shutdownIndex();
    
    initIndex();
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());
  }

  public void testGettingArtifactInfos() throws Exception {
    MavenIndex i1 = new MavenIndex("local1", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    MavenIndex i2 = new MavenIndex("local2", myDataTestFixture.getTestDataPath("local2"), MavenIndex.Kind.LOCAL);
    myIndex.add(i1);
    myIndex.add(i2);
    myIndex.update(i1, myProject, new EmptyProgressIndicator());
    myIndex.update(i2, myProject, new EmptyProgressIndicator());

    assertUnorderedElementsAreEqual(myIndex.getGroupIds(), "junit", "jmock");

    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("junit"), "junit");
    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("jmock"), "jmock");
    assertUnorderedElementsAreEqual(myIndex.getArtifactIds("unknown"));

    assertUnorderedElementsAreEqual(myIndex.getVersions("junit", "junit"), "3.8.1", "3.8.2", "4.0");
    assertUnorderedElementsAreEqual(myIndex.getVersions("junit", "jmock"));
    assertUnorderedElementsAreEqual(myIndex.getVersions("unknown", "unknown"));
  }

  public void testGettingArtifactInfosFromUnUpdatedRepositories() throws Exception {
    myIndex.add(new MavenIndex("local1", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL));
    assertUnorderedElementsAreEqual(myIndex.getGroupIds()); // shouldn't throw
  }

  public void testGettingArtifactInfosAfterReload() throws Exception {
    MavenIndex i = new MavenIndex("local", myDataTestFixture.getTestDataPath("local1"), MavenIndex.Kind.LOCAL);
    myIndex.add(i);
    myIndex.update(i, myProject, new EmptyProgressIndicator());

    myIndex.save();
    shutdownIndex();
    initIndex();
    myIndex.load();
    
    assertUnorderedElementsAreEqual(myIndex.getGroupIds(), "junit");
  }
}
