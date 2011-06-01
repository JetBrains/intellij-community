package com.jetbrains.python.psi.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class PyPathEvaluator  {

  private PyPathEvaluator() {
  }

  @Nullable
  public static String evaluate(PyExpression expr) {
    VirtualFile vFile = expr.getContainingFile().getVirtualFile();
    return evaluate(expr, vFile == null ? null : vFile.getPath());
  }

  @Nullable
  public static String evaluate(PyExpression expr, String path) {
    if (expr == null) {
      return null;
    }
    if (expr instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)expr;
      final PyExpression[] args = call.getArguments();
      if (call.isCalleeText(PyNames.DIRNAME) && args.length == 1) {
        String argValue = evaluate(args[0], path);
        return argValue == null ? null : new File(argValue).getParent();
      }
      else if (call.isCalleeText(PyNames.JOIN) && args.length == 2) {
        String arg1 = evaluate(args[0], path);
        String arg2 = evaluate(args[1], path);
        return arg1 == null || arg2 == null ? null : new File(arg1, arg2).getPath();
      }
      else if (call.isCalleeText(PyNames.ABSPATH) && args.length == 1) {
        String argValue = evaluate(args[0], path);
        // relative to directory of 'path', not file
        return argValue == null ? null : new File(new File(path).getParent(), argValue).getPath();
      }
      else if (call.isCalleeText(PyNames.REPLACE) && args.length == 2) {
        final PyExpression callee = call.getCallee();
        if (!(callee instanceof PyQualifiedExpression)) return null;
        final PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
        String result = evaluate(qualifier, path);
        if (result == null) return null;
        String arg1 = evaluate(args[0], path);
        String arg2 = evaluate(args[1], path);
        if (arg1 == null || arg2 == null) return null;
        return result.replace(arg1, arg2);
      }
    }
    else if (expr instanceof PyReferenceExpression) {
      if (((PyReferenceExpression)expr).getQualifier() == null) {
        final String refName = ((PyReferenceExpression)expr).getReferencedName();
        if (PyNames.FILE.equals(refName)) {
          return path;
        }
        PsiElement result = ((PyReferenceExpression)expr).getReference(PyResolveContext.noImplicits()).resolve();
        if (result instanceof PyTargetExpression) {
          result = ((PyTargetExpression)result).findAssignedValue();
        }
        if (result instanceof PyExpression) {
          return evaluate((PyExpression)result, path);
        }
      }
    }
    else if (expr instanceof PyStringLiteralExpression) {
      return ((PyStringLiteralExpression)expr).getStringValue();
    }
    return null;
  }
}
