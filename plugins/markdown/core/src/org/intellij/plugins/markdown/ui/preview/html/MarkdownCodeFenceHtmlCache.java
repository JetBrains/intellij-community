// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.html;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider;
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class MarkdownCodeFenceHtmlCache implements Disposable {
  @NotNull public static final String MARKDOWN_FILE_PATH_KEY = "markdown-md5-file-path";

  @NotNull private final Alarm myAlarm = new Alarm(this);
  @NotNull private final Collection<MarkdownCodeFencePluginCacheCollector> myCodeFencePluginCaches = ContainerUtil.newConcurrentSet();
  @NotNull private final Collection<File> myAdditionalCacheToDelete = ContainerUtil.newConcurrentSet();

  public static MarkdownCodeFenceHtmlCache getInstance() {
    return ApplicationManager.getApplication().getService(MarkdownCodeFenceHtmlCache.class);
  }

  public MarkdownCodeFenceHtmlCache() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      scheduleClearCache();
    }

    final var listener = new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        final var fileTypeRegistry = FileTypeRegistry.getInstance();
        for (final var event: events) {
          if (event instanceof VFileDeleteEvent) {
            final var file = event.getFile();
            if (file != null && fileTypeRegistry.isFileOfType(file, MarkdownFileType.INSTANCE)) {
              myAdditionalCacheToDelete.addAll(processSourceFileToDelete(file, ContainerUtil.emptyList()));
            }
          }
        }
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, listener);
  }

  private static List<File> getPluginSystemPaths() {
    return CodeFenceGeneratingProvider.Companion.getAll$intellij_markdown_core().stream()
      .filter(MarkdownCodeFenceCacheableProvider.class::isInstance)
      .map(MarkdownCodeFenceCacheableProvider.class::cast)
      .map(provider -> provider.getCacheRootPath().toFile())
      .collect(Collectors.toList());
  }

  public Collection<File> collectFilesToRemove() {
    return myCodeFencePluginCaches.stream()
      .flatMap(cacheProvider -> processSourceFileToDelete(cacheProvider.getFile(), cacheProvider.getAliveCachedFiles()).stream())
      .collect(Collectors.toList());
  }

  private static Collection<File> processSourceFileToDelete(@NotNull VirtualFile sourceFile, @NotNull Collection<File> aliveCachedFiles) {
    Collection<File> filesToDelete = new HashSet<>();
    for (File codeFencePluginSystemPath : getPluginSystemPaths()) {
      for (File sourceFileCacheDirectory : getChildren(codeFencePluginSystemPath)) {
        if (isCachedSourceFile(sourceFileCacheDirectory, sourceFile) && aliveCachedFiles.isEmpty()) {
          filesToDelete.add(sourceFileCacheDirectory);
          continue;
        }

        for (File file : getChildren(sourceFileCacheDirectory)) {
          if (!isCachedSourceFile(sourceFileCacheDirectory, sourceFile) || aliveCachedFiles.contains(file)) continue;

          filesToDelete.add(file);
        }
      }
    }

    return filesToDelete;
  }

  private static File @NotNull [] getChildren(@NotNull File directory) {
    File[] files = directory.listFiles();
    return files != null ? files : ArrayUtilRt.EMPTY_FILE_ARRAY;
  }

  private static boolean isCachedSourceFile(@NotNull File sourceFileDir, @NotNull VirtualFile sourceFile) {
    return sourceFileDir.getName().equals(MarkdownUtil.INSTANCE.md5(sourceFile.getPath(), MARKDOWN_FILE_PATH_KEY));
  }

  public void registerCacheProvider(@NotNull MarkdownCodeFencePluginCacheCollector cacheCollector) {
    myCodeFencePluginCaches.add(cacheCollector);
  }

  private void scheduleClearCache() {
    myAlarm.addRequest(() -> {
      clearCache();

      scheduleClearCache();
    }, Registry.intValue("markdown.clear.cache.interval"));
  }

  public synchronized void clearCache() {
    Collection<File> filesToDelete = ContainerUtil.union(myAdditionalCacheToDelete, collectFilesToRemove());
    for (File file: filesToDelete) {
      FileUtil.delete(file);
    }

    myAdditionalCacheToDelete.clear();
    myCodeFencePluginCaches.clear();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myAlarm);
  }
}
