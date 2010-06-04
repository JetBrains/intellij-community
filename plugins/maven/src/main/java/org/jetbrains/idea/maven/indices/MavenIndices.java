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

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.facade.MavenIndexerWrapper;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MavenIndices {
  private final MavenIndexerWrapper myIndexer;

  private final File myIndicesDir;
  private final MavenIndex.IndexListener myListener;

  private final List<MavenIndex> myIndices = new ArrayList<MavenIndex>();
  private static final Object ourDirectoryLock = new Object();

  public MavenIndices(MavenIndexerWrapper indexer, File indicesDir, MavenIndex.IndexListener listener) {
    myIndexer = indexer;
    myIndicesDir = indicesDir;
    myListener = listener;

    load();
  }

  private void load() {
    if (!myIndicesDir.exists()) return;

    File[] indices = myIndicesDir.listFiles();
    if (indices == null) return;
    Arrays.sort(indices);
    for (File each : indices) {
      if (!each.isDirectory()) continue;

      try {
        MavenIndex index = new MavenIndex(myIndexer, each, myListener);
        if (find(index.getRepositoryId(), index.getRepositoryPathOrUrl(), index.getKind()) != null) {
          index.close();
          FileUtil.delete(each);
          continue;
        }
        myIndices.add(index);
      }
      catch (Exception e) {
        FileUtil.delete(each);
        MavenLog.LOG.warn(e);
      }
    }
  }

  public synchronized void close() {
    for (MavenIndex each : myIndices) {
      each.close();
    }
    myIndices.clear();
  }

  public synchronized List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myIndices);
  }

  public synchronized MavenIndex add(String repositoryId, String repositoryPathOrUrl, MavenIndex.Kind kind) throws MavenIndexException {
    MavenIndex index = find(repositoryId, repositoryPathOrUrl, kind);
    if (index != null) return index;

    File dir = getAvailableIndexDir();
    index = new MavenIndex(myIndexer, dir, repositoryId, repositoryPathOrUrl, kind, myListener);
    myIndices.add(index);
    return index;
  }

  public MavenIndex find(String repositoryId, String repositoryPathOrUrl, MavenIndex.Kind kind) {
    for (MavenIndex each : myIndices) {
      if (each.isFor(kind, repositoryId, repositoryPathOrUrl)) return each;
    }
    return null;
  }

  private File getAvailableIndexDir() {
    return findAvailableDir(myIndicesDir, "Index", 1000);
  }

  static File findAvailableDir(File parent, String prefix, int max) {
    synchronized (ourDirectoryLock) {
      for (int i = 0; i < max; i++) {
        String name = prefix + i;
        File f = new File(parent, name);
        if (!f.exists()) {
          f.mkdirs();
          assert f.exists();
          return f;
        }
      }
      throw new RuntimeException("No available dir found");
    }
  }

  public void updateOrRepair(MavenIndex index, boolean fullUpdate, MavenGeneralSettings settings, MavenProgressIndicator progress)
    throws MavenProcessCanceledException {
    index.updateOrRepair(fullUpdate, settings, progress);
  }
}