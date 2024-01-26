// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.sdk.BasePySdkExtKt;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User : catherine
 */
public final class VirtualEnvSdkFlavor extends CPythonSdkFlavor<PyFlavorData.Empty> {
  private VirtualEnvSdkFlavor() {
  }

  private final static Set<String> NAMES = Set.of("jython", "pypy", "python", "jython.bat", "pypy.exe", "python.exe");
  private static final Pattern PATTERN = Pattern.compile("");

  public static VirtualEnvSdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(VirtualEnvSdkFlavor.class);
  }

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @Override
  public @NotNull Class<PyFlavorData.Empty> getFlavorDataClass() {
    return PyFlavorData.Empty.class;
  }

  @Override
  public @NotNull Collection<@NotNull Path> suggestLocalHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    return ReadAction.compute(() -> {
      final var candidates = new ArrayList<Path>();

      final VirtualFile baseDirFromModule = module == null ? null : BasePySdkExtKt.getBaseDir(module);
      final Path baseDirFromContext = context == null ? null : context.getUserData(PySdkExtKt.getBASE_DIR());

      var reader = VirtualEnvReader.getInstance();
      if (baseDirFromModule != null) {
        candidates.addAll(reader.findLocalInterpreters(baseDirFromModule.toNioPath(), NAMES, PATTERN));
      } else if (baseDirFromContext != null) {
        final VirtualFile dir = VfsUtil.findFile(baseDirFromContext, false);
        if (dir != null) {
          candidates.addAll(reader.findLocalInterpreters(dir.toNioPath(), NAMES, PATTERN));
        }
      }

      candidates.addAll(reader.findVEnvInterpreters(NAMES, PATTERN));
      candidates.addAll(reader.findPyenvInterpreters(NAMES, PATTERN));

      return ContainerUtil.filter(candidates, (Path path) -> {
        return PythonSdkUtil.isVirtualEnv(path.toString());
      });
    });
  }

  public static Path getDefaultLocation() {
    return VirtualEnvReader.getInstance().getVEnvRootDir();
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