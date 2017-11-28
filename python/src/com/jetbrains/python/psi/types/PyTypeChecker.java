// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.pyi.PyiFile;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.*;

/**
 * @author vlan
 */
public class PyTypeChecker {
  private PyTypeChecker() {
  }

  public static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context) {
    return match(expected, actual, context, null, true);
  }

  /**
   * Checks whether a type *actual* can be placed where *expected* is expected.
   * For example int matches object, while str doesn't match int.
   * Work for builtin types, classes, tuples etc.
   *
   * @param expected      expected type
   * @param actual        type to be matched against expected
   * @param context
   * @param substitutions
   * @return
   */
  public static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context,
                              @Nullable Map<PyGenericType, PyType> substitutions) {
    return match(expected, actual, context, substitutions, true);
  }

  private static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context,
                               @Nullable Map<PyGenericType, PyType> substitutions, boolean recursive) {
    // TODO: subscriptable types?, module types?, etc.
    final PyClassType expectedClassType = as(expected, PyClassType.class);
    final PyClassType actualClassType = as(actual, PyClassType.class);
    
    // Special cases: object and type
    if (expectedClassType != null && ArrayUtil.contains(expectedClassType.getName(), PyNames.OBJECT, PyNames.TYPE)) {
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(expectedClassType.getPyClass());
      if (expectedClassType.equals(builtinCache.getObjectType())) {
        return true;
      }
      if (expectedClassType.equals(builtinCache.getTypeType()) &&
          actual instanceof PyInstantiableType && ((PyInstantiableType)actual).isDefinition()) {
        return true;
      }
    }
    if (expected instanceof PyInstantiableType && actual instanceof PyInstantiableType
        && !(expected instanceof PyGenericType && typeVarAcceptsBothClassAndInstanceTypes((PyGenericType)expected))
        && ((PyInstantiableType)expected).isDefinition() ^ ((PyInstantiableType)actual).isDefinition()) {
      if (((PyInstantiableType)actual).isDefinition() && !((PyInstantiableType)expected).isDefinition()) {
        if (actual instanceof PyClassLikeType && matchClassObjectAndMetaclass(expected, (PyClassLikeType)actual, context)) {
          return true;
        }
      }
      return false;
    }
    if (expected instanceof PyGenericType && substitutions != null) {
      final PyGenericType generic = (PyGenericType)expected;
      final PyType subst = substitutions.get(generic);
      PyType bound = generic.getBound();
      // Promote int in Type[TypeVar('T', int)] to Type[int] before checking that bounds match
      if (generic.isDefinition() && bound instanceof PyInstantiableType) {
        bound = ((PyInstantiableType)bound).toClass();
      }
      if (!match(bound, actual, context, substitutions, recursive)) {
        return false;
      }
      else if (subst != null) {
        if (expected.equals(actual) || subst.equals(generic)) {
          return true;
        }
        else if (recursive) {
          return match(subst, actual, context, substitutions, false);
        }
        else {
          return false;
        }
      }
      else if (actual != null) {
        substitutions.put(generic, actual);
      }
      else if (bound != null) {
        substitutions.put(generic, bound);
      }
      return true;
    }
    if (expected == null || actual == null) {
      return true;
    }
    if (isUnknown(actual, context)) {
      return true;
    }
    if (actual instanceof PyUnionType) {
      final PyUnionType actualUnionType = (PyUnionType)actual;

      if (expected instanceof PyTupleType) {
        final PyTupleType expectedTupleType = (PyTupleType)expected;
        final int elementCount = expectedTupleType.getElementCount();

        if (!expectedTupleType.isHomogeneous() && consistsOfSameElementNumberTuples(actualUnionType, elementCount)) {
          return substituteExpectedElementsWithUnions(expectedTupleType, elementCount, actualUnionType, context, substitutions, recursive);
        }
      }

      for (PyType m : actualUnionType.getMembers()) {
        if (match(expected, m, context, substitutions, recursive)) {
          return true;
        }
      }
      return false;
    }
    if (expected instanceof PyUnionType) {
      final Collection<PyType> expectedUnionTypeMembers = ((PyUnionType)expected).getMembers();
      final StreamEx<PyType> notGenericTypes = StreamEx.of(expectedUnionTypeMembers).filter(type -> !PyGenericType.class.isInstance(type));
      final StreamEx<PyGenericType> genericTypes = StreamEx.of(expectedUnionTypeMembers).select(PyGenericType.class);

      for (PyType t : notGenericTypes.append(genericTypes)) {
        if (match(t, actual, context, substitutions, recursive)) {
          return true;
        }
      }
      return false;
    }
    if (expectedClassType != null && actualClassType != null) {
      final PyClass superClass = expectedClassType.getPyClass();
      final PyClass subClass = actualClassType.getPyClass();
      if (expected instanceof PyTupleType && actual instanceof PyTupleType) {
        final PyTupleType superTupleType = (PyTupleType)expected;
        final PyTupleType subTupleType = (PyTupleType)actual;
        if (!superTupleType.isHomogeneous() && !subTupleType.isHomogeneous()) {
          if (superTupleType.getElementCount() != subTupleType.getElementCount()) {
            return false;
          }
          else {
            for (int i = 0; i < superTupleType.getElementCount(); i++) {
              if (!match(superTupleType.getElementType(i), subTupleType.getElementType(i), context, substitutions, recursive)) {
                return false;
              }
            }
            return true;
          }
        }
        else if (superTupleType.isHomogeneous() && !subTupleType.isHomogeneous()) {
          final PyType expectedElementType = superTupleType.getIteratedItemType();
          for (int i = 0; i < subTupleType.getElementCount(); i++) {
            if (!match(expectedElementType, subTupleType.getElementType(i), context, substitutions, recursive)) {
              return false;
            }
          }
          return true;
        }
        else if (!superTupleType.isHomogeneous() && subTupleType.isHomogeneous()) {
          return false;
        }
        else {
          return match(superTupleType.getIteratedItemType(), subTupleType.getIteratedItemType(), context, substitutions, recursive);
        }
      }
      else if (expected instanceof PyCollectionType && actual instanceof PyTupleType) {
        if (!matchClasses(superClass, subClass, context)) {
          return false;
        }

        final PyType superElementType = ((PyCollectionType)expected).getIteratedItemType();
        final PyType subElementType = ((PyTupleType)actual).getIteratedItemType();

        if (!match(superElementType, subElementType, context, substitutions, recursive)) {
          return false;
        }

        return true;
      }
      else if (expected instanceof PyCollectionType) {
        if (!matchClasses(superClass, subClass, context)) {
          return false;
        }
        // TODO: Match generic parameters based on the correspondence between the generic parameters of subClass and its base classes
        final List<PyType> superElementTypes = ((PyCollectionType)expected).getElementTypes();
        final PyCollectionType actualCollectionType = as(actual, PyCollectionType.class);
        final List<PyType> subElementTypes = actualCollectionType != null ?
                                             actualCollectionType.getElementTypes() :
                                             Collections.emptyList();
        for (int i = 0; i < superElementTypes.size(); i++) {
          final PyType subElementType = i < subElementTypes.size() ? subElementTypes.get(i) : null;
          if (!match(superElementTypes.get(i), subElementType, context, substitutions, recursive)) {
            return false;
          }
        }
        return true;
      }

      else if (matchClasses(superClass, subClass, context)) {
        return true;
      }
      else if (actualClassType.isDefinition() && PyNames.CALLABLE.equals(expected.getName())) {
        return true;
      }
      if (expected.equals(actual)) {
        return true;
      }
    }
    if (actual instanceof PyFunctionTypeImpl && expectedClassType != null) {
      final PyClass superClass = expectedClassType.getPyClass();
      if (PyNames.CALLABLE.equals(superClass.getName())) {
        return true;
      }
    }
    if (actual instanceof PyStructuralType && ((PyStructuralType)actual).isInferredFromUsages()) {
      return true;
    }
    if (expected instanceof PyStructuralType && actual instanceof PyStructuralType) {
      final PyStructuralType expectedStructural = (PyStructuralType)expected;
      final PyStructuralType actualStructural = (PyStructuralType)actual;
      if (expectedStructural.isInferredFromUsages()) {
        return true;
      }
      return expectedStructural.getAttributeNames().containsAll(actualStructural.getAttributeNames());
    }
    if (expected instanceof PyStructuralType && actualClassType != null) {
      if (overridesGetAttr(actualClassType.getPyClass(), context)) {
        return true;
      }
      final Set<String> actualAttributes = actualClassType.getMemberNames(true, context);
      return actualAttributes.containsAll(((PyStructuralType)expected).getAttributeNames());
    }
    if (expected instanceof PyStructuralType) {
      final Set<String> expectedAttributes = ((PyStructuralType)expected).getAttributeNames();
      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);

      return expectedAttributes
        .stream()
        .noneMatch(attribute -> ContainerUtil.isEmpty(actual.resolveMember(attribute, null, AccessDirection.READ, resolveContext)));
    }
    if (actual instanceof PyStructuralType && expectedClassType != null) {
      final Set<String> expectedAttributes = expectedClassType.getMemberNames(true, context);
      return expectedAttributes.containsAll(((PyStructuralType)actual).getAttributeNames());
    }
    if (actual instanceof PyCallableType && expected instanceof PyCallableType) {
      final PyCallableType expectedCallable = (PyCallableType)expected;
      final PyCallableType actualCallable = (PyCallableType)actual;
      if (expectedCallable.isCallable() && actualCallable.isCallable()) {
        final List<PyCallableParameter> expectedParameters = expectedCallable.getParameters(context);
        final List<PyCallableParameter> actualParameters = actualCallable.getParameters(context);
        if (expectedParameters != null && actualParameters != null) {
          final int size = Math.min(expectedParameters.size(), actualParameters.size());
          for (int i = 0; i < size; i++) {
            final PyCallableParameter expectedParam = expectedParameters.get(i);
            final PyCallableParameter actualParam = actualParameters.get(i);
            // TODO: Check named and star params, not only positional ones
            if (!match(expectedParam.getType(context), actualParam.getType(context), context, substitutions, recursive)) {
              return false;
            }
          }
        }
        if (!match(expectedCallable.getReturnType(context), actualCallable.getReturnType(context), context, substitutions, recursive)) {
          return false;
        }
        return true;
      }
    }
    return matchNumericTypes(expected, actual);
  }

  private static boolean matchClassObjectAndMetaclass(@NotNull PyType expected,
                                                      @NotNull PyClassLikeType actual,
                                                      @NotNull TypeEvalContext context) {

    if (!actual.isDefinition()) {
      return false;
    }
    final PyClassLikeType metaClass = actual.getMetaClassType(context, true);
    return metaClass != null && match(expected, metaClass, context);
  }

  private static boolean typeVarAcceptsBothClassAndInstanceTypes(@NotNull PyGenericType typeVar) {
    return !typeVar.isDefinition() && typeVar.getBound() == null;
  }

  private static boolean consistsOfSameElementNumberTuples(@NotNull PyUnionType unionType, int elementCount) {
    for (PyType type : unionType.getMembers()) {
      if (type instanceof PyTupleType) {
        final PyTupleType tupleType = (PyTupleType)type;

        if (!tupleType.isHomogeneous() && elementCount != tupleType.getElementCount()) {
          return false;
        }
      }
      else {
        return false;
      }
    }

    return true;
  }

  private static boolean substituteExpectedElementsWithUnions(@NotNull PyTupleType expected,
                                                              int elementCount,
                                                              @NotNull PyUnionType actual,
                                                              @NotNull TypeEvalContext context,
                                                              @Nullable Map<PyGenericType, PyType> substitutions,
                                                              boolean recursive) {
    for (int i = 0; i < elementCount; i++) {
      final int currentIndex = i;

      final PyType elementType = PyUnionType.union(
        StreamEx
          .of(actual.getMembers())
          .select(PyTupleType.class)
          .map(type -> type.getElementType(currentIndex))
          .toList()
      );

      if (!match(expected.getElementType(i), elementType, context, substitutions, recursive)) {
        return false;
      }
    }

    return true;
  }

  private static boolean matchNumericTypes(PyType expected, PyType actual) {
    final String superName = expected.getName();
    final String subName = actual.getName();
    final boolean subIsBool = "bool".equals(subName);
    final boolean subIsInt = PyNames.TYPE_INT.equals(subName);
    final boolean subIsLong = PyNames.TYPE_LONG.equals(subName);
    final boolean subIsFloat = "float".equals(subName);
    final boolean subIsComplex = "complex".equals(subName);
    if (superName == null || subName == null ||
        superName.equals(subName) ||
        (PyNames.TYPE_INT.equals(superName) && subIsBool) ||
        ((PyNames.TYPE_LONG.equals(superName) || PyNames.ABC_INTEGRAL.equals(superName)) && (subIsBool || subIsInt)) ||
        (("float".equals(superName) || PyNames.ABC_REAL.equals(superName)) && (subIsBool || subIsInt || subIsLong)) ||
        (("complex".equals(superName) || PyNames.ABC_COMPLEX.equals(superName)) && (subIsBool || subIsInt || subIsLong || subIsFloat)) ||
        (PyNames.ABC_NUMBER.equals(superName) && (subIsBool || subIsInt || subIsLong || subIsFloat || subIsComplex))) {
      return true;
    }
    return false;
  }

  public static boolean isUnknown(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return isUnknown(type, true, context);
  }

  public static boolean isUnknown(@Nullable PyType type, boolean genericsAreUnknown, @NotNull TypeEvalContext context) {
    if (type == null || (genericsAreUnknown && type instanceof PyGenericType)) {
      return true;
    }
    if (type instanceof PyFunctionType) {
      final PyCallable callable = ((PyFunctionType)type).getCallable();
      if (callable instanceof PyDecoratable &&
          PyKnownDecoratorUtil.hasUnknownOrChangingReturnTypeDecorator((PyDecoratable)callable, context)){
        return true;
      }
    }
    if (type instanceof PyUnionType) {
      final PyUnionType union = (PyUnionType)type;
      for (PyType t : union.getMembers()) {
        if (isUnknown(t, genericsAreUnknown, context)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static PyType toNonWeakType(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type instanceof PyUnionType) {
      final PyUnionType unionType = (PyUnionType)type;
      if (unionType.isWeak()) {
        return unionType.excludeNull(context);
      }
    }
    return type;
  }

  public static boolean hasGenerics(@Nullable PyType type, @NotNull TypeEvalContext context) {
    final Set<PyGenericType> collected = new HashSet<>();
    collectGenerics(type, context, collected, new HashSet<>());
    return !collected.isEmpty();
  }

  private static void collectGenerics(@Nullable PyType type, @NotNull TypeEvalContext context, @NotNull Set<PyGenericType> collected,
                                      @NotNull Set<PyType> visited) {
    if (visited.contains(type)) {
      return;
    }
    visited.add(type);
    if (type instanceof PyGenericType) {
      collected.add((PyGenericType)type);
    }
    else if (type instanceof PyUnionType) {
      final PyUnionType union = (PyUnionType)type;
      for (PyType t : union.getMembers()) {
        collectGenerics(t, context, collected, visited);
      }
    }
    else if (type instanceof PyTupleType) {
      final PyTupleType tuple = (PyTupleType)type;
      final int n = tuple.isHomogeneous() ? 1 : tuple.getElementCount();
      for (int i = 0; i < n; i++) {
        collectGenerics(tuple.getElementType(i), context, collected, visited);
      }
    }
    else if (type instanceof PyCollectionType) {
      final PyCollectionType collection = (PyCollectionType)type;
      for (PyType elementType : collection.getElementTypes()) {
        collectGenerics(elementType, context, collected, visited);
      }
    }
    else if (type instanceof PyCallableType) {
      final PyCallableType callable = (PyCallableType)type;
      final List<PyCallableParameter> parameters = callable.getParameters(context);
      if (parameters != null) {
        for (PyCallableParameter parameter : parameters) {
          if (parameter != null) {
            collectGenerics(parameter.getType(context), context, collected, visited);
          }
        }
      }
      collectGenerics(callable.getReturnType(context), context, collected, visited);
    }
  }

  @Nullable
  public static PyType substitute(@Nullable PyType type, @NotNull Map<PyGenericType, PyType> substitutions,
                                  @NotNull TypeEvalContext context) {
    if (hasGenerics(type, context)) {
      if (type instanceof PyGenericType) {
        final PyGenericType typeVar = (PyGenericType)type;
        PyType substitution = substitutions.get(typeVar);
        if (substitution == null) {
          if (!typeVar.isDefinition()) {
            final PyInstantiableType<?> classType = as(substitutions.get(typeVar.toClass()), PyInstantiableType.class);
            if (classType != null) {
              substitution = classType.toInstance();
            }
          }
          else {
            final PyInstantiableType<?> instanceType = as(substitutions.get(typeVar.toInstance()), PyInstantiableType.class);
            if (instanceType != null) {
              substitution = instanceType.toClass();
            }
          }
        }
        if (substitution instanceof PyGenericType && !typeVar.equals(substitution) && substitutions.containsKey(substitution)) {
          return substitute(substitution, substitutions, context);
        }
        return substitution;
      }
      else if (type instanceof PyUnionType) {
        final PyUnionType union = (PyUnionType)type;
        final List<PyType> results = new ArrayList<>();
        for (PyType t : union.getMembers()) {
          final PyType subst = substitute(t, substitutions, context);
          results.add(subst);
        }
        return PyUnionType.union(results);
      }
      else if (type instanceof PyCollectionTypeImpl) {
        final PyCollectionTypeImpl collection = (PyCollectionTypeImpl)type;
        final List<PyType> elementTypes = collection.getElementTypes();
        final List<PyType> substitutes = new ArrayList<>();
        for (PyType elementType : elementTypes) {
          substitutes.add(substitute(elementType, substitutions, context));
        }
        return new PyCollectionTypeImpl(collection.getPyClass(), collection.isDefinition(), substitutes);
      }
      else if (type instanceof PyTupleType) {
        final PyTupleType tupleType = (PyTupleType)type;
        final PyClass tupleClass = tupleType.getPyClass();

        final List<PyType> oldElementTypes = tupleType.isHomogeneous()
                                             ? Collections.singletonList(tupleType.getIteratedItemType())
                                             : tupleType.getElementTypes();

        final List<PyType> newElementTypes =
          ContainerUtil.map(oldElementTypes, elementType -> substitute(elementType, substitutions, context));

        return new PyTupleType(tupleClass, newElementTypes, tupleType.isHomogeneous());
      }
      else if (type instanceof PyCallableType) {
        final PyCallableType callable = (PyCallableType)type;
        List<PyCallableParameter> substParams = null;
        final List<PyCallableParameter> parameters = callable.getParameters(context);
        if (parameters != null) {
          substParams = new ArrayList<>();
          for (PyCallableParameter parameter : parameters) {
            final PyType substType = substitute(parameter.getType(context), substitutions, context);
            final PyParameter psi = parameter.getParameter();
            final PyCallableParameter subst = psi != null ?
                                              PyCallableParameterImpl.psi(psi, substType) :
                                              PyCallableParameterImpl.nonPsi(parameter.getName(), substType, parameter.getDefaultValue());
            substParams.add(subst);
          }
        }
        final PyType substResult = substitute(callable.getReturnType(context), substitutions, context);
        return new PyCallableTypeImpl(substParams, substResult);
      }
    }
    return type;
  }

  @Nullable
  public static Map<PyGenericType, PyType> unifyGenericCall(@Nullable PyExpression receiver,
                                                            @NotNull Map<PyExpression, PyCallableParameter> arguments,
                                                            @NotNull TypeEvalContext context) {
    final Map<PyGenericType, PyType> substitutions = unifyReceiver(receiver, context);
    for (Map.Entry<PyExpression, PyCallableParameter> entry : getRegularMappedParameters(arguments).entrySet()) {
      final PyCallableParameter paramWrapper = entry.getValue();
      PyType actualType = context.getType(entry.getKey());
      if (paramWrapper.isSelf()) {
        // TODO find out a better way to pass the corresponding function inside
        final PyParameter param = paramWrapper.getParameter();
        final PyFunction function = as(ScopeUtil.getScopeOwner(param), PyFunction.class);
        if (function != null && function.getModifier() == PyFunction.Modifier.CLASSMETHOD) {
          final StreamEx<PyType> types;
          if (actualType instanceof PyUnionType) {
            types = StreamEx.of(((PyUnionType)actualType).getMembers());
          }
          else {
            types = StreamEx.of(actualType);
          }
          actualType = types
            .select(PyClassLikeType.class)
            .map(PyClassLikeType::toClass)
            .select(PyType.class)
            .foldLeft(PyUnionType::union)
            .orElse(actualType);
        }
      }
      final PyType expectedType = paramWrapper.getArgumentType(context);
      if (!match(expectedType, actualType, context, substitutions)) {
        return null;
      }
    }
    if (!matchContainer(getMappedPositionalContainer(arguments), getArgumentsMappedToPositionalContainer(arguments), substitutions,
                        context)) {
      return null;
    }
    if (!matchContainer(getMappedKeywordContainer(arguments), getArgumentsMappedToKeywordContainer(arguments), substitutions, context)) {
      return null;
    }
    return substitutions;
  }

  private static boolean matchContainer(@Nullable PyCallableParameter container, @NotNull List<PyExpression> arguments,
                                        @NotNull Map<PyGenericType, PyType> substitutions, @NotNull TypeEvalContext context) {
    if (container == null) {
      return true;
    }
    final List<PyType> types = ContainerUtil.map(arguments, context::getType);
    return match(container.getArgumentType(context), PyUnionType.union(types), context, substitutions);
  }

  @NotNull
  public static Map<PyGenericType, PyType> unifyReceiver(@Nullable PyExpression receiver, @NotNull TypeEvalContext context) {
    final Map<PyGenericType, PyType> substitutions = new LinkedHashMap<>();
    // Collect generic params of object type
    final Set<PyGenericType> generics = new LinkedHashSet<>();
    final PyType qualifierType = receiver != null ? context.getType(receiver) : null;
    collectGenerics(qualifierType, context, generics, new HashSet<>());
    for (PyGenericType t : generics) {
      substitutions.put(t, t);
    }
    if (qualifierType != null) {
      for (PyClassType type : toPossibleClassTypes(qualifierType)) {
        for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
          final PyType genericType = provider.getGenericType(type.getPyClass(), context);
          if (genericType != null) {
            match(genericType, type, context, substitutions);
          }
          for (Map.Entry<PyType, PyType> entry : provider.getGenericSubstitutions(type.getPyClass(), context).entrySet()) {
            final PyGenericType genericKey = as(entry.getKey(), PyGenericType.class);
            final PyType value = entry.getValue();
            if (genericKey != null && value != null && !substitutions.containsKey(genericKey)) {
              substitutions.put(genericKey, value);
            }
          }
        }
      }
    }

    replaceUnresolvedGenericsWithAny(substitutions);
    return substitutions;
  }

  @NotNull
  private static List<PyClassType> toPossibleClassTypes(@NotNull PyType type) {
    final PyClassType classType = as(type, PyClassType.class);
    if (classType != null) {
      return Collections.singletonList(classType);
    }
    final PyUnionType unionType = as(type, PyUnionType.class);
    if (unionType != null) {
      return StreamEx.of(unionType.getMembers()).nonNull().flatMap(t -> toPossibleClassTypes(t).stream()).toList();
    }
    return Collections.emptyList();
  }

  private static void replaceUnresolvedGenericsWithAny(@NotNull Map<PyGenericType, PyType> substitutions) {
    final List<PyType> unresolvedGenerics =
      ContainerUtil.filter(substitutions.values(), type -> type instanceof PyGenericType && !substitutions.containsKey(type));

    for (PyType unresolvedGeneric : unresolvedGenerics) {
      substitutions.put((PyGenericType)unresolvedGeneric, null);
    }
  }

  private static boolean matchClasses(@Nullable PyClass superClass, @Nullable PyClass subClass, @NotNull TypeEvalContext context) {
    if (superClass == null ||
        subClass == null ||
        subClass.isSubclass(superClass, context) ||
        PyABCUtil.isSubclass(subClass, superClass, context) ||
        isStrUnicodeMatch(subClass, superClass) ||
        isBytearrayBytesStringMatch(subClass, superClass) ||
        PyUtil.hasUnresolvedAncestors(subClass, context)) {
      return true;
    }
    else {
      final String superName = superClass.getName();
      return superName != null && superName.equals(subClass.getName());
    }
  }

  private static boolean isStrUnicodeMatch(@NotNull PyClass subClass, @NotNull PyClass superClass) {
    // TODO: Check for subclasses as well
    return PyNames.TYPE_STR.equals(subClass.getName()) && PyNames.TYPE_UNICODE.equals(superClass.getName());
  }

  private static boolean isBytearrayBytesStringMatch(@NotNull PyClass subClass, @NotNull PyClass superClass) {
    if (!PyNames.TYPE_BYTEARRAY.equals(subClass.getName())) return false;

    final PsiFile subClassFile = subClass.getContainingFile();

    final boolean isPy2 = subClassFile instanceof PyiFile
                          ? PyBuiltinCache.getInstance(subClass).getObjectType(PyNames.TYPE_UNICODE) != null
                          : LanguageLevel.forElement(subClass).isOlderThan(LanguageLevel.PYTHON30);

    final String superClassName = superClass.getName();
    return isPy2 && PyNames.TYPE_STR.equals(superClassName) || !isPy2 && PyNames.TYPE_BYTES.equals(superClassName);
  }

  @Nullable
  public static Boolean isCallable(@Nullable PyType type) {
    if (type == null) {
      return null;
    }
    if (type instanceof PyUnionType) {
      return isUnionCallable((PyUnionType)type);
    }
    if (type instanceof PyCallableType) {
      return ((PyCallableType)type).isCallable();
    }
    if (type instanceof PyStructuralType && ((PyStructuralType)type).isInferredFromUsages()) {
      return true;
    }
    return false;
  }

  /**
   * If at least one is callable -- it is callable.
   * If at least one is unknown -- it is unknown.
   * It is false otherwise.
   */
  @Nullable
  private static Boolean isUnionCallable(@NotNull final PyUnionType type) {
    for (final PyType member : type.getMembers()) {
      final Boolean callable = isCallable(member);
      if (callable == null) {
        return null;
      }
      if (callable) {
        return true;
      }
    }
    return false;
  }

  public static boolean overridesGetAttr(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    PsiElement method = resolveClassMember(cls, PyNames.GETATTR, context);
    if (method != null) {
      return true;
    }
    method = resolveClassMember(cls, PyNames.GETATTRIBUTE, context);
    if (method != null && !PyBuiltinCache.getInstance(cls).isBuiltin(method)) {
      return true;
    }
    return false;
  }

  @Nullable
  private static PsiElement resolveClassMember(@NotNull PyClass cls, @NotNull String name, @NotNull TypeEvalContext context) {
    final PyType type = context.getType(cls);
    if (type != null) {
      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
      final List<? extends RatedResolveResult> results = type.resolveMember(name, null, AccessDirection.READ, resolveContext);
      if (results != null && !results.isEmpty()) {
        return results.get(0).getElement();
      }
    }
    return null;
  }

  @Nullable
  public static PyType getTargetTypeFromTupleAssignment(@NotNull PyTargetExpression target,
                                                        @NotNull PyTupleExpression parentTuple,
                                                        @NotNull PyType assignedType,
                                                        @NotNull TypeEvalContext context) {
    if (assignedType instanceof PyTupleType) {
      return getTargetTypeFromTupleAssignment(target, parentTuple, (PyTupleType)assignedType);
    }
    else if (assignedType instanceof PyClassLikeType) {
      return StreamEx
        .of(((PyClassLikeType)assignedType).getAncestorTypes(context))
        .select(PyNamedTupleType.class)
        .findFirst()
        .map(t -> getTargetTypeFromTupleAssignment(target, parentTuple, t))
        .orElse(null);
    }

    return null;
  }

  @Nullable
  public static PyType getTargetTypeFromTupleAssignment(@NotNull PyTargetExpression target, @NotNull PyTupleExpression parentTuple,
                                                        @NotNull PyTupleType assignedTupleType) {
    final int count = assignedTupleType.getElementCount();
    final PyExpression[] elements = parentTuple.getElements();
    if (elements.length == count || assignedTupleType.isHomogeneous()) {
      final int index = ArrayUtil.indexOf(elements, target);
      if (index >= 0) {
        return assignedTupleType.getElementType(index);
      }
      for (int i = 0; i < count; i++) {
        PyExpression element = elements[i];
        while (element instanceof PyParenthesizedExpression) {
          element = ((PyParenthesizedExpression)element).getContainedExpression();
        }
        if (element instanceof PyTupleExpression) {
          final PyType elementType = assignedTupleType.getElementType(i);
          if (elementType instanceof PyTupleType) {
            final PyType result = getTargetTypeFromTupleAssignment(target, (PyTupleExpression)element, (PyTupleType)elementType);
            if (result != null) {
              return result;
            }
          }
        }
      }
    }
    return null;
  }
}
