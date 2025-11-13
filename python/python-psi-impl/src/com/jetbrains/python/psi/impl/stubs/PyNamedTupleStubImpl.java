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

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyPsiUtils.flattenParens;

public final class PyNamedTupleStubImpl implements PyNamedTupleStub {

  private final @Nullable QualifiedName myCalleeName;

  private final @NotNull String myName;

  private final @NotNull LinkedHashMap<String, FieldTypeAndHasDefault> myFields;

  private PyNamedTupleStubImpl(@Nullable QualifiedName calleeName,
                               @NotNull String name,
                               @NotNull LinkedHashMap<String, FieldTypeAndHasDefault> fields) {
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
    final PyReferenceExpression calleeReference = as(expression.getCallee(), PyReferenceExpression.class);

    if (calleeReference == null) {
      return null;
    }

    final Pair<QualifiedName, NamedTupleModule> calleeNameAndModule = getCalleeNameAndNTModule(calleeReference);

    if (calleeNameAndModule != null) {
      final String name = PyResolveUtil.resolveStrArgument(expression, 0, "typename");

      if (name == null) {
        return null;
      }

      final LinkedHashMap<String, FieldTypeAndHasDefault> fields = resolveTupleFields(expression, calleeNameAndModule.getSecond());

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
    final LinkedHashMap<String, FieldTypeAndHasDefault> fields = deserializeFields(stream, stream.readVarInt());

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

    for (var entry : myFields.entrySet()) {
      stream.writeName(entry.getKey());
      stream.writeName(entry.getValue().type());
      stream.writeBoolean(entry.getValue().hasDefault());
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
  public @NotNull LinkedHashMap<String, FieldTypeAndHasDefault> getFields() {
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

  private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> resolveTupleFields(@NotNull PyCallExpression callExpression,
                                                                                            @NotNull NamedTupleModule module) {
    return switch (module) {
      case TYPING -> resolveTypingNTFields(callExpression);
      case COLLECTIONS -> resolveCollectionsNTFields(callExpression);
    };
  }

  private static @NotNull LinkedHashMap<String, FieldTypeAndHasDefault> deserializeFields(@NotNull StubInputStream stream, int fieldsSize)
    throws IOException {
    final LinkedHashMap<String, FieldTypeAndHasDefault> fields = new LinkedHashMap<>(fieldsSize);

    for (int i = 0; i < fieldsSize; i++) {
      final String name = stream.readNameString();
      final String type = stream.readNameString();
      final boolean hasDefault = stream.readBoolean();

      fields.put(name, new FieldTypeAndHasDefault(type, hasDefault));
    }

    return fields;
  }

  private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> resolveCollectionsNTFields(@NotNull PyCallExpression callExpression) {
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

    List<@NotNull String> fieldNames = PyUtil.strListValue(resolvedFields);
    if (fieldNames == null) {
      final String resolvedFieldsValue = PyPsiUtils.strValue(resolvedFields);
      if (resolvedFieldsValue == null) return null;
      fieldNames = StreamEx
        .of(StringUtil.tokenize(resolvedFieldsValue, ", ").iterator()).toList();
    }

    final PyExpression defaults = getDefaultsArgumentValue(callExpression);
    final PyExpression resolvedDefaults = defaults instanceof PyReferenceExpression
                                          ? PyResolveUtil.fullResolveLocally((PyReferenceExpression)defaults)
                                          : defaults;
    int defaultStart = fieldNames.size();
    if (resolvedDefaults instanceof PySequenceExpression seq) {
      defaultStart -= seq.getElements().length;
    }

    LinkedHashMap<String, FieldTypeAndHasDefault> result = new LinkedHashMap<>(fieldNames.size());
    for (int i = 0; i < fieldNames.size(); i++) {
      if (PyNames.PY3_KEYWORDS.contains(fieldNames.get(i))) {
        fieldNames.set(i, "_" + i);
      }
      result.put(fieldNames.get(i), new FieldTypeAndHasDefault(null, i >= defaultStart));
    }

    return result;
  }

  private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> resolveTypingNTFields(@NotNull PyCallExpression callExpression) {
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
    return flattenParens(callExpression.getArgument(1, possibleKeyword, PyExpression.class));
  }

  private static @Nullable PyExpression getDefaultsArgumentValue(@NotNull PyCallExpression callExpression) {
    return flattenParens(as(callExpression.getKeywordArgument("defaults"), PyExpression.class));
  }

  private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> getTypingNTFieldsFromKwArguments(@NotNull List<PyExpression> arguments) {
    final LinkedHashMap<String, FieldTypeAndHasDefault> result = new LinkedHashMap<>(arguments.size());

    for (PyExpression argument : arguments) {
      if (!(argument instanceof PyKeywordArgument keywordArgument)) return null;

      final String keyword = keywordArgument.getKeyword();
      if (keyword == null) return null;

      result.put(keyword, new FieldTypeAndHasDefault(textIfPresent(keywordArgument.getValueExpression()), false));
    }

    return result;
  }

  private static @Nullable LinkedHashMap<String, FieldTypeAndHasDefault> getTypingNTFieldsFromIterable(@NotNull PySequenceExpression fields) {
    final LinkedHashMap<String, FieldTypeAndHasDefault> result = new LinkedHashMap<>(fields.getElements().length);

    for (PyExpression element : fields.getElements()) {
      if (!(element instanceof PyParenthesizedExpression)) return null;

      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      if (!(contained instanceof PyTupleExpression)) return null;

      final PyExpression[] nameAndType = ((PyTupleExpression)contained).getElements();
      if (nameAndType.length != 2) return null;

      final String name = tryResolveToText(nameAndType[0]);
      if (name == null) return null;

      result.put(name, new FieldTypeAndHasDefault(textIfPresent(nameAndType[1]), false));
    }

    return result;
  }

  private static @Nullable String textIfPresent(@Nullable PsiElement element) {
    return element == null ? null : element.getText();
  }

  private static @Nullable String tryResolveToText(@Nullable PyExpression expression) {
    if (expression instanceof PyStringLiteralExpression) {
      return ((PyStringLiteralExpression)expression).getStringValue();
    }
    else if (expression instanceof PyReferenceExpression) {
      final PyExpression resolved = PyResolveUtil.fullResolveLocally((PyReferenceExpression)expression);
      if (resolved instanceof PyStringLiteralExpression) {
        return ((PyStringLiteralExpression)resolved).getStringValue();
      }
    }
    return null;
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
