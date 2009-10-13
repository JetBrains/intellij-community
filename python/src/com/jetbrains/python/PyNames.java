package com.jetbrains.python;

import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * @author dcheryasov
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
  
  @NonNls private static final Set<String> _Keywords = new HashSet<String>();
  static {
    _Keywords.add("and");
    _Keywords.add("del");
    _Keywords.add("from");
    _Keywords.add("not");
    _Keywords.add("while");
    _Keywords.add("as");
    _Keywords.add("elif");
    _Keywords.add("global");
    _Keywords.add("or");
    _Keywords.add("with");
    _Keywords.add("assert");
    _Keywords.add("else");
    _Keywords.add("if");
    _Keywords.add("pass");
    _Keywords.add("yield");
    _Keywords.add("break");
    _Keywords.add("except");
    _Keywords.add("import");
    _Keywords.add("print");
    _Keywords.add("class");
    _Keywords.add("exec");
    _Keywords.add("in");
    _Keywords.add("raise");
    _Keywords.add("continue");
    _Keywords.add("finally");
    _Keywords.add("is");
    _Keywords.add("return");
    _Keywords.add("def");
    _Keywords.add("for");
    _Keywords.add("lambda");
    _Keywords.add("try");
  }

  /**
   * Contains keywords as of CPython 2.5.
   */
  public static Set<String> Keywords = Collections.unmodifiableSet(_Keywords);

  /**
   * TODO: dependency on language level.
   * @param name what to check
   * @return true iff the name is either a keyword or a reserved name, like None.
   *
   */
  public static boolean isReserved(@NonNls String name) {
    return Keywords.contains(name) || NONE.equals(name) || "as".equals(name) || "with".equals(name);
  }

  // NOTE: includes unicode only good for py3k
  private final static Pattern IDENTIFIER_PATTERN = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

  /**
   * TODO: dependency on language level.
   * @param name what to check
   * @return true iff name is not reserved and is a well-formed identifier.
   */
  public static boolean isIdentifier(@NonNls String name) {
    return ! isReserved(name) && IDENTIFIER_PATTERN.matcher(name).matches(); 
  }


}
