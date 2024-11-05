// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.impl.stubs.PyEnumAttributeStubType;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.PyEnumAttributeStub;
import com.jetbrains.python.psi.stubs.PyLiteralKind;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.getOpenFunctionCallType;
import static com.jetbrains.python.psi.PyUtil.as;


public final class PyStdlibTypeProvider extends PyTypeProviderBase {

  @NotNull
  private static final Set<String> OPEN_FUNCTIONS = ImmutableSet.of("os.fdopen", "posix.fdopen");

  @Nullable
  public static PyStdlibTypeProvider getInstance() {
    for (PyTypeProvider typeProvider : EP_NAME.getExtensionList()) {
      if (typeProvider instanceof PyStdlibTypeProvider) {
        return (PyStdlibTypeProvider)typeProvider;
      }
    }
    return null;
  }

  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    PyType type = getBaseStringType(referenceTarget);
    if (type != null) {
      return Ref.create(type);
    }
    Ref<PyType> enumType = getEnumType(referenceTarget, context, anchor);
    if (enumType != null) {
      return enumType;
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

    return null;
  }

  @Nullable
  private static PyType getBaseStringType(@NotNull PsiElement referenceTarget) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(referenceTarget);
    if (referenceTarget instanceof PyElement && builtinCache.isBuiltin(referenceTarget) &&
        PyNames.BASESTRING.equals(((PyElement)referenceTarget).getName())) {
      return builtinCache.getStrOrUnicodeType(true);
    }
    return null;
  }

  private static @Nullable Ref<PyType> getEnumType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context,
                                                   @Nullable PsiElement anchor) {
    @Nullable Ref<PyType> enumAttributeType = getEnumAttributeType(referenceTarget, context);
    if (enumAttributeType != null) {
      return enumAttributeType;
    }
    if (referenceTarget instanceof PyQualifiedNameOwner qualifiedNameOwner) {
      final String name = qualifiedNameOwner.getQualifiedName();
      if ((PyNames.TYPE_ENUM + ".name").equals(name)) {
        return Ref.create(PyBuiltinCache.getInstance(referenceTarget).getStrType());
      }
      else if ("enum.IntEnum.value".equals(name) && anchor instanceof PyReferenceExpression) {
        return Ref.create(PyBuiltinCache.getInstance(referenceTarget).getIntType());
      }
      else if ((PyNames.TYPE_ENUM + ".value").equals(name) &&
               anchor instanceof PyReferenceExpression anchorExpr && context.maySwitchToAST(anchor)) {
        final PyExpression qualifier = anchorExpr.getQualifier();
        // An enum value is retrieved programmatically, e.g. MyEnum[name].value, or just type-hinted
        if (qualifier != null) {
          PyClassType enumType = as(context.getType(qualifier), PyClassType.class);
          if (enumType != null) {
            PyClass enumClass = enumType.getPyClass();
            PyTargetExpression firstEnumItem = ContainerUtil.getFirstItem(enumClass.getClassAttributes());
            if (firstEnumItem != null && context.maySwitchToAST(firstEnumItem)) {
              final PyExpression value = firstEnumItem.findAssignedValue();
              if (value != null) {
                return Ref.create(context.getType(value));
              }
            }
            else {
              return Ref.create();
            }
          }
        }
      }
      else if ("enum.EnumMeta.__members__".equals(name)) {
        return Ref.create(PyTypeParser.getTypeByName(referenceTarget, "dict[str, unknown]", context));
      }
    }
    @Nullable PyType enumAutoType = getEnumAutoConstructorType(referenceTarget, context, anchor);
    if (enumAutoType != null) {
      return Ref.create(enumAutoType);
    }
    return null;
  }

  private static @Nullable Ref<PyType> getEnumAttributeType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    return RecursionManager.doPreventingRecursion(element, false, () -> getEnumAttributeTypeImpl(element, context));
  }

  private static @Nullable Ref<PyType> getEnumAttributeTypeImpl(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (!(element instanceof PyTargetExpression) && !(element instanceof PyDecoratable)) return null;
    if (!(ScopeUtil.getScopeOwner(element) instanceof PyClass cls && isCustomEnum(cls, context))) return null;

    if (element instanceof PyTargetExpression targetExpression) {
      EnumAttributeInfo info = getEnumAttributeInfo(targetExpression, context);
      if (info != null) {
        PyType assignedValueType = info.assignedValueType;
        if (info.isMemberOrAlias) {
          boolean isAlias = assignedValueType instanceof PyLiteralType literalType && literalType.getPyClass().equals(cls);
          if (!isAlias) {
            assignedValueType = PyLiteralType.enumMember(cls, Objects.requireNonNull(targetExpression.getName()));
          }
        }
        return Ref.create(assignedValueType);
      }
    }
    else if (isEnumMember((PyDecoratable)element, context)) {
      if (element instanceof PyQualifiedNameOwner qualifiedNameOwner) {
        String name = qualifiedNameOwner.getName();
        if (name != null) {
          return Ref.create(PyLiteralType.enumMember(cls, name));
        }
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public static boolean isCustomEnum(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    return isEnum(cls, context) && !PyNames.TYPE_ENUM.equals(cls.getQualifiedName());
  }

  @ApiStatus.Internal
  public static boolean isEnum(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    return cls.getMetaClassType(true, context) instanceof PyClassType metaClassType &&
           metaClassType.getPyClass().isSubclass(PyNames.TYPE_ENUM_META, context);
  }

  @ApiStatus.Internal
  public static @Nullable EnumAttributeInfo getEnumAttributeInfo(@NotNull PyTargetExpression targetExpression,
                                                                 @NotNull TypeEvalContext context) {
    assert ScopeUtil.getScopeOwner(targetExpression) instanceof PyClass cls && isCustomEnum(cls, context);

    String name = targetExpression.getName();
    if (name == null || PyUtil.isClassPrivateName(name)) return null;

    if (context.maySwitchToAST(targetExpression)) {
      PyExpression value = targetExpression.findAssignedValue();
      if (value == null) return null;

      PyType type = context.getType(value);
      return getEnumAttributeInfo(type, context);
    }
    else {
      if (!targetExpression.hasAssignedValue()) return null;

      PyTargetExpressionStub stub = targetExpression.getStub();
      PyEnumAttributeStub attributeStub = stub != null
                                          ? stub.getCustomStub(PyEnumAttributeStub.class)
                                          : new PyEnumAttributeStubType().createStub(targetExpression);
      if (attributeStub != null) {
        PyLiteralKind literalKind = attributeStub.getLiteralKind();
        PyType type = literalKind != null ? PyUtil.convertToType(literalKind, PyBuiltinCache.getInstance(targetExpression)) : null;
        return new EnumAttributeInfo(type, attributeStub.isMember());
      }

      QualifiedName assignedQName = targetExpression.getAssignedQName();
      if (assignedQName != null) {
        PyClass pyClass = (PyClass)ScopeUtil.getScopeOwner(targetExpression);
        assert pyClass != null;
        PsiElement value = ContainerUtil.getFirstItem(PyResolveUtil.resolveQualifiedNameInScope(assignedQName, pyClass, context));
        if (value != null) {
          Ref<PyType> type = getEnumAttributeType(value, context);
          return type == null ? null : getEnumAttributeInfo(type.get(), context);
        }
      }

      PyLiteralKind literalKind = stub != null
                                  ? stub.getAssignedLiteralKind()
                                  : PyLiteralKind.fromExpression(targetExpression.findAssignedValue());
      if (literalKind == null) {
        return new EnumAttributeInfo(null, false);
      }
      else {
        PyType type = PyUtil.convertToType(literalKind, PyBuiltinCache.getInstance(targetExpression));
        return new EnumAttributeInfo(type, true);
      }
    }
  }

  private static @NotNull EnumAttributeInfo getEnumAttributeInfo(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type == null) {
      return new EnumAttributeInfo(null, false);
    }
    PyQualifiedNameOwner typeDeclarationElement = type.getDeclarationElement();
    if (typeDeclarationElement != null) {
      String typeDeclarationQName = typeDeclarationElement.getQualifiedName();
      if (type instanceof PyCollectionType genericType) {
        PyType genericParameterType = ContainerUtil.getOnlyItem(genericType.getElementTypes());
        if (genericParameterType != null) {
          if (PyNames.TYPE_ENUM_MEMBER.equals(typeDeclarationQName)) {
            return new EnumAttributeInfo(genericParameterType, true);
          }
          if (PyNames.TYPE_ENUM_NONMEMBER.equals(typeDeclarationQName)) {
            return new EnumAttributeInfo(genericParameterType, false);
          }
        }
      }
    }
    if (typeDeclarationElement instanceof PyCallable) {
      return new EnumAttributeInfo(type, false);
    }
    boolean isDescriptor = !ContainerUtil.isEmpty(
      type.resolveMember(PyNames.DUNDER_GET, null, AccessDirection.READ, PyResolveContext.defaultContext(context)));
    if (isDescriptor) {
      return new EnumAttributeInfo(type, false);
    }
    return new EnumAttributeInfo(type, true);
  }

  @ApiStatus.Internal
  public record EnumAttributeInfo(@Nullable PyType assignedValueType, boolean isMemberOrAlias) { }

  @ApiStatus.Internal
  public static boolean isEnumMember(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    return PyKnownDecoratorUtil.getKnownDecorators(decoratable, context).contains(KnownDecorator.ENUM_MEMBER);
  }

  @Nullable
  private static PyType getEnumAutoConstructorType(@NotNull PsiElement target,
                                                   @NotNull TypeEvalContext context,
                                                   @Nullable PsiElement anchor) {
    if (target instanceof PyClass && PyNames.TYPE_ENUM_AUTO.equals(((PyClass)target).getQualifiedName()) && anchor instanceof PyCallExpression) {
      PyClassLikeType classType = as(context.getType((PyTypedElement)target), PyClassLikeType.class);
      if (classType != null) {
        return new PyCallableTypeImpl(Collections.emptyList(), classType.toInstance());
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getCallType(@NotNull PyFunction function, @NotNull PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    final String qname = function.getQualifiedName();
    if (qname != null) {
      if (OPEN_FUNCTIONS.contains(qname) && callSite instanceof PyCallExpression) {
        return getOpenFunctionCallType(function, (PyCallExpression)callSite, LanguageLevel.forElement(callSite), context);
      }
      else if ("tuple.__new__".equals(qname) && callSite instanceof PyCallExpression) {
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

        final List<PyType> newElementTypes = ContainerUtil.concat(leftTupleType.getElementTypes(),
                                                                  rightTupleType.getElementTypes());
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
}
