package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyClassRef;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyABCUtil {
  public static boolean isSubclass(@NotNull PyClass subClass, @NotNull PyClass superClass) {
    final String superName = superClass.getName();
    if (superName != null) {
      return isSubclass(subClass, superName);
    }
    return false;
  }

  public static boolean isSubclass(@NotNull PyClass subClass, @NotNull String superClassName) {
    if (PyNames.CALLABLE.equals(superClassName)) {
      return hasMethod(subClass, PyNames.CALL);
    }
    if (PyNames.HASHABLE.equals(superClassName)) {
      return hasMethod(subClass, PyNames.HASH);
    }
    final boolean isIterable = hasMethod(subClass, PyNames.ITER);
    if (PyNames.ITERABLE.equals(superClassName)) {
      return isIterable;
    }
    if (PyNames.ITERATOR.equals(superClassName)) {
      return isIterable && hasMethod(subClass, PyNames.NEXT);
    }
    final boolean isSized = hasMethod(subClass, PyNames.LEN);
    if (PyNames.SIZED.equals(superClassName)) {
      return isSized;
    }
    final boolean isContainer = hasMethod(subClass, PyNames.CONTAINS);
    if (PyNames.CONTAINER.equals(superClassName)) {
      return isContainer;
    }
    if (PyNames.SEQUENCE.equals(superClassName)) {
      return isSized && isIterable && isContainer && hasMethod(subClass, PyNames.GETITEM);
    }
    return false;

  }

  private static boolean hasMethod(PyClass cls, String name) {
    return cls.findMethodByName(name, true) != null;
  }
}
