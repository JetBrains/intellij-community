// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class PySignatureCacheManager {
  @NonNls
  static final String RETURN_TYPE = "<RETURN_TYPE>";

  public static PySignatureCacheManager getInstance(Project project) {
    return ServiceManager.getService(project, PySignatureCacheManager.class);
  }

  public static String signatureToString(PySignature signature) {
    return signature.getFunctionName() + "\t" + StringUtil.join(arguments(signature), "\t") +
           (signature.getReturnType() != null
            ? "\t" + StringUtil.join(
             signature.getReturnType().getTypesList().stream().map(s -> RETURN_TYPE + ":" + s).collect(Collectors.toList()), "\t") : "");
  }

  private static List<String> arguments(PySignature signature) {
    List<String> res = new ArrayList<>();
    for (PySignature.NamedParameter param : signature.getArgs()) {
      res.add(param.getName() + ":" + param.getTypeQualifiedName());
    }
    return res;
  }

  public abstract void recordSignature(@NotNull PySignature signature);

  @Nullable
  public abstract String findParameterType(@NotNull PyFunction function, @NotNull String name);

  @Nullable
  public abstract PySignature findSignature(@NotNull PyFunction function);

  public abstract boolean clearCache();
}
