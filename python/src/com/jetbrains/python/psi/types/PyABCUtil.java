package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyABCUtil {
  private PyABCUtil() {
  }

  public static boolean isSubclass(@NotNull PyClass subClass, @NotNull PyClass superClass) {
    final String superName = superClass.getName();
    if (superName != null) {
      return isSubclass(subClass, superName, true);
    }
    return false;
  }

  public static boolean isSubclass(@NotNull PyClass subClass, @NotNull String superClassName) {
    return isSubclass(subClass, superClassName, true);
  }

  public static boolean isSubclass(@NotNull PyClass subClass, @NotNull String superClassName, boolean inherited) {
    if (PyNames.CALLABLE.equals(superClassName)) {
      return hasMethod(subClass, PyNames.CALL, inherited);
    }
    if (PyNames.HASHABLE.equals(superClassName)) {
      return hasMethod(subClass, PyNames.HASH, inherited);
    }
    final boolean hasIter = hasMethod(subClass, PyNames.ITER, inherited);
    final boolean hasGetItem = hasMethod(subClass, PyNames.GETITEM, inherited);
    if (PyNames.ITERABLE.equals(superClassName)) {
      return hasIter || hasGetItem;
    }
    if (PyNames.ITERATOR.equals(superClassName)) {
      return (hasIter && (hasMethod(subClass, PyNames.NEXT, inherited) || hasMethod(subClass,
                                                                         PyNames.DUNDER_NEXT, inherited))) || hasGetItem;
    }
    final boolean isSized = hasMethod(subClass, PyNames.LEN, inherited);
    if (PyNames.SIZED.equals(superClassName)) {
      return isSized;
    }
    final boolean isContainer = hasMethod(subClass, PyNames.CONTAINS, inherited);
    if (PyNames.CONTAINER.equals(superClassName)) {
      return isContainer;
    }
    if (PyNames.SEQUENCE.equals(superClassName)) {
      return isSized && hasIter && isContainer && hasGetItem;
    }
    if (PyNames.MAPPING.equals(superClassName)) {
      return isSized && hasIter && isContainer && hasGetItem && hasMethod(subClass, PyNames.KEYS, inherited);
    }
    if (PyNames.COMPLEX.equals(superClassName)) {
      return hasMethod(subClass, "__complex__", inherited);
    }
    if (PyNames.REAL.equals(superClassName)) {
      return hasMethod(subClass, "__float__", inherited);
    }
    if (PyNames.INTEGRAL.equals(superClassName)) {
      return hasMethod(subClass, "__int__", inherited);
    }
    return false;
  }

  public static boolean isSubtype(@NotNull PyType type, @NotNull String superClassName) {
    if (type instanceof PyClassType) {
      final PyClass pyClass = ((PyClassType)type).getPyClass();
      return isSubclass(pyClass, superClassName, true);
    }
    if (type instanceof PyUnionType) {
      final PyUnionType unionType = (PyUnionType)type;
      for (PyType m : unionType.getMembers()) {
        if (m != null) {
          if (!isSubtype(m, superClassName)) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  private static boolean hasMethod(PyClass cls, String name, boolean inherited) {
    return cls.findMethodByName(name, inherited) != null || cls.findClassAttribute(name, inherited) != null;
  }
}
