// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.ast.PyAstFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class PySignatureCacheManager {
  @ApiStatus.Internal public static final @NonNls String RETURN_TYPE = "<RETURN_TYPE>";

  public static PySignatureCacheManager getInstance(Project project) {
    return project.getService(PySignatureCacheManager.class);
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

  public abstract @Nullable String findParameterType(@NotNull PyAstFunction function, @NotNull String name);

  public abstract @Nullable PySignature findSignature(@NotNull PyAstFunction function);

  public abstract boolean clearCache();
}
