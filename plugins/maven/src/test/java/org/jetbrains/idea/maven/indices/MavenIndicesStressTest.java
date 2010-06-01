/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.facade.MavenFacadeManager;
import org.jetbrains.idea.maven.facade.MavenIndexerWrapper;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MavenIndicesStressTest extends MavenIndicesTestCase implements MavenIndex.IndexListener {
  public void test1() throws Exception {
    MavenCustomRepositoryHelper helper;

    helper = new MavenCustomRepositoryHelper(myDir, "plugins", "local1", "local2");
    helper.copy("plugins", "local1");
    helper.copy("local2", "local1");
    //setRepositoryPath(fixture.getTestDataPath("local1"));

    final MavenIndexerWrapper indexer = MavenFacadeManager.getInstance().createIndexer();
    File indicesDir = new File(myDir, "indices");

    final MavenIndices indices = new MavenIndices(indexer, indicesDir, this);
    final MavenIndex index = indices.add("id", getRepositoryPath(), MavenIndex.Kind.LOCAL);

    final AtomicBoolean isFinished = new AtomicBoolean(false);

    Thread t1 = new Thread(new Runnable() {
      public void run() {
        try {
          for (int i = 0; i < 3; i++) {
            System.out.println("INDEXING #" + i);
            indices.updateOrRepair(index, true, getMavenGeneralSettings(), EMPTY_MAVEN_PROCESS);
          }
        }
        catch (MavenProcessCanceledException e) {
          throw new RuntimeException(e);
        }
        finally {
          isFinished.set(true);
        }
      }
    });

    Thread t2 = new Thread(new Runnable() {
      public void run() {
        Random random = new Random();
        while (!isFinished.get()) {
          int i = random.nextInt(100);
          System.out.println("Adding artifact #" + i);
          //index.addArtifact(new MavenId("group" + i, "artifact" + i, "" + i));
          fail();
        }
      }
    });

    t1.start();
    t2.start();

    do {
      t1.join(100);
      t2.join(100);
    }
    while (!isFinished.get());

    t1.join(100);
    t2.join(100);

    indices.close();
  }

  public void test2() throws Exception {
    MavenCustomRepositoryHelper helper;

    helper = new MavenCustomRepositoryHelper(myDir, "plugins", "local1", "local2");
    helper.copy("plugins", "local1");
    helper.copy("local2", "local1");
    setRepositoryPath(helper.getTestDataPath("local1"));

    final MavenIndexerWrapper indexer = MavenFacadeManager.getInstance().createIndexer();
    File indicesDir = new File(myDir, "indices");

    MavenIndices indices = new MavenIndices(indexer, indicesDir, this);
    MavenIndex index = indices.add("id", getRepositoryPath(), MavenIndex.Kind.LOCAL);

    //index.addArtifact(new MavenId("group1", "artifact1", "1"));
    fail();
    indices.close();

    MavenIndices indices1 = new MavenIndices(indexer, indicesDir, this);
    MavenIndices indices2 = new MavenIndices(indexer, indicesDir, this);

    AtomicInteger finishedCount = new AtomicInteger(0);

    Thread t1 = createThread(indices1.getIndices().get(0), finishedCount);
    Thread t2 = createThread(indices2.getIndices().get(0), finishedCount);

    t1.start();
    t2.start();

    do {
      t1.join(100);
      t2.join(100);
    }
    while (finishedCount.get() < 2);

    t1.join(100);
    t2.join(100);

    indices.close();

    indices1.close();
    indices2.close();
  }

  private Thread createThread(final MavenIndex index, final AtomicInteger finishedCount) {
    Thread t2 = new Thread(new Runnable() {
      public void run() {
        try {
          for (int i = 0; i < 1000; i++) {
            System.out.println("Adding artifact #" + i);
            //index.addArtifact(new MavenId("group" + i, "artifact" + i, "" + i));
            fail();
          }
        }
        finally {
          finishedCount.incrementAndGet();
        }
      }
    });
    return t2;
  }

  public void indexIsBroken(MavenIndex index) {
  }
}
