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
      return isSubclass(subClass, superName);
    }
    return false;
  }

  public static boolean isSubclass(@NotNull PyClass subClass, @NotNull String superClassName) {
    final String subClassName = subClass.getName();
    if (PyNames.CALLABLE.equals(superClassName)) {
      return hasMethod(subClass, PyNames.CALL);
    }
    if (PyNames.HASHABLE.equals(superClassName)) {
      return hasMethod(subClass, PyNames.HASH);
    }
    final boolean isIterable = hasMethod(subClass, PyNames.ITER);
    if (PyNames.ITERABLE.equals(superClassName)) {
      return isIterable || isStringClass(subClassName);
    }
    if (PyNames.ITERATOR.equals(superClassName)) {
      return (isIterable && hasMethod(subClass, PyNames.NEXT)) || isStringClass(subClassName);
    }
    final boolean isSized = hasMethod(subClass, PyNames.LEN);
    if (PyNames.SIZED.equals(superClassName)) {
      return isSized;
    }
    final boolean isContainer = hasMethod(subClass, PyNames.CONTAINS);
    if (PyNames.CONTAINER.equals(superClassName)) {
      return isContainer;
    }
    final boolean hasGetItem = hasMethod(subClass, PyNames.GETITEM);
    if (PyNames.SEQUENCE.equals(superClassName)) {
      return isSized && isIterable && isContainer && hasGetItem;
    }
    if (PyNames.MAPPING.equals(superClassName)) {
      return isSized && isIterable && isContainer && hasGetItem && hasMethod(subClass, PyNames.KEYS);
    }
    return false;
  }

  public static boolean isSubtype(@NotNull PyType type, @NotNull String superClassName) {
    if (type instanceof PyClassType) {
      final PyClass pyClass = ((PyClassType)type).getPyClass();
      return pyClass != null && isSubclass(pyClass, superClassName);
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

  private static boolean hasMethod(PyClass cls, String name) {
    return cls.findMethodByName(name, true) != null;
  }

  private static boolean isStringClass(String className) {
    return "bytes".equals(className) || "str".equals(className) || "unicode".equals(className);
  }
}
