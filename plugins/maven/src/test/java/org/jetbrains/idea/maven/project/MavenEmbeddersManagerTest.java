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
    myManager.releaseForceefullyInTests();
    super.tearDown();
  }

  public void testBasics() throws Exception {
    MavenEmbedderWrapper one = myManager.getEmbedder("one");
    MavenEmbedderWrapper two = myManager.getEmbedder("two");

    assertNotSame(one, two);
  }

  public void testForSameId() throws Exception {
    MavenEmbedderWrapper one1 = myManager.getEmbedder("one");
    MavenEmbedderWrapper one2 = myManager.getEmbedder("one");

    assertNotSame(one1, one2);

    myManager.release(one1);

    MavenEmbedderWrapper one3 = myManager.getEmbedder("one");

    assertSame(one1, one3);
  }

  public void testCachingNoMoreThanTwo() throws Exception {
    MavenEmbedderWrapper one1 = myManager.getEmbedder("one");
    MavenEmbedderWrapper one2 = myManager.getEmbedder("one");
    MavenEmbedderWrapper one3 = myManager.getEmbedder("one");

    assertNotSame(one1, one2);
    assertNotSame(one1, one3);

    myManager.release(one1);
    myManager.release(one2);
    myManager.release(one3);

    MavenEmbedderWrapper one4 = myManager.getEmbedder("one");
    MavenEmbedderWrapper one5 = myManager.getEmbedder("one");
    MavenEmbedderWrapper one6 = myManager.getEmbedder("one");

    assertSame(one1, one4);
    assertSame(one2, one5);
    assertNotSame(one3, one6);
  }

  public void testResettingAllCachedAndInUse() throws Exception {
    MavenEmbedderWrapper one1 = myManager.getEmbedder("one");
    MavenEmbedderWrapper one2 = myManager.getEmbedder("one");

    myManager.release(one1);
    myManager.reset();

    myManager.release(one2);

    MavenEmbedderWrapper one3 = myManager.getEmbedder("one");
    MavenEmbedderWrapper one4 = myManager.getEmbedder("one");

    assertNotSame(one1, one3);
    assertNotSame(one1, one4);
    assertNotSame(one2, one3);
    assertNotSame(one2, one4);
  }
}
