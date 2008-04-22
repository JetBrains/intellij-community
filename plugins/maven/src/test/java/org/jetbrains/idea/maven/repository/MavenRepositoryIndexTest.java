package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.idea.maven.core.MavenFactory;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.util.List;

public class MavenRepositoryIndexTest extends MavenWithDataTestCase {
  private MavenRepositoryIndex index;
  private MavenEmbedder embedder;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    initIndex();
  }

  @Override
  protected void tearDown() throws Exception {
    shutdownIndex();
    super.tearDown();
  }

  private void initIndex() throws Exception {
    embedder = MavenFactory.createEmbedderForExecute(getMavenCoreSettings());
    index = new MavenRepositoryIndex(embedder, new File(dir, "index"));
  }

  private void shutdownIndex() throws MavenEmbedderException {
    index.close();
    embedder.stop();
  }

  public void testAddingLocal() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local", getTestDataPath("local1"), false);
    index.add(i);
    index.update(i, new EmptyProgressIndicator());

    List<ArtifactInfo> result = index.find("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);
  }

  public void testAddingSeveral() throws Exception {
    MavenRepositoryInfo i1 = new MavenRepositoryInfo("local1", getTestDataPath("local1"), false);
    MavenRepositoryInfo i2 = new MavenRepositoryInfo("local2", getTestDataPath("local2"), false);
    index.add(i1);
    index.add(i2);
    index.update(i1, new EmptyProgressIndicator());
    index.update(i2, new EmptyProgressIndicator());

    List<ArtifactInfo> result = index.find("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);

    result = index.find("jmock*");
    assertEquals(3, result.size());
    assertEquals("1.2.0", result.get(0).version);
  }

  public void testAddingWithoutUpdate() throws Exception {
    index.add(new MavenRepositoryInfo("local", getTestDataPath("local1"), false));

    List<ArtifactInfo> result = index.find("junit*");
    assertEquals(0, result.size());
  }

  public void testAddingRemote() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("remote", "file:///" + getTestDataPath("remote"), true);
    index.add(i);
    index.update(i, new EmptyProgressIndicator());

    List<ArtifactInfo> result = index.find("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);
  }

  public void testChanging() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("local1", getTestDataPath("local1"), false);
    index.add(i);
    index.update(i, new EmptyProgressIndicator());

    assertEquals(3, index.find("junit*").size());
    assertEquals(0, index.find("jmock*").size());

    index.change(i, "local2", getTestDataPath("local2"), false);
    index.update(i, new EmptyProgressIndicator());

    assertEquals(0, index.find("junit*").size());
    assertEquals(3, index.find("jmock*").size());
  }

  public void testRemoving() throws Exception {
    MavenRepositoryInfo i = new MavenRepositoryInfo("remote", "file:///" + getTestDataPath("remote"), true);
    index.add(i);
    index.update(i, new EmptyProgressIndicator());

    index.remove(i);

    List<ArtifactInfo> result = index.find("junit*");
    assertEquals(0, result.size());
  }

  public void testSaving() throws Exception {
    MavenRepositoryInfo i1 = new MavenRepositoryInfo("local", getTestDataPath("local2"), false);
    MavenRepositoryInfo i2 = new MavenRepositoryInfo("remote", "file:///" + getTestDataPath("remote"), true);
    index.add(i1);
    index.add(i2);
    index.update(i1, new EmptyProgressIndicator());
    index.update(i2, new EmptyProgressIndicator());

    index.save();

    shutdownIndex();
    initIndex();

    List<ArtifactInfo> result = index.find("junit*");
    assertEquals(3, result.size());
    assertEquals("4.0", result.get(0).version);

    result = index.find("jmock*");
    assertEquals(3, result.size());
    assertEquals("1.2.0", result.get(0).version);
  }
}
