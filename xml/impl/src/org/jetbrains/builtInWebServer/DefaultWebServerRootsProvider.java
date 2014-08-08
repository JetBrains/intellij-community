/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.builtInWebServer;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairFunction;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DefaultWebServerRootsProvider extends WebServerRootsProvider {
  @Nullable
  @Override
  public PathInfo resolve(@NotNull String path, @NotNull Project project) {
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
          PathInfo result = resolve(path, moduleRootManager.getSourceRoots(), resolver, moduleName);
          if (result == null) {
            result = resolve(path, moduleRootManager.getContentRoots(), resolver, moduleName);
          }
          if (result != null) {
            return result;
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
    PathInfo result = findByRelativePath(project, path, modules, true, resolver);
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
  public PathInfo getRoot(@NotNull VirtualFile file, @NotNull Project project) {
    AccessToken token = ReadAction.start();
    try {
      VirtualFile root;
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
      else {
        // excluded
        return null;
      }
      assert root != null : file.getPresentableUrl();
      return new PathInfo(file, root, getModuleNameQualifier(project, fileIndex.getModuleForFile(file)));
    }
    finally {
      token.finish();
    }
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
  private static PathInfo resolve(@NotNull String path, @NotNull VirtualFile[] roots, @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver, @Nullable String moduleName) {
    for (VirtualFile root : roots) {
      VirtualFile file = resolver.fun(path, root);
      if (file != null) {
        return new PathInfo(file, root, moduleName);
      }
    }
    return null;
  }

  @Nullable
  private static PathInfo findByRelativePath(@NotNull Project project,
                                             @NotNull String path,
                                             @NotNull Module[] modules,
                                             boolean inSourceRoot,
                                             @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver) {
    for (Module module : modules) {
      if (!module.isDisposed()) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        PathInfo result = resolve(path, inSourceRoot ? moduleRootManager.getSourceRoots() : moduleRootManager.getContentRoots(), resolver, null);
        if (result != null) {
          result.moduleName = getModuleNameQualifier(project, module);
          return result;
        }
      }
    }
    return null;
  }
}