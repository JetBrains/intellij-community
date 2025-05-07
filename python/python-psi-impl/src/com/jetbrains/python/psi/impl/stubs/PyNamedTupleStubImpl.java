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
import com.intellij.util.containers.ContainerUtil;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class PyNamedTupleStubImpl implements PyNamedTupleStub {

  private final @Nullable QualifiedName myCalleeName;

  private final @NotNull String myName;

  private final @NotNull LinkedHashMap<String, Optional<String>> myFields;

  private PyNamedTupleStubImpl(@Nullable QualifiedName calleeName,
                               @NotNull String name,
                               @NotNull LinkedHashMap<String, Optional<String>> fields) {
    myCalleeName = calleeName;
    myName = name;
    myFields = fields;
  }

  public static @Nullable PyNamedTupleStub create(@NotNull PyTargetExpression expression) {
    final PyExpression assignedValue = expression.findAssignedValue();

    if (assignedValue instanceof PyCallExpression) {
      return create((PyCallExpression)assignedValue);
    }

    return null;
  }

  public static @Nullable PyNamedTupleStub create(@NotNull PyCallExpression expression) {
    final PyReferenceExpression calleeReference = PyUtil.as(expression.getCallee(), PyReferenceExpression.class);

    if (calleeReference == null) {
      return null;
    }

    final Pair<QualifiedName, NamedTupleModule> calleeNameAndModule = getCalleeNameAndNTModule(calleeReference);

    if (calleeNameAndModule != null) {
      final String name = PyResolveUtil.resolveStrArgument(expression, 0, "typename");

      if (name == null) {
        return null;
      }

      final LinkedHashMap<String, Optional<String>> fields = resolveTupleFields(expression, calleeNameAndModule.getSecond());

      if (fields == null) {
        return null;
      }

      return new PyNamedTupleStubImpl(calleeNameAndModule.getFirst(), name, fields);
    }

    return null;
  }

  public static @Nullable PyNamedTupleStub deserialize(@NotNull StubInputStream stream) throws IOException {
    final String calleeName = stream.readNameString();
    final String name = stream.readNameString();
    final LinkedHashMap<String, Optional<String>> fields = deserializeFields(stream, stream.readVarInt());

    if (calleeName == null || name == null) {
      return null;
    }

    return new PyNamedTupleStubImpl(QualifiedName.fromDottedString(calleeName), name, fields);
  }

  @Override
  public @NotNull Class<PyNamedTupleStubType> getTypeClass() {
    return PyNamedTupleStubType.class;
  }

  @Override
  public void serialize(@NotNull StubOutputStream stream) throws IOException {
    stream.writeName(myCalleeName == null ? null : myCalleeName.toString());
    stream.writeName(myName);
    stream.writeVarInt(myFields.size());

    for (Map.Entry<String, Optional<String>> entry : myFields.entrySet()) {
      stream.writeName(entry.getKey());
      stream.writeName(entry.getValue().orElse(null));
    }
  }

  @Override
  public @Nullable QualifiedName getCalleeName() {
    return myCalleeName;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull Map<String, Optional<String>> getFields() {
    return myFields;
  }

  private static @Nullable Pair<QualifiedName, NamedTupleModule> getCalleeNameAndNTModule(@NotNull PyReferenceExpression referenceExpression) {
    final QualifiedName calleeName = PyPsiUtils.asQualifiedName(referenceExpression);
    if (calleeName == null) return null;

    for (String name : ContainerUtil.map(PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression), QualifiedName::toString)) {
      if (name.equals(PyNames.COLLECTIONS_NAMEDTUPLE_PY2)) {
        return Pair.createNonNull(calleeName, NamedTupleModule.COLLECTIONS);
      }
      else if (name.equals(PyTypingTypeProvider.NAMEDTUPLE)) {
        return Pair.createNonNull(calleeName, NamedTupleModule.TYPING);
      }
    }

    return null;
  }

  private static @Nullable LinkedHashMap<String, Optional<String>> resolveTupleFields(@NotNull PyCallExpression callExpression,
                                                                                      @NotNull NamedTupleModule module) {
    return switch (module) {
      case TYPING -> resolveTypingNTFields(callExpression);
      case COLLECTIONS -> resolveCollectionsNTFields(callExpression);
    };
  }

  private static @NotNull LinkedHashMap<String, Optional<String>> deserializeFields(@NotNull StubInputStream stream, int fieldsSize)
    throws IOException {
    final LinkedHashMap<String, Optional<String>> fields = new LinkedHashMap<>(fieldsSize);

    for (int i = 0; i < fieldsSize; i++) {
      final String name = stream.readNameString();
      final String type = stream.readNameString();

      if (name != null) {
        fields.put(name, Optional.ofNullable(type));
      }
    }

    return fields;
  }

  private static @Nullable LinkedHashMap<String, Optional<String>> resolveCollectionsNTFields(@NotNull PyCallExpression callExpression) {
    // SUPPORTED CASES:

    // fields = ["x", "y"]
    // Point = namedtuple(..., fields)

    // Point = namedtuple(..., field_names=["x", "y"])

    // Point = namedtuple(..., "x y")

    // Point = namedtuple(..., ("x y"))

    // Point = namedtuple(..., "x, y")

    // Point = namedtuple(..., ["x", "y"])

    final PyExpression fields = getFieldsArgumentValue(callExpression, "field_names");

    final PyExpression resolvedFields = fields instanceof PyReferenceExpression
                                        ? PyResolveUtil.fullResolveLocally((PyReferenceExpression)fields)
                                        : fields;

    final Collector<String, ?, LinkedHashMap<String, Optional<String>>> toFieldsOfUnknownType =
      Collectors.toMap(Function.identity(), key -> Optional.empty(), (v1, v2) -> v2, LinkedHashMap::new);

    final List<String> listValue = PyUtil.strListValue(resolvedFields);
    if (listValue != null) return listValue.contains(null) ? null : StreamEx.of(listValue).collect(toFieldsOfUnknownType);

    final String resolvedFieldsValue = PyPsiUtils.strValue(resolvedFields);
    if (resolvedFieldsValue == null) return null;

    return StreamEx
      .of(StringUtil.tokenize(resolvedFieldsValue, ", ").iterator())
      .collect(toFieldsOfUnknownType);
  }

  private static @Nullable LinkedHashMap<String, Optional<String>> resolveTypingNTFields(@NotNull PyCallExpression callExpression) {
    // SUPPORTED CASES:

    // fields = [("x", str), ("y", int)]
    // Point = NamedTuple(..., fields)

    // Point = NamedTuple(..., fields=[("x", str), ("y", int)])

    // Point = NamedTuple(..., [("x", str), ("y", int)])

    // Point = NamedTuple(..., x=str, y=int)

    final PyExpression fields = getFieldsArgumentValue(callExpression, "fields");

    if (fields instanceof PyKeywordArgument) {
      final PyExpression[] arguments = callExpression.getArguments();
      return getTypingNTFieldsFromKwArguments(Arrays.asList(arguments).subList(1, arguments.length));
    }
    else {
      final PyExpression resolvedFields = fields instanceof PyReferenceExpression
                                          ? PyResolveUtil.fullResolveLocally((PyReferenceExpression)fields)
                                          : fields;
      if (!(resolvedFields instanceof PySequenceExpression)) return null;

      return getTypingNTFieldsFromIterable((PySequenceExpression)resolvedFields);
    }
  }

  private static @Nullable PyExpression getFieldsArgumentValue(@NotNull PyCallExpression callExpression, @NotNull String possibleKeyword) {
    return PyPsiUtils.flattenParens(callExpression.getArgument(1, possibleKeyword, PyExpression.class));
  }

  private static @Nullable LinkedHashMap<String, Optional<String>> getTypingNTFieldsFromKwArguments(@NotNull List<PyExpression> arguments) {
    final LinkedHashMap<String, Optional<String>> result = new LinkedHashMap<>();

    for (PyExpression argument : arguments) {
      if (!(argument instanceof PyKeywordArgument keywordArgument)) return null;

      final String keyword = keywordArgument.getKeyword();
      if (keyword == null) return null;

      result.put(keyword, Optional.ofNullable(textIfPresent(keywordArgument.getValueExpression())));
    }

    return result;
  }

  private static @Nullable LinkedHashMap<String, Optional<String>> getTypingNTFieldsFromIterable(@NotNull PySequenceExpression fields) {
    final LinkedHashMap<String, Optional<String>> result = new LinkedHashMap<>();

    for (PyExpression element : fields.getElements()) {
      if (!(element instanceof PyParenthesizedExpression)) return null;

      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      if (!(contained instanceof PyTupleExpression)) return null;

      final PyExpression[] nameAndType = ((PyTupleExpression)contained).getElements();
      final PyExpression name = ArrayUtil.getFirstElement(nameAndType);
      if (nameAndType.length != 2) return null;

      String nameValue = null;
      if (name instanceof PyStringLiteralExpression) {
        nameValue = ((PyStringLiteralExpression)name).getStringValue();
      }
      else if (name instanceof PyReferenceExpression) {
        nameValue = PyPsiUtils.strValue(PyResolveUtil.fullResolveLocally((PyReferenceExpression)name));
      }
      if (nameValue == null) return null;

      result.put(nameValue, Optional.ofNullable(textIfPresent(nameAndType[1])));
    }

    return result;
  }

  private static @Nullable String textIfPresent(@Nullable PsiElement element) {
    return element == null ? null : element.getText();
  }

  @Override
  public String toString() {
    return "PyNamedTupleStub(calleeName=" + myCalleeName + ", name=" + myName + ", fields=" + myFields + ')';
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
