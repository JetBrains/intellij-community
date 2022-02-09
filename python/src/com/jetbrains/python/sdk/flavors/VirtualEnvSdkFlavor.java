// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.sdk.BasePySdkExtKt;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUtil;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User : catherine
 */
public final class VirtualEnvSdkFlavor extends CPythonSdkFlavor {
  private VirtualEnvSdkFlavor() {
  }
  private final static Set<String> NAMES = Set.of("jython", "pypy", "python", "jython.bat", "pypy.exe", "python.exe");

  public static VirtualEnvSdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(VirtualEnvSdkFlavor.class);
  }

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    return ReadAction.compute(() -> {
      final List<String> candidates = new ArrayList<>();
      final VirtualFile baseDirFromModule = module == null ? null : BasePySdkExtKt.getBaseDir(module);
      final Path baseDirFromContext = context == null ? null : context.getUserData(PySdkExtKt.getBASE_DIR());

      if (baseDirFromModule != null) {
        candidates.addAll(findInBaseDirectory(baseDirFromModule));
      }
      else if (baseDirFromContext != null) {
        final VirtualFile dir = VfsUtil.findFile(baseDirFromContext, false);
        if (dir != null) {
          candidates.addAll(findInBaseDirectory(dir));
        }
      }

      final VirtualFile path = getDefaultLocation();
      if (path != null) {
        candidates.addAll(findInBaseDirectory(path));
      }

      final VirtualFile pyEnvLocation = getPyEnvDefaultLocations();
      if (pyEnvLocation != null) {
        candidates.addAll(findInBaseDirectory(pyEnvLocation));
      }

      return ContainerUtil.filter(candidates, PythonSdkUtil::isVirtualEnv);
    });
  }

  @Nullable
  private static VirtualFile getPyEnvDefaultLocations() {
    final String path = System.getenv().get("PYENV_ROOT");
    if (!StringUtil.isEmpty(path)) {
      final VirtualFile pyEnvRoot = LocalFileSystem.getInstance().findFileByPath(FileUtil.expandUserHome(path).replace('\\', '/'));
      if (pyEnvRoot != null) {
        return pyEnvRoot.findFileByRelativePath("versions");
      }
    }
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\','/'));
    if (userHome != null) {
      return userHome.findFileByRelativePath(".pyenv/versions");
    }
    return null;
  }

  public static VirtualFile getDefaultLocation() {
    final String path = System.getenv().get("WORKON_HOME");
    if (!StringUtil.isEmpty(path)) {
      return LocalFileSystem.getInstance().findFileByPath(FileUtil.expandUserHome(path).replace('\\','/'));
    }

    final VirtualFile userHome = VfsUtil.getUserHomeDir();
    if (userHome != null) {
      return userHome.findChild(".virtualenvs");
    }
    return null;
  }

  private static Collection<String> findInBaseDirectory(@Nullable VirtualFile baseDir) {
    List<String> candidates = new ArrayList<>();
    if (baseDir != null) {
      baseDir.refresh(true, false);
      VirtualFile[] suspects = baseDir.getChildren();
      for (VirtualFile child : suspects) {
        candidates.addAll(findInRootDirectory(child));
      }
    }
    return candidates;
  }

  @NotNull
  public static Collection<String> findInRootDirectory(@Nullable VirtualFile rootDir) {
    final List<String> candidates = new ArrayList<>();
    if (rootDir != null && rootDir.isDirectory()) {
      final VirtualFile bin = rootDir.findChild("bin");
      final VirtualFile scripts = rootDir.findChild("Scripts");
      if (bin != null && bin.isDirectory()) {
        final String interpreter = findInterpreter(bin);
        if (interpreter != null) candidates.add(interpreter);
      }
      if (scripts != null && scripts.isDirectory()) {
        final String interpreter = findInterpreter(scripts);
        if (interpreter != null) candidates.add(interpreter);
      }
      if (candidates.isEmpty()) {
        final String interpreter = findInterpreter(rootDir);
        if (interpreter != null) candidates.add(interpreter);
      }
    }
    return candidates;
  }

  @Nullable
  private static String findInterpreter(VirtualFile dir) {
    for (VirtualFile child : dir.getChildren()) {
      if (!child.isDirectory()) {
        final String childName = StringUtil.toLowerCase(child.getName());
        if (NAMES.contains(childName)) {
          final String childPath = child.getPath();
          return SystemInfo.isWindows ? FileUtil.toSystemDependentName(childPath) : childPath;
        }
      }
    }
    return null;
  }

  @Override
  public boolean isValidSdkPath(@NotNull File file) {
    if (!super.isValidSdkPath(file)) return false;
    return PythonSdkUtil.getVirtualEnvRoot(file.getPath()) != null;
  }

  @Override
  public @NotNull Icon getIcon() {
    return PythonIcons.Python.Virtualenv;
  }
}
