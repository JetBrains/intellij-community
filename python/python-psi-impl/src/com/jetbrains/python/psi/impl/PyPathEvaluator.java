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
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;


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
    if (expression.isCalleeText("resolve")) {
      PyReferenceExpression callee = (PyReferenceExpression)expression.getCallee();
      if (callee != null && callee.getQualifier() instanceof PyCallExpression) {
        return evaluate(callee.getQualifier());
      }
    }
    if (expression.isCalleeText("Path") && args.length >= 1) {
      return evaluate(args[0]);
    }
    if (expression.isCalleeText(PyNames.DIRNAME) && args.length == 1) {
      Object argValue = evaluate(args[0]);
      return argValue instanceof String ? Paths.get((String) argValue).getParent().toFile().getPath() : null;
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
        String path = Paths.get(myContainingFilePath).resolveSibling((String)argValue).toFile().getPath();
        return FileUtil.toSystemIndependentName(path);
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
      return FileUtil.toSystemIndependentName(myContainingFilePath);
    }
    if ("parent".equals(expression.getName()) && expression.isQualified()) {
      Object qualifier = evaluate(expression.getQualifier());
      if (qualifier instanceof String) {
        return Paths.get((String)qualifier).getParent().toFile().getPath();
      }
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
        result = FileUtil.toSystemIndependentName(Paths.get(result, (String)arg).toFile().getPath());
      }
    }
    return result;
  }

  @Override
  protected @Nullable Object evaluateBinary(@NotNull PyBinaryExpression expression) {
    final PyElementType operator = expression.getOperator();
    if (operator == PyTokenTypes.DIV) {
      final Object lhs = evaluate(expression.getLeftExpression());
      final Object rhs = evaluate(expression.getRightExpression());
      if (lhs instanceof String && rhs instanceof String) {
        return FileUtil.toSystemIndependentName(Paths.get((String)lhs, (String)rhs).toFile().getPath());
      }
    }
    return super.evaluateBinary(expression);
  }
}
