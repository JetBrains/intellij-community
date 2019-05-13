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
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author traff
 */
public abstract class PySignatureCacheManager {


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
    List<String> res = Lists.newArrayList();
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

  public abstract void clearCache();
}
