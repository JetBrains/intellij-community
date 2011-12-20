package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Ref;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author vlan
 */
public class PyTypeChecker {
  private PyTypeChecker() {
  }

  public static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context) {
    return match(expected, actual, context, null, true);
  }

  public static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context,
                              @Nullable Map<PyGenericType, PyType> substitutions) {
    return match(expected, actual, context, substitutions, true);
  }

  private static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context,
                               @Nullable Map<PyGenericType, PyType> substitutions, boolean resolveReferences) {
    // TODO: subscriptable types?, module types?, etc.
    if (expected == null || actual == null) {
      return true;
    }
    if (expected instanceof PyClassType) {
      final PyClass c = ((PyClassType)expected).getPyClass();
      if (c != null && "object".equals(c.getName())) {
        return true;
      }
    }
    if ((expected instanceof PyTypeReference || actual instanceof PyTypeReference) && !resolveReferences) {
      return true;
    }
    if (expected instanceof PyTypeReference) {
      return match(((PyTypeReference)expected).resolve(null, context), actual, context, substitutions, resolveReferences);
    }
    if (actual instanceof PyTypeReference) {
      return match(expected, ((PyTypeReference)actual).resolve(null, context), context, substitutions, false);
    }
    if (isUnknown(actual)) {
      return true;
    }
    if (actual instanceof PyUnionType) {
      for (PyType m : ((PyUnionType)actual).getMembers()) {
        if (!match(expected, m, context, substitutions, resolveReferences)) {
          return false;
        }
      }
      return true;
    }
    if (expected instanceof PyUnionType) {
      for (PyType t : ((PyUnionType)expected).getMembers()) {
        if (match(t, actual, context, substitutions, resolveReferences)) {
          return true;
        }
      }
      return false;
    }
    if (expected instanceof PyClassType && actual instanceof PyClassType) {
      final PyClass superClass = ((PyClassType)expected).getPyClass();
      final PyClass subClass = ((PyClassType)actual).getPyClass();
      if (expected instanceof PyCollectionType && actual instanceof PyCollectionType) {
        if (!matchClasses(superClass, subClass)) {
          return false;
        }
        final PyType superElementType = ((PyCollectionType)expected).getElementType(context);
        final PyType subElementType = ((PyCollectionType)actual).getElementType(context);
        return match(superElementType, subElementType, context, substitutions, resolveReferences);
      }
      else if (expected instanceof PyTupleType && actual instanceof PyTupleType) {
        final PyTupleType superTupleType = (PyTupleType)expected;
        final PyTupleType subTupleType = (PyTupleType)actual;
        if (superTupleType.getElementCount() != subTupleType.getElementCount()) {
          return false;
        }
        else {
          for (int i = 0; i < superTupleType.getElementCount(); i++) {
            if (!match(superTupleType.getElementType(i), subTupleType.getElementType(i), context, substitutions, resolveReferences)) {
              return false;
            }
          }
          return true;
        }
      }
      else if (matchClasses(superClass, subClass)) {
        return true;
      }
      else if (((PyClassType)actual).isDefinition() && PyNames.CALLABLE.equals(expected.getName())) {
        return true;
      }
    }
    if (expected.equals(actual)) {
      return true;
    }
    if (expected instanceof PyGenericType && substitutions != null) {
      final PyGenericType generic = (PyGenericType)expected;
      final PyType subst = substitutions.get(generic);
      if (subst != null) {
        return match(subst, actual, context, substitutions, resolveReferences);
      }
      else {
        substitutions.put(generic, actual);
        return true;
      }
    }
    final String superName = expected.getName();
    final String subName = actual.getName();
    // TODO: No inheritance check for builtin numerics at this moment
    final boolean subIsBool = "bool".equals(subName);
    final boolean subIsInt = "int".equals(subName);
    final boolean subIsLong = "long".equals(subName);
    final boolean subIsFloat = "float".equals(subName);
    if (superName == null || subName == null ||
        superName.equals(subName) ||
        ("int".equals(superName) && subIsBool) ||
        ("long".equals(superName) && (subIsBool || subIsInt)) ||
        ("float".equals(superName) && (subIsBool || subIsInt || subIsLong)) ||
        ("complex".equals(superName) && (subIsBool || subIsInt || subIsLong || subIsFloat))) {
      return true;
    }
    return false;
  }

  public static boolean isUnknown(@Nullable PyType type) {
    if (type == null) {
      return true;
    }
    if (type instanceof PyUnionType) {
      final PyUnionType union = (PyUnionType)type;
      for (PyType t : union.getMembers()) {
        if (isUnknown(t)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean hasGenerics(@Nullable PyType type, @NotNull TypeEvalContext context) {
    final Set<PyGenericType> collected = new HashSet<PyGenericType>();
    collectGenerics(type, context, collected);
    return !collected.isEmpty();
  }

  private static void collectGenerics(@Nullable PyType type, @NotNull TypeEvalContext context, @NotNull Set<PyGenericType> collected) {
    if (type instanceof PyGenericType) {
      collected.add((PyGenericType)type);
    }
    else if (type instanceof PyUnionType) {
      final PyUnionType union = (PyUnionType)type;
      for (PyType t : union.getMembers()) {
        collectGenerics(t, context, collected);
      }
    }
    else if (type instanceof PyCollectionType) {
      final PyCollectionType collection = (PyCollectionType)type;
      collectGenerics(collection.getElementType(context), context, collected);
    }
    else if (type instanceof PyTupleType) {
      final PyTupleType tuple = (PyTupleType)type;
      final int n = tuple.getElementCount();
      for (int i = 0; i < n; i++) {
        collectGenerics(tuple.getElementType(i), context, collected);
      }
    }
  }

  @Nullable
  public static Ref<PyType> substitute(@Nullable PyType type, @NotNull Map<PyGenericType, PyType> substitutions,
                                       @NotNull TypeEvalContext context) {
    if (hasGenerics(type, context)) {
      if (type instanceof PyGenericType) {
        final PyType subst = substitutions.get((PyGenericType)type);
        return subst != null ? Ref.create(subst) : null;
      }
      else if (type instanceof PyUnionType) {
        final PyUnionType union = (PyUnionType)type;
        final List<PyType> results = new ArrayList<PyType>();
        for (PyType t : union.getMembers()) {
          final Ref<PyType> subst = substitute(t, substitutions, context);
          if (subst == null) {
            return null;
          }
          results.add(subst.get());
        }
        return Ref.create(PyUnionType.union(results));
      }
      else if (type instanceof PyCollectionTypeImpl) {
        final PyCollectionTypeImpl collection = (PyCollectionTypeImpl)type;
        final PyType elem = collection.getElementType(context);
        final Ref<PyType> subst = substitute(elem, substitutions, context);
        if (subst == null) {
          return null;
        }
        final PyType result = new PyCollectionTypeImpl(collection.getPyClass(), collection.isDefinition(), subst.get());
        return Ref.create(result);
      }
      else if (type instanceof PyTupleType) {
        final PyTupleType tuple = (PyTupleType)type;
        final int n = tuple.getElementCount();
        final List<PyType> results = new ArrayList<PyType>();
        for (int i = 0; i < n; i++) {
          final Ref<PyType> subst = substitute(tuple.getElementType(i), substitutions, context);
          if (subst == null) {
            return null;
          }
          results.add(subst.get());
        }
        final PyType result = new PyTupleType((PyTupleType)type, results.toArray(new PyType[results.size()]));
        return Ref.create(result);
      }
    }
    return Ref.create(type);
  }

  @Nullable
  public static Map<PyGenericType, PyType> unifyGenericCall(@NotNull PyFunction function, @NotNull PyCallExpression call,
                                                            @NotNull TypeEvalContext context) {
    final PyArgumentList args = call.getArgumentList();
    if (args == null) {
      return null;
    }
    final Map<PyGenericType, PyType> substitutions = collectCallGenerics(function, call, context);
    final CallArgumentsMapping res = args.analyzeCall(PyResolveContext.noImplicits().withTypeEvalContext(context));
    for (Map.Entry<PyExpression, PyNamedParameter> entry : res.getPlainMappedParams().entrySet()) {
      final PyNamedParameter p = entry.getValue();
      final String name = p.getName();
      if (p.isPositionalContainer() || p.isKeywordContainer() || name == null) {
        continue;
      }
      final PyType argType = entry.getKey().getType(context);
      final PyType paramType = p.getType(context);
      if (!match(paramType, argType, context, substitutions)) {
        return null;
      }
    }
    return substitutions;
  }

  @NotNull
  public static Map<PyGenericType, PyType> collectCallGenerics(@NotNull PyFunction function, @NotNull PyCallExpression call,
                                                               @NotNull TypeEvalContext context) {
    final Map<PyGenericType, PyType> substitutions = new HashMap<PyGenericType, PyType>();
    final PyExpression callee = call.getCallee();
    if (callee instanceof PyReferenceExpression) {
      final PyReferenceExpression expr = (PyReferenceExpression)callee;
      final PyExpression qualifier = expr.getQualifier();
      if (qualifier != null) {
        final PyType qualType = qualifier.getType(context);
        // Collect generic params of object type
        final Set<PyGenericType> generics = new HashSet<PyGenericType>();
        collectGenerics(qualType, context, generics);
        for (PyGenericType t : generics) {
          substitutions.put(t, t);
        }
        final PyClass cls = function.getContainingClass();
        if (cls != null) {
          final PyFunction init = cls.findInitOrNew(true);
          // Unify generics in constructor
          if (init != null) {
            final PyType initType = init.getReturnType(context, null);
            if (initType != null) {
              match(initType, qualType, context, substitutions);
            }
          }
          else {
            // Unify generics in stdlib pseudo-constructor
            final PyStdlibTypeProvider stdlib = PyStdlibTypeProvider.getInstance();
            if (stdlib != null) {
              final PyType initType = stdlib.getConstructorType(cls);
              if (initType != null) {
                match(initType, qualType, context, substitutions);
              }
            }
          }
        }
      }
    }
    return substitutions;
  }

  private static boolean matchClasses(@Nullable PyClass superClass, @Nullable PyClass subClass) {
    if (superClass == null || subClass == null || subClass.isSubclass(superClass) || PyABCUtil.isSubclass(subClass, superClass)) {
      return true;
    }
    else {
      final String superName = superClass.getName();
      return superName != null && superName.equals(subClass.getName());
    }
  }
}
