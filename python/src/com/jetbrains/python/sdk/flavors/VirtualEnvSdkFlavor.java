// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.icons.PythonIcons;
import com.jetbrains.python.sdk.BasePySdkExtKt;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.venvReader.VirtualEnvReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

/**
 * User : catherine
 */
public final class VirtualEnvSdkFlavor extends CPythonSdkFlavor<PyFlavorData.Empty> {
  private VirtualEnvSdkFlavor() {
  }


  public static VirtualEnvSdkFlavor getInstance() {
    return EP_NAME.findExtension(VirtualEnvSdkFlavor.class);
  }

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @Override
  public @NotNull Class<PyFlavorData.Empty> getFlavorDataClass() {
    return PyFlavorData.Empty.class;
  }

  @RequiresBackgroundThread
  @Override
  protected @NotNull Collection<@NotNull Path> suggestLocalHomePathsImpl(@Nullable Module module, @Nullable UserDataHolder context) {
    return ReadAction.compute(() -> {
      final var candidates = new ArrayList<Path>();

      final VirtualFile baseDirFromModule = module == null ? null : BasePySdkExtKt.getBaseDir(module);
      final Path baseDirFromContext = context == null ? null : context.getUserData(PySdkExtKt.getBASE_DIR());

      var reader = VirtualEnvReader.getInstance();
      if (baseDirFromModule != null) {
        candidates.addAll(reader.findLocalInterpreters(baseDirFromModule.toNioPath()));
      } else if (baseDirFromContext != null) {
        final VirtualFile dir = VfsUtil.findFile(baseDirFromContext, false);
        if (dir != null) {
          candidates.addAll(reader.findLocalInterpreters(dir.toNioPath()));
        }
      }

      candidates.addAll(reader.findVEnvInterpreters());
      candidates.addAll(reader.findPyenvInterpreters());

      return ContainerUtil.filter(candidates, (Path path) -> {
        return PythonSdkUtil.isVirtualEnv(path.toString());
      });
    });
  }

  @Override
  public boolean isValidSdkPath(@NotNull String pathStr) {
    if (!super.isValidSdkPath(pathStr)) {
      return false;
    }

    return PythonSdkUtil.getVirtualEnvRoot(pathStr) != null;
  }

  @Override
  public @NotNull Icon getIcon() {
    return PythonIcons.Python.Virtualenv;
  }
}