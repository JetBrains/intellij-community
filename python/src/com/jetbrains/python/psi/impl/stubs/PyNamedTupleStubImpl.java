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
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.PyNamedTupleStub;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PyNamedTupleStubImpl implements PyNamedTupleStub {

  @Nullable
  private final QualifiedName myCalleeName;

  @NotNull
  private final String myName;

  @NotNull
  private final List<String> myFields;

  private PyNamedTupleStubImpl(@Nullable QualifiedName calleeName, @NotNull String name, @NotNull List<String> fields) {
    myCalleeName = calleeName;
    myName = name;
    myFields = Collections.unmodifiableList(new ArrayList<>(fields));
  }

  @Nullable
  public static PyNamedTupleStub create(@NotNull PyTargetExpression expression) {
    final PyExpression assignedValue = expression.findAssignedValue();

    if (assignedValue instanceof PyCallExpression) {
      return create((PyCallExpression)assignedValue);
    }

    return null;
  }

  @Nullable
  public static PyNamedTupleStub create(@NotNull PyCallExpression expression) {
    final PyReferenceExpression calleeReference = PyUtil.as(expression.getCallee(), PyReferenceExpression.class);

    if (calleeReference == null) {
      return null;
    }

    final Pair<QualifiedName, NamedTupleModule> calleeNameAndModule = getCalleeNameAndNTModule(calleeReference);

    if (calleeNameAndModule != null) {
      final String name = resolveTupleName(expression);

      if (name == null) {
        return null;
      }

      final List<String> fields = resolveTupleFields(expression, calleeNameAndModule.getSecond());

      if (fields == null) {
        return null;
      }

      return new PyNamedTupleStubImpl(calleeNameAndModule.getFirst(), name, fields);
    }

    return null;
  }

  @Nullable
  public static PyNamedTupleStub deserialize(@NotNull StubInputStream stream) throws IOException {
    final StringRef calleeName = stream.readName();
    final StringRef name = stream.readName();
    final List<String> fields = deserializeFields(stream, stream.readVarInt());

    if (calleeName == null || name == null) {
      return null;
    }

    return new PyNamedTupleStubImpl(
      QualifiedName.fromDottedString(calleeName.getString()),
      name.getString(),
      fields
    );
  }

  @NotNull
  @Override
  public Class<? extends CustomTargetExpressionStubType> getTypeClass() {
    return PyNamedTupleStubType.class;
  }

  @Override
  public void serialize(@NotNull StubOutputStream stream) throws IOException {
    stream.writeName(myCalleeName == null ? null : myCalleeName.toString());
    stream.writeName(myName);
    stream.writeVarInt(myFields.size());

    for (String field : myFields) {
      stream.writeName(field);
    }
  }

  @Nullable
  @Override
  public QualifiedName getCalleeName() {
    return myCalleeName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public List<String> getFields() {
    return myFields;
  }

  @Nullable
  private static Pair<QualifiedName, NamedTupleModule> getCalleeNameAndNTModule(@NotNull PyReferenceExpression referenceExpression) {
    final Pair<QualifiedName, NamedTupleModule> name = getFullyQCalleeNameAndNTModule(referenceExpression);

    if (name != null) {
      return name;
    }

    return getImportedCalleeNameAndNTModule(referenceExpression);
  }

  @Nullable
  private static String resolveTupleName(@NotNull PyCallExpression callExpression) {
    // SUPPORTED CASES:

    // name = "Point"
    // Point = namedtuple(name, ...)

    // Point = namedtuple("Point", ...)

    // Point = namedtuple(("Point"), ...)

    // name = "Point"
    // Point = NamedTuple(name, ...)

    // Point = NamedTuple("Point", ...)

    // Point = NamedTuple(("Point"), ...)

    final PyExpression nameExpression = PyPsiUtils.flattenParens(callExpression.getArgument(0, PyExpression.class));

    if (nameExpression instanceof PyReferenceExpression) {
      return PyPsiUtils.strValue(fullResolveLocally((PyReferenceExpression)nameExpression));
    }

    return PyPsiUtils.strValue(nameExpression);
  }

  @Nullable
  private static List<String> resolveTupleFields(@NotNull PyCallExpression callExpression, @NotNull NamedTupleModule module) {
    switch (module) {
      case TYPING:
        return resolveTypingNTFields(callExpression);
      case COLLECTIONS:
        return resolveCollectionsNTFields(callExpression);
      default:
        return null;
    }
  }

  @NotNull
  private static List<String> deserializeFields(@NotNull StubInputStream stream, int fieldsSize) throws IOException {
    final List<String> fields = new ArrayList<>(fieldsSize);

    for (int i = 0; i < fieldsSize; i++) {
      final StringRef field = stream.readName();

      if (field != null) {
        fields.add(field.getString());
      }
    }

    return fields;
  }

  @Nullable
  private static Pair<QualifiedName, NamedTupleModule> getFullyQCalleeNameAndNTModule(@NotNull PyReferenceExpression referenceExpression) {
    // SUPPORTED CASES:

    // import collections
    // Point = collections.namedtuple(...)

    // import collections as c
    // Point = c.namedtuple(...)

    // import typing
    // ... = typing.NamedTuple(...)

    // import typing as t
    // ... = t.NamedTuple(...)

    final String referenceName = referenceExpression.getName();
    final NamedTupleModule module = PyNames.NAMEDTUPLE.equals(referenceName)
                                    ? NamedTupleModule.COLLECTIONS
                                    : PyTypingTypeProvider.NAMEDTUPLE_SIMPLE.equals(referenceName)
                                      ? NamedTupleModule.TYPING
                                      : null;

    if (module != null) {
      final PyExpression qualifier = referenceExpression.getQualifier();

      if (qualifier instanceof PyReferenceExpression) {
        final PyReferenceExpression qualifierReference = (PyReferenceExpression)qualifier;

        if (!qualifierReference.isQualified() && resolvesToModule(qualifierReference, module)) {
          return Pair.createNonNull(QualifiedName.fromComponents(qualifierReference.getName(), referenceName), module);
        }
      }
    }

    return null;
  }

  @Nullable
  private static Pair<QualifiedName, NamedTupleModule> getImportedCalleeNameAndNTModule(@NotNull PyReferenceExpression referenceExpression) {
    // SUPPORTED CASES:

    // from collections import namedtuple
    // Point = namedtuple(...)

    // from collections import namedtuple as NT
    // Point = NT(...)

    // from typing import NamedTuple
    // Point = NamedTuple(...)

    // from typing import NamedTuple as NT
    // Point = NT(...)

    for (PsiElement element : PyResolveUtil.resolveLocally(referenceExpression)) {
      if (element instanceof PyImportElement) {
        final PyImportElement importElement = (PyImportElement)element;
        final QualifiedName importedQName = importElement.getImportedQName();

        final NamedTupleModule module = equals(importedQName, PyNames.NAMEDTUPLE)
                                        ? NamedTupleModule.COLLECTIONS
                                        : equals(importedQName, PyTypingTypeProvider.NAMEDTUPLE_SIMPLE)
                                          ? NamedTupleModule.TYPING
                                          : null;

        if (module != null) {
          final PyStatement importStatement = importElement.getContainingImportStatement();

          if (importStatement instanceof PyFromImportStatement) {
            final PyFromImportStatement fromImportStatement = (PyFromImportStatement)importStatement;

            if (equals(fromImportStatement.getImportSourceQName(), module.getModuleName())) {
              return Pair.createNonNull(QualifiedName.fromComponents(referenceExpression.getName()), module);
            }
          }
        }
      }
    }

    return null;
  }

  private static boolean resolvesToModule(@NotNull PyReferenceExpression referenceExpression, @NotNull NamedTupleModule module) {
    for (PsiElement element : PyResolveUtil.resolveLocally(referenceExpression)) {
      if (element instanceof PyImportElement) {
        final PyImportElement importElement = (PyImportElement)element;

        if (equals(importElement.getImportedQName(), module.getModuleName())) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean equals(@Nullable QualifiedName qualifiedName, @NotNull String name) {
    return qualifiedName != null && name.equals(qualifiedName.toString());
  }

  @Nullable
  private static PyExpression fullResolveLocally(@NotNull PyReferenceExpression referenceExpression) {
    for (PsiElement element : PyResolveUtil.resolveLocally(referenceExpression)) {
      if (element instanceof PyTargetExpression) {
        final PyExpression assignedValue = ((PyTargetExpression)element).findAssignedValue();

        if (assignedValue instanceof PyReferenceExpression) {
          return fullResolveLocally((PyReferenceExpression)assignedValue);
        }

        return assignedValue;
      }
    }

    return null;
  }

  @Nullable
  private static List<String> resolveCollectionsNTFields(@NotNull PyCallExpression callExpression) {
    // SUPPORTED CASES:

    // fields = ["x", "y"]
    // Point = namedtuple(..., fields)

    // Point = namedtuple(..., "x y")

    // Point = namedtuple(..., ("x y"))

    // Point = namedtuple(..., "x, y")

    // Point = namedtuple(..., ["x", "y"])

    final PyExpression fields = PyPsiUtils.flattenParens(callExpression.getArgument(1, PyExpression.class));

    final PyExpression resolvedFields = fields instanceof PyReferenceExpression
                                        ? fullResolveLocally((PyReferenceExpression)fields)
                                        : fields;

    final List<String> listValue = PyUtil.strListValue(resolvedFields);
    if (listValue != null) return listValue;

    final String resolvedFieldsValue = PyPsiUtils.strValue(resolvedFields);
    if (resolvedFieldsValue == null) return null;

    return StreamEx
      .of(StringUtil.tokenize(resolvedFieldsValue, ", ").iterator())
      .toList();
  }

  @Nullable
  private static List<String> resolveTypingNTFields(@NotNull PyCallExpression callExpression) {
    // SUPPORTED CASES:

    // fields = [("x", str), ("y", int)]
    // Point = NamedTuple(..., fields)

    // Point = NamedTuple(..., [("x", str), ("y", int)])

    // Point = NamedTuple(..., x=str, y=int)

    final PyExpression secondArgument = PyPsiUtils.flattenParens(callExpression.getArgument(1, PyExpression.class));

    if (secondArgument instanceof PyKeywordArgument) {
      final PyExpression[] arguments = callExpression.getArguments();
      return StreamEx
        .of(arguments, 1, arguments.length)
        .select(PyKeywordArgument.class)
        .map(PyKeywordArgument::getKeyword)
        .toList();
    } else {
      final PyExpression resolvedFields = secondArgument instanceof PyReferenceExpression
                                          ? fullResolveLocally((PyReferenceExpression)secondArgument)
                                          : secondArgument;
      if (!(resolvedFields instanceof PySequenceExpression)) return null;

      final List<String> result = new ArrayList<>();

      for (PyExpression element : ((PySequenceExpression)resolvedFields).getElements()) {
        if (!(element instanceof PyParenthesizedExpression)) return null;

        final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
        if (!(contained instanceof PyTupleExpression)) return null;

        final PyExpression[] nameAndType = ((PyTupleExpression)contained).getElements();
        final PyExpression name = ArrayUtil.getFirstElement(nameAndType);
        if (nameAndType.length != 2 || !(name instanceof PyStringLiteralExpression)) return null;

        result.add(((PyStringLiteralExpression)name).getStringValue());
      }

      return result;
    }
  }

  private enum NamedTupleModule {

    COLLECTIONS {
      @Override
      public String getModuleName() {
        return PyNames.COLLECTIONS;
      }
    },

    TYPING {
      @Override
      public String getModuleName() {
        return PyTypingTypeProvider.TYPING;
      }
    };

    public abstract String getModuleName();
  }
}
