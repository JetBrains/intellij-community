package org.jetbrains.builtInWebServer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implement {@link WebServerRootsProvider} to add your provider
 */
public class WebServerPathToFileManager {
  private static final PairFunction<String, VirtualFile, VirtualFile> RELATIVE_PATH_RESOLVER = new PairFunction<String, VirtualFile, VirtualFile>() {
    @Nullable
    @Override
    public VirtualFile fun(String path, VirtualFile parent) {
      return parent.findFileByRelativePath(path);
    }
  };

  private static final PairFunction<String, VirtualFile, VirtualFile> EMPTY_PATH_RESOLVER = new PairFunction<String, VirtualFile, VirtualFile>() {
    @Nullable
    @Override
    public VirtualFile fun(String path, VirtualFile parent) {
      return BuiltInWebServer.findIndexFile(parent);
    }
  };

  private final Project project;

  final Cache<String, VirtualFile> pathToFileCache = CacheBuilder.newBuilder().maximumSize(512).expireAfterAccess(10, TimeUnit.MINUTES).build();
  // time to expire should be greater than pathToFileCache
  private final Cache<VirtualFile, PathInfo> fileToRoot = CacheBuilder.newBuilder().maximumSize(512).expireAfterAccess(11, TimeUnit.MINUTES).build();

  public static WebServerPathToFileManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, WebServerPathToFileManager.class);
  }

  public WebServerPathToFileManager(@NotNull Application application, @NotNull Project project) {
    this.project = project;
    application.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileContentChangeEvent) {
            VirtualFile file = ((VFileContentChangeEvent)event).getFile();
            for (WebServerRootsProvider rootsProvider : WebServerRootsProvider.EP_NAME.getExtensions()) {
              if (rootsProvider.isClearCacheOnFileContentChanged(file)) {
                clearCache();
                break;
              }
            }
          }
          else {
            clearCache();
            break;
          }
        }
      }
    });
    project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        clearCache();
      }
    });
  }

  private void clearCache() {
    pathToFileCache.invalidateAll();
    fileToRoot.invalidateAll();
  }

  @Nullable
  public VirtualFile get(@NotNull String path) {
    return get(path, true);
  }

  @Nullable
  public VirtualFile get(@NotNull String path, boolean cacheResult) {
    VirtualFile result = pathToFileCache.getIfPresent(path);
    if (result == null || !result.isValid()) {
      result = findByRelativePath(project, path);
      if (cacheResult && result != null && result.isValid()) {
        pathToFileCache.put(path, result);
      }
    }
    return result;
  }

  @Nullable
  public String getPath(@NotNull VirtualFile file) {
    PathInfo pathInfo = getRoot(file);
    return pathInfo == null ? null : pathInfo.getPath();
  }

  @Nullable
  public PathInfo getRoot(@NotNull VirtualFile child) {
    PathInfo result = fileToRoot.getIfPresent(child);
    if (result == null) {
      for (WebServerRootsProvider rootsProvider : WebServerRootsProvider.EP_NAME.getExtensions()) {
        result = rootsProvider.getRoot(child, project);
        if (result != null) {
          fileToRoot.put(child, result);
          break;
        }
      }
    }
    return result;
  }

  @Nullable
  VirtualFile findByRelativePath(@NotNull Project project, @NotNull String path) {
    for (WebServerRootsProvider rootsProvider : WebServerRootsProvider.EP_NAME.getExtensions()) {
      PathInfo result = rootsProvider.resolve(path, project);
      if (result != null) {
        fileToRoot.put(result.getChild(), result);
        return result.getChild();
      }
    }
    return null;
  }

  @NotNull
  public PairFunction<String, VirtualFile, VirtualFile> getResolver(@NotNull String path) {
    return path.isEmpty() ? EMPTY_PATH_RESOLVER : RELATIVE_PATH_RESOLVER;
  }
}