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
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.file.Files;
import java.nio.file.Path;
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
    String embedderDir = guessExistingEmbedderDir(multiModuleProjectDirectory);

    Pair<Key, String> key = Pair.create(kind, embedderDir);
    MavenEmbedderWrapper result = myPool.get(key);
    boolean alwaysOnline = kind == FOR_DOWNLOAD;

    if (result == null) {
      result = createEmbedder(embedderDir, alwaysOnline);
      myPool.put(key, result);
    }

    if (myEmbeddersInUse.contains(result)) {
      MavenLog.LOG.warn("embedder " + key + " is already used");
      return createEmbedder(embedderDir, alwaysOnline);
    }

    myEmbeddersInUse.add(result);
    return result;
  }

  private String guessExistingEmbedderDir(@NotNull String multiModuleProjectDirectory) {
    var dir = multiModuleProjectDirectory;
    if (dir.isBlank()) {
      MavenLog.LOG.warn("Maven project directory is blank. Using project base path");
      dir = myProject.getBasePath();
    }
    if (null == dir || dir.isBlank()) {
      MavenLog.LOG.warn("Maven project directory is blank. Using tmp dir");
      dir = System.getProperty("java.io.tmpdir");
    }
    Path originalPath = Path.of(dir).toAbsolutePath();
    Path path = originalPath;
    while (null != path && !Files.exists(path)) {
      MavenLog.LOG.warn(String.format("Maven project %s directory does not exist. Using parent", path));
      path = path.getParent();
    }
    if (null == path) {
      MavenLog.LOG.warn("Could not determine maven project directory: " + multiModuleProjectDirectory);
      return originalPath.toString();
    }
    return path.toString();
  }

  @NotNull
  private MavenEmbedderWrapper createEmbedder(@NotNull String multiModuleProjectDirectory, boolean alwaysOnline) {
    return MavenServerManager.getInstance().createEmbedder(myProject, alwaysOnline, multiModuleProjectDirectory);
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
                      @NotNull Key embedderKind,
                      @NotNull MavenEmbeddersManager.EmbedderTask task) throws MavenProcessCanceledException {
    var baseDir = MavenUtil.getBaseDir(mavenProject.getDirectoryFile()).toString();
    execute(baseDir, embedderKind, task);
  }

  public void execute(@NotNull String baseDir,
                      @NotNull Key embedderKind,
                      @NotNull MavenEmbeddersManager.EmbedderTask task) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = getEmbedder(embedderKind, baseDir);
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
