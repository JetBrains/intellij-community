package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;

public class MavenEmbeddersManagerTest extends MavenTestCase {
  private MavenEmbeddersManager myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myManager = new MavenEmbeddersManager(getMavenGeneralSettings());
  }

  @Override
  protected void tearDown() throws Exception {
    myManager.releaseForcefullyInTests();
    super.tearDown();
  }

  public void testBasics() throws Exception {
    MavenEmbedderWrapper one = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_FOLDERS_RESOLVE);
    MavenEmbedderWrapper two = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);

    assertNotSame(one, two);
  }

  public void testForSameId() throws Exception {
    MavenEmbedderWrapper one1 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);
    MavenEmbedderWrapper one2 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);

    assertNotSame(one1, one2);

    myManager.release(one1);

    MavenEmbedderWrapper one3 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);

    assertSame(one1, one3);
  }

  public void testCachingOnlyOne() throws Exception {
    MavenEmbedderWrapper one1 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);
    MavenEmbedderWrapper one2 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);

    assertNotSame(one1, one2);

    myManager.release(one1);
    myManager.release(one2);

    MavenEmbedderWrapper one11 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);
    MavenEmbedderWrapper one22 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);

    assertSame(one1, one11);
    assertNotSame(one2, one22);
  }

  public void testResettingAllCachedAndInUse() throws Exception {
    MavenEmbedderWrapper one1 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);
    MavenEmbedderWrapper one2 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_FOLDERS_RESOLVE);

    myManager.release(one1);
    myManager.reset();

    myManager.release(one2);

    MavenEmbedderWrapper one11 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_DEPENDENCIES_RESOLVE);
    MavenEmbedderWrapper one22 = myManager.getEmbedder(MavenEmbeddersManager.EmbedderKind.FOR_FOLDERS_RESOLVE);

    assertNotSame(one1, one11);
    assertNotSame(one2, one22);
  }
}
