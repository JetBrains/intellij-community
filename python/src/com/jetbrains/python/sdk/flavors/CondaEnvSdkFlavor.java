/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.flavors;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer;
import icons.PythonIcons;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor.findInRootDirectory;

public class CondaEnvSdkFlavor extends CPythonSdkFlavor {
  private CondaEnvSdkFlavor() {
  }

  public static CondaEnvSdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(CondaEnvSdkFlavor.class);
  }

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    final List<String> results = new ArrayList<>();
    final Sdk sdk = ReadAction.compute(() -> PythonSdkUtil.findPythonSdk(module));
    try {
      final List<String> environments = PyCondaRunKt.listCondaEnvironments(sdk);
      for (String environment : environments) {
        results.addAll(ReadAction.compute(() -> {
          final VirtualFile root = StandardFileSystems.local().findFileByPath(environment);
          final Collection<String> found = findInRootDirectory(root);
          if (PyCondaSdkCustomizer.Companion.getInstance().getDetectEnvironmentsOutsideEnvsFolder()) {
            return found;
          }
          else {
            return StreamEx.of(found).filter(s -> getCondaEnvRoot(s) != null).toList();
          }
        }));
      }
    }
    catch (ExecutionException e) {
      return Collections.emptyList();
    }
    return results;
  }

  @Override
  public boolean isValidSdkPath(@NotNull File file) {
    if (!super.isValidSdkPath(file)) return false;
    return PythonSdkUtil.isConda(file.getPath());
  }

  @Nullable
  public static File getCondaEnvRoot(@NotNull final String binaryPath) {
    final File binary = new File(binaryPath);
    final File parent = binary.getParentFile();
    if (parent == null) return null;
    final File parent2 = parent.getParentFile();
    if (parent2 == null) return null;
    final File parent3 = parent2.getParentFile();
    if (parent3 != null && "envs".equals(parent3.getName())) {
      return parent2;
    }
    else if ("envs".equals(parent2.getName())) {
      return parent;
    }
    else {
      return null;
    }
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Anaconda;
  }
}
