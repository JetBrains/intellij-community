/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.impl.stubs.PyNamedTupleStubImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyNamedTupleStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyStdlibTypeProvider extends PyTypeProviderBase {
  private static final Set<String> OPEN_FUNCTIONS = ImmutableSet.of("__builtin__.open", "io.open", "os.fdopen",
                                                                    "pathlib.Path.open");

  private static final String PY2K_FILE_TYPE = "file";
  private static final String PY3K_BINARY_FILE_TYPE = "io.FileIO[bytes]";
  private static final String PY3K_TEXT_FILE_TYPE = "io.TextIOWrapper[unicode]";

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
    PyType type = getBaseStringType(referenceTarget);
    if (type != null) {
      return type;
    }
    type = getNamedTupleType(referenceTarget, context, anchor);
    if (type != null) {
      return type;
    }
    type = getEnumType(referenceTarget, context, anchor);
    if (type != null) {
      return type;
    }
    return null;
  }

  @Nullable
  private static PyType getBaseStringType(@NotNull PsiElement referenceTarget) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(referenceTarget);
    if (referenceTarget instanceof PyElement && builtinCache.isBuiltin(referenceTarget) &&
        "basestring".equals(((PyElement)referenceTarget).getName())) {
      return builtinCache.getStringType(LanguageLevel.forElement(referenceTarget));
    }
    return null;
  }

  @Nullable
  private static PyType getEnumType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context,
                                    @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression) {
      final PyTargetExpression target = (PyTargetExpression)referenceTarget;
      final ScopeOwner owner = ScopeUtil.getScopeOwner(target);
      if (owner instanceof PyClass) {
        final PyClass cls = (PyClass)owner;
        final List<PyClassLikeType> types = cls.getAncestorTypes(context);
        for (PyClassLikeType type : types) {
          if (type != null && "enum.Enum".equals(type.getClassQName())) {
            final PyType classType = context.getType(cls);
            if (classType instanceof PyClassType) {
              return ((PyClassType)classType).toInstance();
            }
          }
        }
      }
    }
    if (referenceTarget instanceof PyQualifiedNameOwner) {
      final PyQualifiedNameOwner qualifiedNameOwner = (PyQualifiedNameOwner)referenceTarget;
      final String name = qualifiedNameOwner.getQualifiedName();
      if ("enum.Enum.name".equals(name)) {
        return PyBuiltinCache.getInstance(referenceTarget).getStrType();
      }
      else if ("enum.Enum.value".equals(name) && anchor instanceof PyReferenceExpression && context.maySwitchToAST(anchor)) {
        final PyReferenceExpression anchorExpr = (PyReferenceExpression)anchor;
        final PyExpression qualifier = anchorExpr.getQualifier();
        if (qualifier instanceof PyReferenceExpression) {
          final PyReferenceExpression qualifierExpr = (PyReferenceExpression)qualifier;
          final PsiElement resolvedQualifier = qualifierExpr.getReference().resolve();
          if (resolvedQualifier instanceof PyTargetExpression) {
            final PyTargetExpression qualifierTarget = (PyTargetExpression)resolvedQualifier;
            // Requires switching to AST, we cannot use getType(qualifierTarget) here, because its type is overridden by this type provider
            if (context.maySwitchToAST(qualifierTarget)) {
              final PyExpression value = qualifierTarget.findAssignedValue();
              if (value != null) {
                return context.getType(value);
              }
            }
          }
        }
      }
      else if ("enum.EnumMeta.__members__".equals(name)) {
        return PyTypeParser.getTypeByName(referenceTarget, "dict[str, unknown]");
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    if (callSite != null && isListGetItem(function)) {
      final PyExpression receiver = PyTypeChecker.getReceiver(callSite, function);
      final Map<PyExpression, PyNamedParameter> mapping = PyCallExpressionHelper.mapArguments(callSite, function, context);
      final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(receiver, mapping, context);
      if (substitutions != null) {
        return analyzeListGetItemCallType(receiver, mapping, substitutions, context);
      }
    }

    final String qname = getQualifiedName(function, callSite);
    if (qname != null) {
      if (OPEN_FUNCTIONS.contains(qname) && callSite instanceof PyCallExpression) {
        final PyCallExpression callExpr = (PyCallExpression)callSite;
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
        final PyCallExpression.PyArgumentsMapping mapping = callExpr.mapArguments(resolveContext);
        if (mapping.getMarkedCallee() != null) {
          return getOpenFunctionType(qname, mapping.getMappedParameters(), callSite);
        }
      }
      else if ("__builtin__.tuple.__add__".equals(qname) && callSite instanceof PyBinaryExpression) {
        return getTupleConcatenationResultType((PyBinaryExpression)callSite, context);
      }
      else if ("__builtin__.tuple.__mul__".equals(qname) && callSite instanceof PyBinaryExpression) {
        return getTupleMultiplicationResultType((PyBinaryExpression)callSite, context);
      }
    }

    return null;
  }

  private static boolean isListGetItem(@NotNull PyFunction function) {
    return PyNames.GETITEM.equals(function.getName()) &&
           Optional
             .ofNullable(PyBuiltinCache.getInstance(function).getListType())
             .map(PyClassType::getPyClass)
             .map(cls -> cls.equals(function.getContainingClass()))
             .orElse(false);
  }

  @Nullable
  private static Ref<PyType> analyzeListGetItemCallType(@Nullable PyExpression receiver,
                                                        @NotNull Map<PyExpression, PyNamedParameter> parameters,
                                                        @NotNull Map<PyGenericType, PyType> substitutions,
                                                        @NotNull TypeEvalContext context) {
    if (parameters.size() != 1 || substitutions.size() > 1) {
      return null;
    }

    final PyType firstArgumentType = Optional
      .ofNullable(parameters.keySet().iterator().next())
      .map(context::getType)
      .orElse(null);

    if (firstArgumentType == null) {
      return null;
    }

    if (PyABCUtil.isSubtype(firstArgumentType, PyNames.ABC_INTEGRAL, context)) {
      final PyType result = substitutions.isEmpty() ? null : substitutions.values().iterator().next();
      return Ref.create(result);
    }

    if (PyNames.SLICE.equals(firstArgumentType.getName()) && firstArgumentType.isBuiltin()) {
      return Ref.create(
        Optional
          .ofNullable(receiver)
          .map(context::getType)
          .orElseGet(() -> PyTypeChecker.substitute(PyBuiltinCache.getInstance(receiver).getListType(), substitutions, context))
      );
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getTupleMultiplicationResultType(@NotNull PyBinaryExpression multiplication, @NotNull TypeEvalContext context) {
    final PyTupleType leftTupleType = as(context.getType(multiplication.getLeftExpression()), PyTupleType.class);
    if (leftTupleType == null) {
      return null;
    }

    PyExpression rightExpression = multiplication.getRightExpression();
    if (rightExpression instanceof PyReferenceExpression) {
      final PsiElement target = ((PyReferenceExpression)rightExpression).getReference().resolve();
      if (target instanceof PyTargetExpression) {
        rightExpression = ((PyTargetExpression)target).findAssignedValue();
      }
    }

    if (rightExpression instanceof PyNumericLiteralExpression && ((PyNumericLiteralExpression)rightExpression).isIntegerLiteral()) {
      if (leftTupleType.isHomogeneous()) {
        return Ref.create(leftTupleType);
      }

      final int multiplier = ((PyNumericLiteralExpression)rightExpression).getBigIntegerValue().intValue();
      final int originalSize = leftTupleType.getElementCount();
      // Heuristic
      if (originalSize * multiplier <= 20) {
        final PyType[] elementTypes = new PyType[leftTupleType.getElementCount() * multiplier];
        for (int i = 0; i < multiplier; i++) {
          for (int j = 0; j < originalSize; j++) {
            elementTypes[i * originalSize + j] = leftTupleType.getElementType(j);
          }
        }
        return Ref.create(PyTupleType.create(multiplication, elementTypes));
      }
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getTupleConcatenationResultType(@NotNull PyBinaryExpression addition, @NotNull TypeEvalContext context) {
    if (addition.getRightExpression() != null) {
      final PyTupleType leftTupleType = as(context.getType(addition.getLeftExpression()), PyTupleType.class);
      final PyTupleType rightTupleType = as(context.getType(addition.getRightExpression()), PyTupleType.class);

      if (leftTupleType != null && rightTupleType != null) {
        if (leftTupleType.isHomogeneous() || rightTupleType.isHomogeneous()) {
          // We may try to find the common type of elements of two homogeneous tuple as an alternative
          return null;
        }
        
        final PyType[] elementTypes = new PyType[leftTupleType.getElementCount() + rightTupleType.getElementCount()];
        for (int i = 0; i < leftTupleType.getElementCount(); i++) {
          elementTypes[i] = leftTupleType.getElementType(i);
        }
        for (int i = 0; i < rightTupleType.getElementCount(); i++) {
          elementTypes[i + leftTupleType.getElementCount()] = rightTupleType.getElementType(i);
        }

        return Ref.create(PyTupleType.create(addition, elementTypes));
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
  private static PyType getNamedTupleType(@NotNull PsiElement referenceTarget,
                                          @NotNull TypeEvalContext context,
                                          @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression) {
      final PyTargetExpression target = (PyTargetExpression)referenceTarget;
      final PyTargetExpressionStub stub = target.getStub();

      if (stub != null) {
        return getNamedTupleTypeFromStub(target, stub.getCustomStub(PyNamedTupleStub.class), 1);
      } else {
        return getNamedTupleTypeFromAST(target, context, 1);
      }
    }
    else if (referenceTarget instanceof PyFunction && anchor instanceof PyCallExpression) {
      return getNamedTupleTypeFromAST((PyCallExpression)anchor, context, 2);
    }
    return null;
  }

  @NotNull
  private static Ref<PyType> getOpenFunctionType(@NotNull String callQName,
                                                 @NotNull Map<PyExpression, PyNamedParameter> arguments,
                                                 @NotNull PsiElement anchor) {
    String mode = "r";
    for (Map.Entry<PyExpression, PyNamedParameter> entry : arguments.entrySet()) {
      final PyNamedParameter parameter = entry.getValue();
      if ("mode".equals(parameter.getName())) {
        PyExpression argument = entry.getKey();
        if (argument instanceof PyKeywordArgument) {
          argument = ((PyKeywordArgument)argument).getValueExpression();
        }
        if (argument instanceof PyStringLiteralExpression) {
          mode = ((PyStringLiteralExpression)argument).getStringValue();
          break;
        }
      }
    }

    if (LanguageLevel.forElement(anchor).isAtLeast(LanguageLevel.PYTHON30) || "io.open".equals(callQName)) {
      if (mode.contains("b")) {
        return Ref.create(PyTypeParser.getTypeByName(anchor, PY3K_BINARY_FILE_TYPE));
      }
      else {
        return Ref.create(PyTypeParser.getTypeByName(anchor, PY3K_TEXT_FILE_TYPE));
      }
    }

    return Ref.create(PyTypeParser.getTypeByName(anchor, PY2K_FILE_TYPE));
  }

  @Nullable
  private static String getQualifiedName(@NotNull PyFunction f, @Nullable PsiElement callSite) {
    PyPsiUtils.assertValid(f);
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

  @Nullable
  private static PyType getNamedTupleTypeFromStub(@NotNull PsiElement referenceTarget,
                                                  @Nullable PyNamedTupleStub stub,
                                                  int definitionLevel) {
    if (stub == null) {
      return null;
    }

    final PyClass tupleClass = PyBuiltinCache.getInstance(referenceTarget).getClass(PyNames.FAKE_NAMEDTUPLE);
    if (tupleClass == null) {
      return null;
    }

    return new PyNamedTupleType(tupleClass, referenceTarget, stub.getName(), stub.getFields(), definitionLevel);
  }

  @Nullable
  private static PyType getNamedTupleTypeFromAST(@NotNull PyTargetExpression expression,
                                                 @NotNull TypeEvalContext context,
                                                 int definitionLevel) {
    if (context.maySwitchToAST(expression)) {
      return getNamedTupleTypeFromStub(expression, PyNamedTupleStubImpl.create(expression), definitionLevel);
    }

    return null;
  }

  @Nullable
  private static PyType getNamedTupleTypeFromAST(@NotNull PyCallExpression expression,
                                                 @NotNull TypeEvalContext context,
                                                 int definitionLevel) {
    if (context.maySwitchToAST(expression)) {
      return getNamedTupleTypeFromStub(expression, PyNamedTupleStubImpl.create(expression), definitionLevel);
    }

    return null;
  }
}
