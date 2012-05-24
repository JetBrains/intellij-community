package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
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
    if (PyNames.CALLABLE.equals(superClassName)) {
      return hasNonAbstractMethod(subClass, PyNames.CALL);
    }
    if (PyNames.HASHABLE.equals(superClassName)) {
      return hasNonAbstractMethod(subClass, PyNames.HASH);
    }
    final boolean hasIter = hasNonAbstractMethod(subClass, PyNames.ITER);
    final boolean hasGetItem = hasNonAbstractMethod(subClass, PyNames.GETITEM);
    if (PyNames.ITERABLE.equals(superClassName)) {
      return hasIter || hasGetItem;
    }
    if (PyNames.ITERATOR.equals(superClassName)) {
      return (hasIter && (hasNonAbstractMethod(subClass, PyNames.NEXT) || hasNonAbstractMethod(subClass,
                                                                                               PyNames.DUNDER_NEXT))) || hasGetItem;
    }
    final boolean isSized = hasNonAbstractMethod(subClass, PyNames.LEN);
    if (PyNames.SIZED.equals(superClassName)) {
      return isSized;
    }
    final boolean isContainer = hasNonAbstractMethod(subClass, PyNames.CONTAINS);
    if (PyNames.CONTAINER.equals(superClassName)) {
      return isContainer;
    }
    if (PyNames.SEQUENCE.equals(superClassName)) {
      return isSized && hasIter && isContainer && hasGetItem;
    }
    if (PyNames.MAPPING.equals(superClassName)) {
      return isSized && hasIter && isContainer && hasGetItem && hasNonAbstractMethod(subClass, PyNames.KEYS);
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

  private static boolean hasNonAbstractMethod(PyClass cls, String name) {
    final PyFunction method = cls.findMethodByName(name, true);
    if (method == null) return false;
    final PyDecoratorList decoratorList = method.getDecoratorList();
    if (decoratorList != null) {
      for (PyDecorator decorator : decoratorList.getDecorators()) {
        if (PyNames.ABSTRACTMETHOD.equals(decorator.getName())) return false;
      }
    }
    return true;
  }
}
