// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MavenEmbeddersManager {
  public static final Key FOR_DEPENDENCIES_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_DEPENDENCIES_RESOLVE");
  public static final Key FOR_PLUGINS_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_PLUGINS_RESOLVE");
  public static final Key FOR_FOLDERS_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_FOLDERS_RESOLVE");
  public static final Key FOR_POST_PROCESSING = Key.create(MavenEmbeddersManager.class + ".FOR_POST_PROCESSING");
  public static final Key FOR_GET_VERSIONS = Key.create(MavenEmbeddersManager.class + ".FOR_GET_VERSIONS");
  public static final Key FOR_MODEL_READ = Key.create(MavenEmbeddersManager.class + ".FOR_MODEL_READ");

  // will always regardless to 'work offline' setting
  public static final Key FOR_DOWNLOAD = Key.create(MavenEmbeddersManager.class + ".FOR_DOWNLOAD");

  private final Project myProject;

  private final Map<Trinity<Key, String, String>, MavenEmbedderWrapper> myPool = ContainerUtil.createSoftValueMap();
  private final Set<MavenEmbedderWrapper> myEmbeddersInUse = new HashSet<>();
  private final Set<MavenEmbedderWrapper> myEmbeddersToClear = new HashSet<>();

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
  public synchronized MavenEmbedderWrapper getEmbedder(@NotNull MavenProject mavenProject, Key kind) {
    String baseDir = MavenUtil.getBaseDir(mavenProject.getDirectoryFile()).toString();
    return getEmbedder(kind, baseDir, baseDir);
  }
  @NotNull
  public synchronized MavenEmbedderWrapper getEmbedder(Key kind, String workingDirectory,  @NotNull String multiModuleProjectDirectory) {
    Trinity<Key, String, String> key = Trinity.create(kind, workingDirectory, multiModuleProjectDirectory);
    MavenEmbedderWrapper result = myPool.get(key);
    boolean alwaysOnline = kind == FOR_DOWNLOAD;

    if (result == null) {
      result = MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline, workingDirectory, multiModuleProjectDirectory);
      myPool.put(key, result);
    }

    if (myEmbeddersInUse.contains(result)) {
      MavenLog.LOG.warn("embedder " + key + " is already used");
      return MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline, workingDirectory, multiModuleProjectDirectory);
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
    for (Trinity<Key, String, String> each : myPool.keySet()) {
      MavenEmbedderWrapper embedder = myPool.get(each);
      if (embedder == null) continue; // collected
      if (!includeInUse && myEmbeddersInUse.contains(embedder)) continue;
      func.fun(embedder);
    }
  }
}
