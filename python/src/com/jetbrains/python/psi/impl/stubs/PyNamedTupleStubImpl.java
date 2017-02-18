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
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.PyNamedTupleStub;
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

    final QualifiedName namedTupleQName = getNamedTupleQName(calleeReference);

    if (namedTupleQName != null) {
      final String name = resolveTupleName(expression);

      if (name == null) {
        return null;
      }

      final List<String> fields = resolveTupleFields(expression);

      if (fields == null) {
        return null;
      }

      return new PyNamedTupleStubImpl(namedTupleQName, name, fields);
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
  private static QualifiedName getNamedTupleQName(@NotNull PyReferenceExpression referenceExpression) {
    final QualifiedName name = getFullyQualifiedNamedTupleQName(referenceExpression);

    if (name != null) {
      return name;
    }

    return getImportedNamedTupleQName(referenceExpression);
  }

  @Nullable
  private static String resolveTupleName(@NotNull PyCallExpression callExpression) {
    // SUPPORTED CASES:

    // name = "Point"
    // Point = namedtuple(name, ...)

    // Point = namedtuple("Point", ...)

    // Point = namedtuple(("Point"), ...)

    final PyExpression nameExpression = PyPsiUtils.flattenParens(callExpression.getArgument(0, PyExpression.class));

    if (nameExpression instanceof PyReferenceExpression) {
      return PyPsiUtils.strValue(fullResolveLocally((PyReferenceExpression)nameExpression));
    }

    return PyPsiUtils.strValue(nameExpression);
  }

  @Nullable
  private static List<String> resolveTupleFields(@NotNull PyCallExpression callExpression) {
    // SUPPORTED CASES:

    // fields = ["x", "y"]
    // Point = namedtuple(..., fields)

    // Point = namedtuple(..., "x y")

    // Point = namedtuple(..., ("x y"))

    // Point = namedtuple(..., "x, y")

    // Point = namedtuple(..., ["x", "y"])

    final PyExpression fieldsExpression = PyPsiUtils.flattenParens(callExpression.getArgument(1, PyExpression.class));

    if (fieldsExpression instanceof PyReferenceExpression) {
      return extractFields(fullResolveLocally((PyReferenceExpression)fieldsExpression));
    }

    return extractFields(fieldsExpression);
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
  private static QualifiedName getFullyQualifiedNamedTupleQName(@NotNull PyReferenceExpression referenceExpression) {
    // SUPPORTED CASES:

    // import collections
    // Point = collections.namedtuple(...)

    // import collections as c
    // Point = c.namedtuple(...)

    if (PyNames.NAMEDTUPLE.equals(referenceExpression.getName())) {
      final PyExpression qualifier = referenceExpression.getQualifier();

      if (qualifier instanceof PyReferenceExpression) {
        final PyReferenceExpression qualifierReference = (PyReferenceExpression)qualifier;

        if (!qualifierReference.isQualified() && resolvesToCollections(qualifierReference)) {
          return QualifiedName.fromComponents(qualifierReference.getName(), referenceExpression.getName());
        }
      }
    }

    return null;
  }

  @Nullable
  private static QualifiedName getImportedNamedTupleQName(@NotNull PyReferenceExpression referenceExpression) {
    // SUPPORTED CASES:

    // from collections import namedtuple
    // Point = namedtuple(...)

    // from collections import namedtuple as NT
    // Point = NT(...)

    for (PsiElement element : PyResolveUtil.resolveLocally(referenceExpression)) {
      if (element instanceof PyImportElement) {
        final PyImportElement importElement = (PyImportElement)element;

        if (equals(importElement.getImportedQName(), PyNames.NAMEDTUPLE)) {
          final PyStatement importStatement = importElement.getContainingImportStatement();

          if (importStatement instanceof PyFromImportStatement) {
            final PyFromImportStatement fromImportStatement = (PyFromImportStatement)importStatement;

            if (equals(fromImportStatement.getImportSourceQName(), PyNames.COLLECTIONS)) {
              return QualifiedName.fromComponents(referenceExpression.getName());
            }
          }
        }
      }
    }

    return null;
  }

  private static boolean resolvesToCollections(@NotNull PyReferenceExpression referenceExpression) {
    for (PsiElement element : PyResolveUtil.resolveLocally(referenceExpression)) {
      if (element instanceof PyImportElement) {
        final PyImportElement importElement = (PyImportElement)element;

        if (equals(importElement.getImportedQName(), PyNames.COLLECTIONS)) {
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
  private static List<String> extractFields(@Nullable PyExpression expression) {
    if (expression == null) {
      return null;
    }

    final List<String> listValue = PyUtil.strListValue(expression);

    if (listValue != null) {
      return listValue;
    }

    return extractFields(PyPsiUtils.strValue(expression));
  }

  @Nullable
  private static List<String> extractFields(@Nullable String fieldsString) {
    if (fieldsString == null) {
      return null;
    }

    final List<String> result = new ArrayList<>();

    for (String name : StringUtil.tokenize(fieldsString, ", ")) {
      result.add(name);
    }

    return result;
  }
}
