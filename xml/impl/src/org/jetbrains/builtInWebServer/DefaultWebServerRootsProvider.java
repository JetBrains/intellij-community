package org.jetbrains.builtInWebServer;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairFunction;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DefaultWebServerRootsProvider extends WebServerRootsProvider {
  @Nullable
  @Override
  public Pair<VirtualFile, Pair<VirtualFile, String>> resolve(@NotNull String path, @NotNull Project project) {
    PairFunction<String, VirtualFile, VirtualFile> resolver;
    if (PlatformUtils.isIntelliJ()) {
      int index = path.indexOf('/');
      if (index > 0 && !path.regionMatches(!SystemInfo.isFileSystemCaseSensitive, 0, project.getName(), 0, index)) {
        String moduleName = path.substring(0, index);
        AccessToken token = ReadAction.start();
        Module module;
        try {
          module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        }
        finally {
          token.finish();
        }

        if (module != null && !module.isDisposed()) {
          path = path.substring(index + 1);
          resolver = WebServerPathToFileManager.getInstance(project).getResolver(path);

          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          Couple<VirtualFile> result = resolve(path, moduleRootManager.getSourceRoots(), resolver);
          if (result == null) {
            result = resolve(path, moduleRootManager.getContentRoots(), resolver);
          }
          if (result != null) {
            return Pair.create(result.first, Pair.create(result.second, module.getName()));
          }
        }
      }
    }

    Module[] modules;
    AccessToken token = ReadAction.start();
    try {
      modules = ModuleManager.getInstance(project).getModules();
    }
    finally {
      token.finish();
    }

    resolver = WebServerPathToFileManager.getInstance(project).getResolver(path);
    Pair<VirtualFile, Pair<VirtualFile, String>> result = findByRelativePath(project, path, modules, true, resolver);
    if (result == null) {
      // let's find in content roots
      return findByRelativePath(project, path, modules, false, resolver);
    }
    else {
      return result;
    }
  }

  @Nullable
  @Override
  public Pair<VirtualFile, String> getRoot(@NotNull final VirtualFile file, @NotNull final Project project) {
    return new ReadAction<Pair<VirtualFile, String>>() {
      protected void run(@NotNull final Result<Pair<VirtualFile, String>> result) {
        VirtualFile root = null;
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        if (fileIndex.isInSourceContent(file)) {
          root = fileIndex.getSourceRootForFile(file);
        }
        else if (fileIndex.isInContent(file)) {
          root = fileIndex.getContentRootForFile(file);
        }
        else if (fileIndex.isInLibraryClasses(file)) {
          root = fileIndex.getClassRootForFile(file);
        }
        assert root != null : file.getPresentableUrl();
        result.setResult(Pair.create(root, getModuleNameQualifier(project, fileIndex.getModuleForFile(file))));
      }
    }.execute().getResultObject();
  }

  @Nullable
  private static String getModuleNameQualifier(@NotNull Project project, @Nullable Module module) {
    if (module != null &&
        PlatformUtils.isIntelliJ() &&
        !(module.getName().equalsIgnoreCase(project.getName()) || BuiltInWebServer.compareNameAndProjectBasePath(module.getName(), project))) {
      return module.getName();
    }
    return null;
  }

  @Nullable
  private static Couple<VirtualFile> resolve(@NotNull String path, @NotNull VirtualFile[] roots, @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver) {
    for (VirtualFile root : roots) {
      VirtualFile file = resolver.fun(path, root);
      if (file != null) {
        return Couple.of(file, root);
      }
    }
    return null;
  }

  @Nullable
  private static Pair<VirtualFile, Pair<VirtualFile, String>> findByRelativePath(@NotNull Project project,
                                                                                 @NotNull String path,
                                                                                 @NotNull Module[] modules,
                                                                                 boolean inSourceRoot,
                                                                                 @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver) {
    for (Module module : modules) {
      if (!module.isDisposed()) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        Couple<VirtualFile> result = resolve(path, inSourceRoot ? moduleRootManager.getSourceRoots() : moduleRootManager.getContentRoots(), resolver);
        if (result != null) {
          return Pair.create(result.first, Pair.create(result.second, getModuleNameQualifier(project, module)));
        }
      }
    }
    return null;
  }
}