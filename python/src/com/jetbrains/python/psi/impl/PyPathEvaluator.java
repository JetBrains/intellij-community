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
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class PyPathEvaluator extends PyEvaluator {

  private final String myContainingFilePath;

  public PyPathEvaluator(String containingFilePath) {
    myContainingFilePath = containingFilePath;
  }

  @Nullable
  public static String evaluatePath(PyExpression expr) {
    if (expr == null) {
      return null;
    }
    VirtualFile vFile = expr.getContainingFile().getVirtualFile();
    Object result = new PyPathEvaluator(vFile == null ? null : vFile.getPath()).evaluate(expr);
    return result instanceof String ? (String) result : null;
  }

  @Override
  @Nullable
  protected Object evaluateCall(@NotNull PyCallExpression expression) {
    final PyExpression[] args = expression.getArguments();
    if (expression.isCalleeText(PyNames.DIRNAME) && args.length == 1) {
      Object argValue = evaluate(args[0]);
      return argValue instanceof String ? new File((String) argValue).getParent() : null;
    }
    else if (expression.isCalleeText(PyNames.JOIN) && args.length >= 1) {
      return evaluatePathInJoin(args, args.length);
    }
    else if (expression.isCalleeText(PyNames.NORMPATH, PyNames.REALPATH) && args.length == 1) {
      // we don't care about the exact transformation performed by python but need to preserve the availability of the path
      return evaluate(args[0]);
    }
    else if (expression.isCalleeText(PyNames.ABSPATH) && args.length == 1) {
      Object argValue = evaluate(args[0]);
      // relative to directory of 'containingFilePath', not file
      if (!(argValue instanceof String)) {
        return null;
      }
      if (FileUtil.isAbsolutePlatformIndependent((String)argValue)) {
        return argValue;
      }
      else {
        String path = new File(new File(myContainingFilePath).getParent(), (String)argValue).getPath();
        return path.replace("\\", "/");
      }
    }
    return super.evaluateCall(expression);
  }

  @Override
  @Nullable
  protected Object evaluateReference(@NotNull PyReferenceExpression expression) {
    if (PyNames.PARDIR.equals(expression.getName())) {
      return "..";
    }
    else if (PyNames.CURDIR.equals(expression.getName())) {
      return ".";
    }
    if (!expression.isQualified() && PyNames.FILE.equals(expression.getReferencedName())) {
      return myContainingFilePath;
    }
    return super.evaluateReference(expression);
  }

  public String evaluatePathInJoin(PyExpression[] args, int endElement) {
    String result = null;
    for (int i = 0; i < endElement; i++) {
      Object arg = evaluate(args[i]);
      if (!(arg instanceof String)) {
        return null;
      }
      if (result == null) {
        result = (String)arg;
      }
      else {
        result = new File(result, (String)arg).getPath().replace("\\", "/");
      }
    }
    return result;
  }
}
