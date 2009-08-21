package com.jetbrains.python;

import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

/**
 * @author yole
 */
public class PyNames {
  private PyNames() {
  }

  @NonNls public static final String INIT = "__init__";
  @NonNls public static final String DOT_PY = ".py";
  @NonNls public static final String INIT_DOT_PY = INIT + DOT_PY;

  @NonNls public static final String NEW = "__new__";

  @NonNls public static final String OBJECT = "object";
  @NonNls public static final String NONE = "None";

  @NonNls public static final String CLASSMETHOD = "classmethod";
  @NonNls public static final String STATICMETHOD = "staticmethod";

  @NonNls private static final Set<String> _UnderscoredNames = new HashSet<String>();
  static {
    _UnderscoredNames.add("__abs__");
    _UnderscoredNames.add("__add__");
    _UnderscoredNames.add("__all__");
    _UnderscoredNames.add("__author__");
    _UnderscoredNames.add("__bases__");
    _UnderscoredNames.add("__builtins__");
    _UnderscoredNames.add("__call__");
    _UnderscoredNames.add("__class__");
    _UnderscoredNames.add("__cmp__");
    _UnderscoredNames.add("__coerce__");
    _UnderscoredNames.add("__contains__");
    _UnderscoredNames.add("__debug__");
    _UnderscoredNames.add("__del__");
    _UnderscoredNames.add("__delattr__");
    _UnderscoredNames.add("__delitem__");
    _UnderscoredNames.add("__delslice__");
    _UnderscoredNames.add("__dict__");
    _UnderscoredNames.add("__div__");
    _UnderscoredNames.add("__divmod__");
    _UnderscoredNames.add("__doc__");
    _UnderscoredNames.add("__docformat__");
    _UnderscoredNames.add("__eq__");
    _UnderscoredNames.add("__file__");
    _UnderscoredNames.add("__float__");
    _UnderscoredNames.add("__floordiv__");
    _UnderscoredNames.add("__future__");
    _UnderscoredNames.add("__ge__");
    _UnderscoredNames.add("__getattr__");
    _UnderscoredNames.add("__getattribute__");
    _UnderscoredNames.add("__getitem__");
    _UnderscoredNames.add("__getslice__");
    _UnderscoredNames.add("__gt__");
    _UnderscoredNames.add("__hash__");
    _UnderscoredNames.add("__hex__");
    _UnderscoredNames.add("__iadd__");
    _UnderscoredNames.add("__import__");
    _UnderscoredNames.add("__imul__");
    _UnderscoredNames.add(INIT);
    _UnderscoredNames.add("__int__");
    _UnderscoredNames.add("__invert__");
    _UnderscoredNames.add("__iter__");
    _UnderscoredNames.add("__le__");
    _UnderscoredNames.add("__len__");
    _UnderscoredNames.add("__long__");
    _UnderscoredNames.add("__lshift__");
    _UnderscoredNames.add("__lt__");
    _UnderscoredNames.add("__members__");
    _UnderscoredNames.add("__metaclass__");
    _UnderscoredNames.add("__mod__");
    _UnderscoredNames.add("__mro__");
    _UnderscoredNames.add("__mul__");
    _UnderscoredNames.add("__name__");
    _UnderscoredNames.add("__ne__");
    _UnderscoredNames.add("__neg__");
    _UnderscoredNames.add(NEW);
    _UnderscoredNames.add("__nonzero__");
    _UnderscoredNames.add("__oct__");
    _UnderscoredNames.add("__or__");
    _UnderscoredNames.add("__path__");
    _UnderscoredNames.add("__pos__");
    _UnderscoredNames.add("__pow__");
    _UnderscoredNames.add("__radd__");
    _UnderscoredNames.add("__rdiv__");
    _UnderscoredNames.add("__rdivmod__");
    _UnderscoredNames.add("__reduce__");
    _UnderscoredNames.add("__repr__");
    _UnderscoredNames.add("__rfloordiv__");
    _UnderscoredNames.add("__rlshift__");
    _UnderscoredNames.add("__rmod__");
    _UnderscoredNames.add("__rmul__");
    _UnderscoredNames.add("__ror__");
    _UnderscoredNames.add("__rpow__");
    _UnderscoredNames.add("__rrshift__");
    _UnderscoredNames.add("__rsub__");
    _UnderscoredNames.add("__rtruediv__");
    _UnderscoredNames.add("__rxor__");
    _UnderscoredNames.add("__setattr__");
    _UnderscoredNames.add("__setitem__");
    _UnderscoredNames.add("__setslice__");
    _UnderscoredNames.add("__self__");
    _UnderscoredNames.add("__slots__");
    _UnderscoredNames.add("__str__");
    _UnderscoredNames.add("__sub__");
    _UnderscoredNames.add("__truediv__");
    _UnderscoredNames.add("__version__");
    _UnderscoredNames.add("__xor__");
  }

  /**
   * Contains all known predefined names of "__foo__" form.
   */
  public static Set<String> UnderscoredNames = Collections.unmodifiableSet(_UnderscoredNames);

  // canonical names, not forced by interpreter
  public static final String CANONICAL_SELF = "self";
}
