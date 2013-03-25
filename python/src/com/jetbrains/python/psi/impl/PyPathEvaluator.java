package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class PyPathEvaluator {

  private PyPathEvaluator() {
  }

  @Nullable
  public static String evaluate(PyExpression expr) {
    if (expr == null) {
      return null;
    }
    VirtualFile vFile = expr.getContainingFile().getVirtualFile();
    return evaluate(expr, vFile == null ? null : vFile.getPath(), new HashSet<PyExpression>());
  }

  @Nullable
  public static String evaluate(PyExpression expr, String containingFilePath, Set<PyExpression> visited) {
    if (expr == null || visited.contains(expr)) {
      return null;
    }
    visited.add(expr);
    if (expr instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)expr;
      final PyExpression[] args = call.getArguments();
      if (call.isCalleeText(PyNames.DIRNAME) && args.length == 1) {
        String argValue = evaluate(args[0], containingFilePath, visited);
        return argValue == null ? null : new File(argValue).getParent();
      }
      else if (call.isCalleeText(PyNames.JOIN) && args.length >= 1) {
        return evaluatePathInJoin(containingFilePath, args, args.length, visited);
      }
      else if (call.isCalleeText(PyNames.ABSPATH) && args.length == 1) {
        String argValue = evaluate(args[0], containingFilePath, visited);
        // relative to directory of 'containingFilePath', not file
        if (argValue == null) {
          return null;
        }
        if (FileUtil.isAbsolutePlatformIndependent(argValue)) {
          return argValue;
        }
        else {
          String path = new File(new File(containingFilePath).getParent(), argValue).getPath();
          return path.replace("\\", "/");
        }
      }
      else if (call.isCalleeText(PyNames.REPLACE) && args.length == 2) {
        final PyExpression callee = call.getCallee();
        if (!(callee instanceof PyQualifiedExpression)) return null;
        final PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
        String result = evaluate(qualifier, containingFilePath, visited);
        if (result == null) return null;
        String arg1 = evaluate(args[0], containingFilePath, visited);
        String arg2 = evaluate(args[1], containingFilePath, visited);
        if (arg1 == null || arg2 == null) return null;
        return result.replace(arg1, arg2);
      }
    }
    else if (expr instanceof PyReferenceExpression) {
      if (PyNames.PARDIR.equals(expr.getName())) {
        return "..";
      }
      else if (PyNames.CURDIR.equals(expr.getName())) {
        return ".";
      }
      else if (((PyReferenceExpression)expr).getQualifier() == null) {
        final String refName = ((PyReferenceExpression)expr).getReferencedName();
        if (PyNames.FILE.equals(refName)) {
          return containingFilePath;
        }
        PsiElement result = ((PyReferenceExpression)expr).getReference(PyResolveContext.noImplicits()).resolve();
        if (result instanceof PyTargetExpression) {
          result = ((PyTargetExpression)result).findAssignedValue();
        }
        if (result instanceof PyExpression) {
          return evaluate((PyExpression)result, containingFilePath, visited);
        }
      }
    }
    else if (expr instanceof PyStringLiteralExpression) {
      return ((PyStringLiteralExpression)expr).getStringValue();
    }
    return null;
  }

  public static String evaluatePathInJoin(String containingFilePath, PyExpression[] args, int endElement, Set<PyExpression> visited) {
    String result = null;
    for (int i = 0; i < endElement; i++) {
      String arg = evaluate(args[i], containingFilePath, visited);
      if (arg == null) {
        return null;
      }
      if (result == null) {
        result = arg;
      }
      else {
        result = new File(result, arg).getPath().replace("\\", "/");
      }
    }
    return result;
  }
}
