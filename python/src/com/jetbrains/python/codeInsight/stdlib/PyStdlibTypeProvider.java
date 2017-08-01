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
package com.jetbrains.python.codeInsight.stdlib;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.impl.stubs.PyNamedTupleStubImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyNamedTupleStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyStdlibTypeProvider extends PyTypeProviderBase {
  private static final Set<String> OPEN_FUNCTIONS = ImmutableSet.of("open", "io.open", "os.fdopen",
                                                                    "pathlib.Path.open", "_io.open", "_io.fdopen");

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
    type = getNamedTupleTypeForResolvedCallee(referenceTarget, context, anchor);
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
  @Override
  public PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    if (!referenceExpression.isQualified()) {
      final String name = referenceExpression.getReferencedName();
      if (PyNames.NONE.equals(name)) {
        return PyNoneType.INSTANCE;
      }
      else if (PyNames.FALSE.equals(name) || PyNames.TRUE.equals(name)) {
        return PyBuiltinCache.getInstance(referenceExpression).getBoolType();
      }
    }

    final PyType fieldTypeForTypingNTTarget = getFieldTypeForTypingNTTarget(referenceExpression, context);
    if (fieldTypeForTypingNTTarget != null) {
      return fieldTypeForTypingNTTarget;
    }

    final PyCallableType namedTupleTypeForCallee = getNamedTupleTypeForCallee(referenceExpression, context);
    if (namedTupleTypeForCallee != null) {
      return namedTupleTypeForCallee;
    }

    return null;
  }

  @Nullable
  private static PyType getBaseStringType(@NotNull PsiElement referenceTarget) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(referenceTarget);
    if (referenceTarget instanceof PyElement && builtinCache.isBuiltin(referenceTarget) &&
        PyNames.BASESTRING.equals(((PyElement)referenceTarget).getName())) {
      return builtinCache.getStrOrUnicodeType();
    }
    return null;
  }

  @Nullable
  static PyNamedTupleType getNamedTupleTypeForResolvedCallee(@NotNull PsiElement referenceTarget,
                                                             @NotNull TypeEvalContext context,
                                                             @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyFunction && anchor instanceof PyCallExpression) {
      return getNamedTupleFunctionType((PyFunction)referenceTarget, context, (PyCallExpression)anchor);
    }

    if (referenceTarget instanceof PyTargetExpression) {
      return getNamedTupleTypeForTarget((PyTargetExpression)referenceTarget, context);
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
          if (type != null && PyNames.TYPE_ENUM.equals(type.getClassQName())) {
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
      if ((PyNames.TYPE_ENUM + ".name").equals(name)) {
        return PyBuiltinCache.getInstance(referenceTarget).getStrType();
      }
      else if ((PyNames.TYPE_ENUM + ".value").equals(name) && anchor instanceof PyReferenceExpression && context.maySwitchToAST(anchor)) {
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
        return PyTypeParser.getTypeByName(referenceTarget, "dict[str, unknown]", context);
      }
    }
    return null;
  }

  @Nullable
  private static PyType getFieldTypeForTypingNTTarget(@NotNull PyReferenceExpression referenceExpression,
                                                      @NotNull TypeEvalContext context) {
    final PyExpression qualifier = referenceExpression.getQualifier();
    if (qualifier != null) {
      final PyType qualifierType = context.getType(qualifier);
      if (qualifierType instanceof PyNamedTupleType) {
        final Map<String, PyNamedTupleType.FieldTypeAndDefaultValue> fields = ((PyNamedTupleType)qualifierType).getFields();
        final PyNamedTupleType.FieldTypeAndDefaultValue typeAndDefaultValue = fields.get(referenceExpression.getName());
        if (typeAndDefaultValue != null) {
          return typeAndDefaultValue.getType();
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyNamedTupleType getNamedTupleTypeForCallee(@NotNull PyReferenceExpression referenceExpression,
                                                             @NotNull TypeEvalContext context) {
    if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) {
      return null;
    }

    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
    final ResolveResult[] resolveResults = referenceExpression.getReference(resolveContext).multiResolve(false);

    for (PsiElement element : PyUtil.filterTopPriorityResults(resolveResults)) {
      if (element instanceof PyTargetExpression) {
        final PyNamedTupleType result = getNamedTupleTypeForTarget((PyTargetExpression)element, context);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PyClass) {
        final PyNamedTupleType result = getNamedTupleTypeForTypingNTInheritorAsCallee((PyClass)element, context);
        if (result != null) {
          return result;
        }
      }

      if (element instanceof PyTypedElement) {
        final PyType type = context.getType((PyTypedElement)element);
        if (type instanceof PyClassLikeType) {
          final List<PyClassLikeType> superClassTypes = ((PyClassLikeType)type).getSuperClassTypes(context);

          final PyClassLikeType superNTType = ContainerUtil.find(superClassTypes, PyNamedTupleType.class::isInstance);
          if (superNTType != null) {
            return as(superNTType, PyNamedTupleType.class);
          }
        }
      }
    }

    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    final String qname = function.getQualifiedName();
    if (qname != null) {
      if (OPEN_FUNCTIONS.contains(qname) && callSite instanceof PyCallExpression) {
        final PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(callSite, function, context);
        return getOpenFunctionType(qname, mapping.getMappedParameters(), callSite, context);
      }
      else if ("tuple.__init__".equals(qname) && callSite instanceof PyCallExpression) {
        return getTupleInitializationType((PyCallExpression)callSite, context);
      }
      else if ("tuple.__add__".equals(qname) && callSite instanceof PyBinaryExpression) {
        return getTupleConcatenationResultType((PyBinaryExpression)callSite, context);
      }
      else if ("tuple.__mul__".equals(qname) && callSite instanceof PyBinaryExpression) {
        return getTupleMultiplicationResultType((PyBinaryExpression)callSite, context);
      }
      else if ("object.__new__".equals(qname) && callSite instanceof PyCallExpression) {
        final PyExpression firstArgument = ((PyCallExpression)callSite).getArgument(0, PyExpression.class);
        final PyClassLikeType classLikeType = as(firstArgument != null ? context.getType(firstArgument) : null, PyClassLikeType.class);
        return classLikeType != null ? Ref.create(classLikeType.toInstance()) : null;
      }
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getTupleMultiplicationResultType(@NotNull PyBinaryExpression multiplication,
                                                              @NotNull TypeEvalContext context) {
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
        return Ref.create(PyTupleType.create(multiplication, Arrays.asList(elementTypes)));
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

        final List<PyType> newElementTypes = ContainerUtil.concat(leftTupleType.getElementTypes(context),
                                                                  rightTupleType.getElementTypes(context));
        return Ref.create(PyTupleType.create(addition, newElementTypes));
      }
    }

    return null;
  }

  @Nullable
  private static Ref<PyType> getTupleInitializationType(@NotNull PyCallExpression call, @NotNull TypeEvalContext context) {
    final PyExpression[] arguments = call.getArguments();

    if (arguments.length != 1) return null;

    final PyExpression argument = arguments[0];
    final PyType argumentType = context.getType(argument);

    if (argumentType instanceof PyTupleType) {
      return Ref.create(argumentType);
    }
    else if (argumentType instanceof PyCollectionType) {
      final PyType iteratedItemType = ((PyCollectionType)argumentType).getIteratedItemType();
      return Ref.create(PyTupleType.createHomogeneous(call, iteratedItemType));
    }

    return null;
  }

  @Nullable
  @Override
  public PyType getContextManagerVariableType(@NotNull PyClass contextManager,
                                              @NotNull PyExpression withExpression,
                                              @NotNull TypeEvalContext context) {
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
  private static PyNamedTupleType getNamedTupleFunctionType(@NotNull PyFunction function,
                                                            @NotNull TypeEvalContext context,
                                                            @NotNull PyCallExpression call) {
    if (PyNames.COLLECTIONS_NAMEDTUPLE.equals(function.getQualifiedName())) {
      return getNamedTupleTypeFromAST(call, context, PyNamedTupleType.DefinitionLevel.NT_FUNCTION);
    }

    if (PyUtil.isInit(function)) {
      final PyClass cls = function.getContainingClass();
      if (cls != null && PyTypingTypeProvider.NAMEDTUPLE.equals(cls.getQualifiedName())) {
        return getNamedTupleTypeFromAST(call, context, PyNamedTupleType.DefinitionLevel.NT_FUNCTION);
      }
    }

    return null;
  }

  @Nullable
  private static PyNamedTupleType getNamedTupleTypeForTarget(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    final PyTargetExpressionStub stub = target.getStub();

    if (stub != null) {
      return getNamedTupleTypeFromStub(target,
                                       stub.getCustomStub(PyNamedTupleStub.class),
                                       context,
                                       PyNamedTupleType.DefinitionLevel.NEW_TYPE);
    }
    else {
      return getNamedTupleTypeFromAST(target, context, PyNamedTupleType.DefinitionLevel.NEW_TYPE);
    }
  }

  @Nullable
  private static PyNamedTupleType getNamedTupleTypeForTypingNTInheritorAsCallee(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final Condition<PyClassLikeType> isTypingNT =
      type ->
        type != null &&
        !(type instanceof PyNamedTupleType) &&
        PyTypingTypeProvider.NAMEDTUPLE.equals(type.getClassQName());

    if (ContainerUtil.exists(cls.getSuperClassTypes(context), isTypingNT)) {
      final String name = cls.getName();
      if (name != null) {
        final PsiElement typingNT =
          PyResolveImportUtil.resolveTopLevelMember(QualifiedName.fromDottedString(PyTypingTypeProvider.NAMEDTUPLE),
                                                    PyResolveImportUtil.fromFoothold(cls));

        final PyClass tupleClass = as(typingNT, PyClass.class);
        if (tupleClass != null) {
          final Set<PyTargetExpression> fields = new TreeSet<>(Comparator.comparingInt(PyTargetExpression::getTextOffset));

          cls.processClassLevelDeclarations(
            new BaseScopeProcessor() {
              @Override
              public boolean execute(@NotNull PsiElement element, @NotNull ResolveState substitutor) {
                if (element instanceof PyTargetExpression) {
                  final PyTargetExpression target = (PyTargetExpression)element;
                  if (target.getAnnotation() != null) {
                    fields.add(target);
                  }
                }

                return true;
              }
            }
          );

          final Collector<PyTargetExpression, ?, LinkedHashMap<String, PyNamedTupleType.FieldTypeAndDefaultValue>> toNTFields =
            Collectors.toMap(PyTargetExpression::getName,
                             field -> new PyNamedTupleType.FieldTypeAndDefaultValue(context.getType(field), field.findAssignedValue()),
                             (v1, v2) -> v2,
                             LinkedHashMap::new);

          return new PyNamedTupleType(tupleClass,
                                      cls,
                                      name,
                                      fields.stream().collect(toNTFields),
                                      PyNamedTupleType.DefinitionLevel.NEW_TYPE);
        }
      }
    }

    return null;
  }

  @NotNull
  private static Ref<PyType> getOpenFunctionType(@NotNull String callQName,
                                                 @NotNull Map<PyExpression, PyCallableParameter> arguments,
                                                 @NotNull PsiElement anchor,
                                                 @NotNull TypeEvalContext context) {
    String mode = "r";
    for (Map.Entry<PyExpression, PyCallableParameter> entry : arguments.entrySet()) {
      if ("mode".equals(entry.getValue().getName())) {
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

    if (LanguageLevel.forElement(anchor).isAtLeast(LanguageLevel.PYTHON30) || "io.open".equals(callQName) || "_io.open".equals(callQName)) {
      if (mode.contains("b")) {
        return Ref.create(PyTypeParser.getTypeByName(anchor, PY3K_BINARY_FILE_TYPE, context));
      }
      else {
        return Ref.create(PyTypeParser.getTypeByName(anchor, PY3K_TEXT_FILE_TYPE, context));
      }
    }

    return Ref.create(PyTypeParser.getTypeByName(anchor, PY2K_FILE_TYPE, context));
  }

  @Nullable
  private static PyNamedTupleType getNamedTupleTypeFromStub(@NotNull PsiElement referenceTarget,
                                                            @Nullable PyNamedTupleStub stub,
                                                            @NotNull TypeEvalContext context,
                                                            @NotNull PyNamedTupleType.DefinitionLevel definitionLevel) {
    if (stub == null) {
      return null;
    }

    final PsiElement typingNT = PyResolveImportUtil.resolveTopLevelMember(QualifiedName.fromDottedString(PyTypingTypeProvider.NAMEDTUPLE),
                                                                          PyResolveImportUtil.fromFoothold(referenceTarget));

    final PyClass tupleClass = as(typingNT, PyClass.class);
    if (tupleClass == null) {
      return null;
    }

    return new PyNamedTupleType(tupleClass,
                                referenceTarget,
                                stub.getName(),
                                parseNamedTupleFields(referenceTarget, stub.getFields(), context),
                                definitionLevel);
  }

  @Nullable
  private static PyNamedTupleType getNamedTupleTypeFromAST(@NotNull PyTargetExpression expression,
                                                           @NotNull TypeEvalContext context,
                                                           @NotNull PyNamedTupleType.DefinitionLevel definitionLevel) {
    if (context.maySwitchToAST(expression)) {
      return getNamedTupleTypeFromStub(expression, PyNamedTupleStubImpl.create(expression), context, definitionLevel);
    }

    return null;
  }

  @Nullable
  private static PyNamedTupleType getNamedTupleTypeFromAST(@NotNull PyCallExpression expression,
                                                           @NotNull TypeEvalContext context,
                                                           @NotNull PyNamedTupleType.DefinitionLevel definitionLevel) {
    if (context.maySwitchToAST(expression)) {
      return getNamedTupleTypeFromStub(expression, PyNamedTupleStubImpl.create(expression), context, definitionLevel);
    }

    return null;
  }

  @NotNull
  private static LinkedHashMap<String, PyNamedTupleType.FieldTypeAndDefaultValue> parseNamedTupleFields(@NotNull PsiElement anchor,
                                                                                                        @NotNull Map<String, Optional<String>> fields,
                                                                                                        @NotNull TypeEvalContext context) {
    final LinkedHashMap<String, PyNamedTupleType.FieldTypeAndDefaultValue> result = new LinkedHashMap<>();

    for (Map.Entry<String, Optional<String>> entry : fields.entrySet()) {
      result.put(entry.getKey(), parseNamedTupleField(anchor, entry.getValue().orElse(null), context));
    }

    return result;
  }

  @NotNull
  private static PyNamedTupleType.FieldTypeAndDefaultValue parseNamedTupleField(@NotNull PsiElement anchor,
                                                                                @Nullable String type,
                                                                                @NotNull TypeEvalContext context) {
    if (type == null) return new PyNamedTupleType.FieldTypeAndDefaultValue(null, null);

    final PyType pyType = Ref.deref(PyTypingTypeProvider.getStringBasedType(type, anchor, context));
    return new PyNamedTupleType.FieldTypeAndDefaultValue(pyType, null);
  }
}
