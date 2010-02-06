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
package org.jetbrains.idea.maven.project;

import com.intellij.util.Function;
import com.intellij.util.containers.SoftValueHashMap;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.Map;
import java.util.Set;

public class MavenEmbeddersManager {
  public enum EmbedderKind {
    FOR_DEPENDENCIES_RESOLVE,
    FOR_PLUGINS_RESOLVE,
    FOR_FOLDERS_RESOLVE,
    FOR_DOWNLOAD,
    FOR_POST_PROCESSING,
  }

  private final MavenGeneralSettings myGeneralSettings;

  private final Map<EmbedderKind, MavenEmbedderWrapper> myPool = new SoftValueHashMap<EmbedderKind, MavenEmbedderWrapper>();
  private final Set<MavenEmbedderWrapper> myEmbeddersInUse = new THashSet<MavenEmbedderWrapper>();
  private final Set<MavenEmbedderWrapper> myEmbeddersToClear = new THashSet<MavenEmbedderWrapper>();

  public MavenEmbeddersManager(MavenGeneralSettings generalSettings) {
    myGeneralSettings = generalSettings;
  }

  public synchronized void reset() {
    releasePooledEmbedders(false);
  }

  public synchronized void clearCaches() {
    forEachPooled(false, new Function<MavenEmbedderWrapper, Object>() {
      public Object fun(MavenEmbedderWrapper each) {
        each.clearCaches();
        return null;
      }
    });
    myEmbeddersToClear.addAll(myEmbeddersInUse);
  }

  public synchronized MavenEmbedderWrapper getEmbedder(EmbedderKind kind) {
    MavenEmbedderWrapper result = myPool.get(kind);
    if (result == null) {
      result = MavenEmbedderFactory.createEmbedder(myGeneralSettings);
      myPool.put(kind, result);
    }

    if (myEmbeddersInUse.contains(result)) {
      MavenLog.LOG.warn("embedder " + kind + " is already used");
      return MavenEmbedderFactory.createEmbedder(myGeneralSettings);
    }

    myEmbeddersInUse.add(result);
    return result;
  }

  public synchronized void release(MavenEmbedderWrapper embedder) {
    if (!myEmbeddersInUse.contains(embedder)) {
      embedder.release();
      myEmbeddersToClear.remove(embedder);
      return;
    }

    myEmbeddersInUse.remove(embedder);
    if (myEmbeddersToClear.contains(embedder)) {
      embedder.clearCaches();
      myEmbeddersToClear.remove(embedder);
    }
  }

  public synchronized void release() {
    if (!myEmbeddersInUse.isEmpty()) {
      MavenLog.LOG.warn("embedders should be release first");
    }
    releasePooledEmbedders(false);
  }

  public synchronized void releaseForcefullyInTests() {
    releasePooledEmbedders(true);
  }

  private synchronized void releasePooledEmbedders(boolean force) {
    forEachPooled(force, new Function<MavenEmbedderWrapper, Object>() {
      public Object fun(MavenEmbedderWrapper each) {
        each.release();
        return null;
      }
    });
    myPool.clear();
    myEmbeddersInUse.clear();
    myEmbeddersToClear.clear();
  }

  private void forEachPooled(boolean includeInUse, Function<MavenEmbedderWrapper, ?> func) {
    for (EmbedderKind each : myPool.keySet()) {
      MavenEmbedderWrapper embedder = myPool.get(each);
      if (embedder == null) continue; // collected
      if (!includeInUse && myEmbeddersInUse.contains(embedder)) continue;
      func.fun(embedder);
    }
  }
}
