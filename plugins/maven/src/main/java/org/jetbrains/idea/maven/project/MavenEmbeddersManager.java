// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MavenEmbeddersManager {
  public static final Key FOR_DEPENDENCIES_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_DEPENDENCIES_RESOLVE");
  public static final Key FOR_PLUGINS_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_PLUGINS_RESOLVE");
  public static final Key FOR_FOLDERS_RESOLVE = Key.create(MavenEmbeddersManager.class + ".FOR_FOLDERS_RESOLVE");
  public static final Key FOR_POST_PROCESSING = Key.create(MavenEmbeddersManager.class + ".FOR_POST_PROCESSING");
  public static final Key FOR_MODEL_READ = Key.create(MavenEmbeddersManager.class + ".FOR_MODEL_READ");

  // will always regardless to 'work offline' setting
  public static final Key FOR_DOWNLOAD = Key.create(MavenEmbeddersManager.class + ".FOR_DOWNLOAD");

  private final Project myProject;

  private final Map<Pair<Key, String>, MavenEmbedderWrapper> myPool = ContainerUtil.createSoftValueMap();
  private final Set<MavenEmbedderWrapper> myEmbeddersInUse = new HashSet<>();

  public MavenEmbeddersManager(Project project) {
    myProject = project;
  }

  public synchronized void reset() {
    releasePooledEmbedders(false);
  }

  @NotNull
  // used in third-party plugins
  public synchronized MavenEmbedderWrapper getEmbedder(@NotNull MavenProject mavenProject, Key kind) {
    String baseDir = MavenUtil.getBaseDir(mavenProject.getDirectoryFile()).toString();
    return getEmbedder(kind, baseDir);
  }

  @NotNull
  public synchronized MavenEmbedderWrapper getEmbedder(Key kind, @NotNull String multiModuleProjectDirectory) {
    Pair<Key, String> key = Pair.create(kind, multiModuleProjectDirectory);
    MavenEmbedderWrapper result = myPool.get(key);
    boolean alwaysOnline = kind == FOR_DOWNLOAD;

    if (result == null) {
      result = MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline, multiModuleProjectDirectory);
      myPool.put(key, result);
    }

    if (myEmbeddersInUse.contains(result)) {
      MavenLog.LOG.warn("embedder " + key + " is already used");
      return MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline, multiModuleProjectDirectory);
    }

    myEmbeddersInUse.add(result);
    return result;
  }

  /**
   * @deprecated use {@link MavenEmbeddersManager#getEmbedder(Key, String)} instead
   */
  @Deprecated
  @NotNull
  // used in third-party plugins
  public synchronized MavenEmbedderWrapper getEmbedder(Key kind, String ignoredWorkingDirectory, @NotNull String multiModuleProjectDirectory) {
    return getEmbedder(kind, multiModuleProjectDirectory);
  }

  public synchronized void release(@NotNull MavenEmbedderWrapper embedder) {
    if (!myEmbeddersInUse.contains(embedder)) {
      embedder.release();
      return;
    }

    embedder.reset();
    myEmbeddersInUse.remove(embedder);
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
  }

  private void forEachPooled(boolean includeInUse, Function<MavenEmbedderWrapper, ?> func) {
    for (var each : myPool.keySet()) {
      MavenEmbedderWrapper embedder = myPool.get(each);
      if (embedder == null) continue; // collected
      if (!includeInUse && myEmbeddersInUse.contains(embedder)) continue;
      func.fun(embedder);
    }
  }

  public void execute(@NotNull MavenProject mavenProject,
                      @NotNull MavenProjectsTree tree,
                      @NotNull Key embedderKind,
                      @NotNull MavenConsole console,
                      @NotNull MavenProgressIndicator process,
                      @NotNull MavenEmbeddersManager.EmbedderTask task) throws MavenProcessCanceledException {
    var baseDir = MavenUtil.getBaseDir(mavenProject.getDirectoryFile()).toString();
    execute(baseDir, tree, embedderKind, console, process, task);
  }

  public void execute(@NotNull String baseDir,
                      @NotNull MavenProjectsTree tree,
                      @NotNull Key embedderKind,
                      @NotNull MavenConsole console,
                      @NotNull MavenProgressIndicator process,
                      @NotNull MavenEmbeddersManager.EmbedderTask task) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = getEmbedder(embedderKind, baseDir);
    embedder.customizeForResolve(console, process, false, tree.getWorkspaceMap(), null);
    try {
      task.run(embedder);
    }
    finally {
      release(embedder);
    }
  }

  public interface EmbedderTask {
    void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException;
  }
}
