/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.stdlib;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @author yole
 */
public class PyStdlibTypeProvider extends PyTypeProviderBase {
  private static final Set<String> OPEN_FUNCTIONS = ImmutableSet.of("__builtin__.open", "io.open", "os.fdopen");
  private static final String BINARY_FILE_TYPE = "io.FileIO[bytes]";
  private static final String TEXT_FILE_TYPE = "io.TextIOWrapper[unicode]";

  @Nullable
  public static PyStdlibTypeProvider getInstance() {
    for (PyTypeProvider typeProvider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      if (typeProvider instanceof PyStdlibTypeProvider) {
        return (PyStdlibTypeProvider)typeProvider;
      }
    }
    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression) {
      final PyTargetExpression target = (PyTargetExpression)referenceTarget;
      final QualifiedName calleeName = target.getCalleeName();
      if (calleeName != null && PyNames.NAMEDTUPLE.equals(calleeName.toString())) {
        // TODO: Create stubs for namedtuple for preventing switch from stub to AST
        final PyExpression value = target.findAssignedValue();
        if (value instanceof PyCallExpression) {
          final PyCallExpression call = (PyCallExpression)value;
          final PyCallExpression.PyMarkedCallee callee = call.resolveCallee(PyResolveContext.noImplicits());
          if (callee != null) {
            final Callable callable = callee.getCallable();
            if (PyNames.COLLECTIONS_NAMEDTUPLE.equals(callable.getQualifiedName())) {
              return PyNamedTupleType.fromCall(call, 1);
            }
          }
        }
      }
    }
    else if (referenceTarget instanceof PyFunction && anchor instanceof PyCallExpression) {
      final PyFunction function = (PyFunction)referenceTarget;
      if (PyNames.NAMEDTUPLE.equals(function.getName()) && PyNames.COLLECTIONS_NAMEDTUPLE.equals(function.getQualifiedName())) {
        return PyNamedTupleType.fromCall((PyCallExpression)anchor, 2);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull PyFunction function, @Nullable PyQualifiedExpression callSite, @NotNull TypeEvalContext context) {
    final String qname = getQualifiedName(function, callSite);
    if (qname != null) {
      if (OPEN_FUNCTIONS.contains(qname) && callSite != null) {
        final PyTypeChecker.AnalyzeCallResults results = PyTypeChecker.analyzeCallSite(callSite, context);
        if (results != null) {
          final PyType type = getOpenFunctionType(qname, results.getArguments(), callSite);
          if (type != null) {
            return type;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getIterationType(@NotNull PyClass iterable) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(iterable);
    if (builtinCache.hasInBuiltins(iterable)) {
      if ("file".equals(iterable.getName())) {
        return builtinCache.getStrType();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getContextManagerVariableType(@NotNull PyClass contextManager, @NotNull PyExpression withExpression, @NotNull TypeEvalContext context) {
    if ("contextlib.closing".equals(contextManager.getQualifiedName()) && withExpression instanceof PyCallExpression) {
      PyExpression closee = ((PyCallExpression)withExpression).getArgument(0, PyExpression.class);
      if (closee != null) {
        return context.getType(closee);
      }
    }
    final String name = contextManager.getName();
    if ("FileIO".equals(name) || "TextIOWrapper".equals(name) || "IOBase".equals(name) || "_IOBase".equals(name)) {
      return context.getType(withExpression);
    }
    return null;
  }

  @Nullable
  private static PyType getOpenFunctionType(@NotNull String callQName,
                                            @NotNull Map<PyExpression, PyNamedParameter> arguments,
                                            @NotNull PsiElement anchor) {
    String mode = "r";
    for (Map.Entry<PyExpression, PyNamedParameter> entry : arguments.entrySet()) {
      final PyNamedParameter parameter = entry.getValue();
      if ("mode".equals(parameter.getName())) {
        final PyExpression argument = entry.getKey();
        if (argument instanceof PyStringLiteralExpression) {
          mode = ((PyStringLiteralExpression)argument).getStringValue();
          break;
        }
      }
    }
    final LanguageLevel level = LanguageLevel.forElement(anchor);
    // Binary mode
    if (mode.contains("b")) {
      return PyTypeParser.getTypeByName(anchor, BINARY_FILE_TYPE);
    }
    // Text mode
    else {
      if (level.isPy3K() || "io.open".equals(callQName)) {
        return PyTypeParser.getTypeByName(anchor, TEXT_FILE_TYPE);
      }
      else {
        return PyTypeParser.getTypeByName(anchor, BINARY_FILE_TYPE);
      }
    }
  }

  @Nullable
  private static String getQualifiedName(@NotNull PyFunction f, @Nullable PsiElement callSite) {
    if (!f.isValid()) {
      return null;
    }
    String result = f.getName();
    final PyClass c = f.getContainingClass();
    final VirtualFile vfile = f.getContainingFile().getVirtualFile();
    if (vfile != null) {
      String module = QualifiedNameFinder.findShortestImportableName(callSite != null ? callSite : f, vfile);
      if ("builtins".equals(module)) {
        module = "__builtin__";
      }
      result = String.format("%s.%s%s",
                             module,
                             c != null ? c.getName() + "." : "",
                             result);
      final QualifiedName qname = PyStdlibCanonicalPathProvider.restoreStdlibCanonicalPath(QualifiedName.fromDottedString(result));
      if (qname != null) {
        return qname.toString();
      }
    }
    return result;
  }
}
