// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(name = "TerminalCommandHistoryManager", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
public final class TerminalCommandHistoryManager implements PersistentStateComponent<TerminalCommandHistoryManager.State> {

  private static final Logger LOG = Logger.getInstance(TerminalCommandHistoryManager.class);
  private static final AtomicBoolean PRUNE_SCHEDULED = new AtomicBoolean(false);

  private final Map<String, CommandHistoryFileInfo> myMap = new ConcurrentHashMap<>();

  public @Nullable Path getOrCreateCommandHistoryFile(@Nullable String commandHistoryFileName, @NotNull Project project) {
    Path historyDir = getCommandHistoryDir();
    if (historyDir == null) {
      return null;
    }
    String projectPath = Objects.requireNonNull(project.getBasePath());
    if (commandHistoryFileName != null) {
      CommandHistoryFileInfo info = myMap.get(commandHistoryFileName);
      if (info != null && projectPath.equals(info.myProjectPath)) {
        Path path = historyDir.resolve(commandHistoryFileName);
        if (Files.exists(path)) {
          info.myAccessTime = now();
          return path;
        }
        myMap.remove(commandHistoryFileName);
      }
    }
    try {
      File tmpFile = FileUtil.createTempFile(historyDir.toFile(), project.getName() + "-history", null, true, false);
      Path filePath = tmpFile.toPath();
      String filename = getFilename(filePath);
      myMap.put(filename, createFileInfo(filename, projectPath));
      return filePath;
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  private static @NotNull CommandHistoryFileInfo createFileInfo(@NotNull String filename, @NotNull String projectPath) {
    CommandHistoryFileInfo info = new CommandHistoryFileInfo();
    info.myFilename = filename;
    info.myProjectPath = projectPath;
    info.myAccessTime = now();
    return info;
  }

  private static long now() {
    return System.currentTimeMillis();
  }

  private static @Nullable Path getCommandHistoryDir() {
    Path dir = Paths.get(PathManager.getSystemPath()).resolve("terminal/history");
    if (Files.isDirectory(dir)) {
      return dir;
    }
    if (Files.exists(dir)) {
      LOG.warn("Not a directory " + dir);
      return null;
    }
    deleteOldHistoryDir();
    try {
      Files.createDirectories(dir);
      return dir;
    }
    catch (IOException e) {
      LOG.warn("Cannot create " + dir, e);
      return null;
    }
  }

  // To be removed in 2021.3
  private static void deleteOldHistoryDir() {
    Path dir = PathManager.getConfigDir().resolve("terminal/history");
    if (Files.isDirectory(dir)) {
      try {
        FileUtil.delete(dir);
        LOG.info("Old config/terminal/history/ deleted: " + dir);
      }
      catch (IOException e) {
        LOG.warn("Cannot delete old terminal/history/", e);
      }
    }

    Path parentDir = dir.getParent();
    if (!Files.isDirectory(parentDir)) {
      LOG.info("Old terminal/ directory does not exist or not a directory: " + parentDir);
      return;
    }

    boolean empty = false;
    try (Stream<Path> s = Files.list(parentDir)) {
      empty = s.findAny().isEmpty();
    }
    catch (IOException e) {
      LOG.warn("Cannot list files in " + parentDir, e);
    }
    if (empty) {
      try {
        FileUtil.delete(parentDir);
        LOG.info("Old terminal/ deleted: " + dir);
      }
      catch (IOException e) {
        LOG.warn("Cannot delete old terminal/", e);
      }
    }
  }

  public void retainCommandHistoryFiles(@NotNull List<String> historyFileNamesToKeep, @NotNull Project project) {
    String projectPath = Objects.requireNonNull(project.getBasePath());
    List<String> toRemove = new ArrayList<>();
    for (CommandHistoryFileInfo info : myMap.values()) {
      if (projectPath.equals(info.myProjectPath) && !historyFileNamesToKeep.contains(info.myFilename)) {
        toRemove.add(info.myFilename);
      }
    }
    deleteHistoryFiles(toRemove, "closed sessions");
  }

  private void deleteHistoryFiles(@NotNull List<String> historyFileNamesToRemove, @NotNull String reason) {
    Path historyDir = historyFileNamesToRemove.isEmpty() ? null : getCommandHistoryDir();
    if (historyDir == null) {
      return;
    }
    LOG.info("Deleting " + historyFileNamesToRemove + " (" + reason + ")");
    for (String historyFileName : historyFileNamesToRemove) {
      myMap.remove(historyFileName);
      Path file = historyDir.resolve(historyFileName);
      if (Files.exists(file)) {
        try {
          Files.delete(file);
        }
        catch (IOException e) {
          LOG.warn("Cannot delete " + historyFileName, e);
        }
      }
    }
  }

  @Override
  public @NotNull TerminalCommandHistoryManager.State getState() {
    State state = new State();
    state.myHistoryFileInfoList = ContainerUtil.nullize(new ArrayList<>(myMap.values()));
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    myMap.clear();
    for (CommandHistoryFileInfo info : state.myHistoryFileInfoList) {
      myMap.put(info.myFilename, info);
    }
  }

  public static @NotNull TerminalCommandHistoryManager getInstance() {
    if (PRUNE_SCHEDULED.compareAndSet(false, true)) {
      JobScheduler.getScheduler().schedule(() -> pruneOutdated(), 4, TimeUnit.MINUTES);
    }
    return ApplicationManager.getApplication().getService(TerminalCommandHistoryManager.class);
  }

  private static void pruneOutdated() {
    Path historyDir = getCommandHistoryDir();
    if (historyDir != null) {
      try (Stream<Path> s = Files.list(historyDir)) {
        getInstance().doPruneOutdated(s.collect(Collectors.toList()));
      }
      catch (IOException e) {
        LOG.warn("Cannot list files in " + historyDir, e);
      }
    }
  }

  private void doPruneOutdated(@NotNull List<Path> existingHistoryFiles) {
    Set<String> existingHistoryFilenames = ContainerUtil.map2Set(existingHistoryFiles, TerminalCommandHistoryManager::getFilename);
    long allowed = now() - TimeUnit.DAYS.toMillis(30);
    List<String> toRemove = new ArrayList<>();
    for (CommandHistoryFileInfo info : myMap.values()) {
      if (info.myAccessTime <= allowed || !existingHistoryFilenames.contains(info.myFilename)) {
        toRemove.add(info.myFilename);
      }
    }
    for (String filename : existingHistoryFilenames) {
      if (!myMap.containsKey(filename)) {
        toRemove.add(filename);
      }
    }
    deleteHistoryFiles(toRemove, "outdated sessions");
  }

  private static @NotNull String getFilename(@NotNull Path path) {
    return PathUtil.getFileName(path.toString());
  }

  public static @Nullable String getFilename(@Nullable String path) {
    return path == null ? null : PathUtil.getFileName(path);
  }

  public static final class State {
    @XCollection(propertyElementName = "command-history-files")
    private List<CommandHistoryFileInfo> myHistoryFileInfoList;
  }

  @Tag("command-history-file")
  public static final class CommandHistoryFileInfo {

    @Attribute("filename")
    private volatile String myFilename;

    @Attribute("project-path")
    private String myProjectPath;

    @Attribute("last-access-time")
    private volatile long myAccessTime;
  }
}
