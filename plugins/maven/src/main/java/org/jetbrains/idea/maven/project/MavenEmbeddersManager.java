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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.Function;
import com.intellij.util.containers.SoftValueHashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.Map;
import java.util.Set;

public class MavenEmbeddersManager {
  public static final Key FOR_DEPENDENCIES_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_DEPENDENCIES_RESOLVE");
  public static final Key FOR_PLUGINS_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_PLUGINS_RESOLVE");
  public static final Key FOR_FOLDERS_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_FOLDERS_RESOLVE");
  public static final Key FOR_POST_PROCESSING = Key.create(MavenEmbeddersManager.class + ".FOR_POST_PROCESSING");
  public static final Key FOR_GET_VERSIONS = Key.create(MavenEmbeddersManager.class + ".FOR_GET_VERSIONS");

  // will always regardless to 'work offline' setting
  public static final Key FOR_DOWNLOAD = Key.create(MavenEmbeddersManager.class + ".FOR_DOWNLOAD");

  private final Project myProject;

  private final Map<Key, MavenEmbedderWrapper> myPool = new SoftValueHashMap<>();
  private final Set<MavenEmbedderWrapper> myEmbeddersInUse = new THashSet<>();
  private final Set<MavenEmbedderWrapper> myEmbeddersToClear = new THashSet<>();

  public MavenEmbeddersManager(Project project) {
    myProject = project;
  }

  public synchronized void reset() {
    releasePooledEmbedders(false);
  }

  public synchronized void clearCaches() {
    forEachPooled(false, each -> {
      each.clearCaches();
      return null;
    });
    myEmbeddersToClear.addAll(myEmbeddersInUse);
  }

  @NotNull
  public synchronized MavenEmbedderWrapper getEmbedder(Key kind) {
    MavenEmbedderWrapper result = myPool.get(kind);
    boolean alwaysOnline = kind == FOR_DOWNLOAD;

    if (result == null) {
      result = MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline);
      myPool.put(kind, result);
    }

    if (myEmbeddersInUse.contains(result)) {
      MavenLog.LOG.warn("embedder " + kind + " is already used");
      return MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline);
    }

    myEmbeddersInUse.add(result);
    return result;
  }

  public synchronized void release(@NotNull MavenEmbedderWrapper embedder) {
    if (!myEmbeddersInUse.contains(embedder)) {
      embedder.release();
      myEmbeddersToClear.remove(embedder);
      return;
    }

    embedder.reset();
    myEmbeddersInUse.remove(embedder);

    if (myEmbeddersToClear.contains(embedder)) {
      embedder.clearCaches();
      myEmbeddersToClear.remove(embedder);
    }
  }

  @TestOnly
  public synchronized void releaseInTests() {
    if (!myEmbeddersInUse.isEmpty()) {
      MavenLog.LOG.warn("embedders should be release first");
    }
    releasePooledEmbedders(false);
  }

  public synchronized void releaseForcefullyInTests() {
    releasePooledEmbedders(true);
  }

  private synchronized void releasePooledEmbedders(boolean force) {
    forEachPooled(force, each -> {
      each.release();
      return null;
    });
    myPool.clear();
    myEmbeddersInUse.clear();
    myEmbeddersToClear.clear();
  }

  private void forEachPooled(boolean includeInUse, Function<MavenEmbedderWrapper, ?> func) {
    for (Key each : myPool.keySet()) {
      MavenEmbedderWrapper embedder = myPool.get(each);
      if (embedder == null) continue; // collected
      if (!includeInUse && myEmbeddersInUse.contains(embedder)) continue;
      func.fun(embedder);
    }
  }
}
