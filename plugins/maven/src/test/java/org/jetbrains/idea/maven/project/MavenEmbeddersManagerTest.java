/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.maven.testFramework.MavenTestCase;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;

public class MavenEmbeddersManagerTest extends MavenTestCase {
  private MavenEmbeddersManager myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myManager = new MavenEmbeddersManager(myProject);
  }

  @Override
  protected void tearDownFixtures() throws Exception {
    super.tearDownFixtures();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myManager.releaseForcefullyInTests();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testBasics() {
    MavenEmbedderWrapper one = myManager.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, null, myDir.getPath());
    MavenEmbedderWrapper two = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());

    assertNotSame(one, two);
  }

  public void testForSameId() {
    MavenEmbedderWrapper one1 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());
    MavenEmbedderWrapper one2 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());

    assertNotSame(one1, one2);

    myManager.release(one1);

    MavenEmbedderWrapper one3 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());

    assertSame(one1, one3);
  }

  public void testCachingOnlyOne() {
    MavenEmbedderWrapper one1 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());
    MavenEmbedderWrapper one2 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());

    assertNotSame(one1, one2);

    myManager.release(one1);
    myManager.release(one2);

    MavenEmbedderWrapper one11 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());
    MavenEmbedderWrapper one22 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());

    assertSame(one1, one11);
    assertNotSame(one2, one22);
  }

  public void testResettingAllCachedAndInUse() {
    MavenEmbedderWrapper one1 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());
    MavenEmbedderWrapper one2 = myManager.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, null, myDir.getPath());

    myManager.release(one1);
    myManager.reset();

    myManager.release(one2);

    MavenEmbedderWrapper one11 = myManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, null, myDir.getPath());
    MavenEmbedderWrapper one22 = myManager.getEmbedder(MavenEmbeddersManager.FOR_FOLDERS_RESOLVE, null, myDir.getPath());

    assertNotSame(one1, one11);
    assertNotSame(one2, one22);
  }
}
