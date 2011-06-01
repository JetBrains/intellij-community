package com.jetbrains.python;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
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
  @NonNls public static final String GETATTR = "__getattr__";
  @NonNls public static final String GETATTRIBUTE = "__getattribute__";
  @NonNls public static final String CLASS = "__class__";
  @NonNls public static final String METACLASS = "__metaclass__";

  @NonNls public static final String SUPER = "super";

  @NonNls public static final String OBJECT = "object";
  @NonNls public static final String NONE = "None";
  @NonNls public static final String TRUE = "True";
  @NonNls public static final String FALSE = "False";
  @NonNls public static final String FAKE_OLD_BASE = "___Classobj";

  @NonNls public static final String FUTURE_MODULE = "__future__";

  @NonNls public static final String CLASSMETHOD = "classmethod";
  @NonNls public static final String STATICMETHOD = "staticmethod";

  @NonNls public static final String PROPERTY = "property";

  @NonNls public static final String ALL = "__all__";
  @NonNls public static final String SLOTS = "__slots__";
  @NonNls public static final String DEBUG = "__debug__";

  @NonNls public static final String ISINSTANCE = "isinstance";

  @NonNls public static final String DOCFORMAT = "__docformat__";

  @NonNls public static final String DIRNAME = "dirname";
  @NonNls public static final String JOIN = "join";
  @NonNls public static final String REPLACE = "replace";
  @NonNls public static final String FILE = "__file__";

  /**
   * Contains all known predefined names of "__foo__" form.
   */
  public static ImmutableSet<String> UnderscoredAttributes = ImmutableSet.of(
    "__all__",
    "__author__",
    "__bases__",
    "__dict__",
    "__doc__",
    "__docformat__",
    "__file__",
    "__members__",
    "__metaclass__",
    "__mod__",
    "__mro__",
    "__name__",
    "__path__",
    "__self__",
    "__slots__",
    "__version__"
  );

  public static class BuiltinDescription {
    private final String mySignature;

    public BuiltinDescription(String signature) {
      mySignature = signature;
    }

    public String getSignature() {
      return mySignature;
    }

    // TODO: doc string, too
  }

  private static final Map<String, BuiltinDescription> _BuiltinMethods = new TreeMap<String, BuiltinDescription>();


  static {
    final BuiltinDescription _only_self_descr = new BuiltinDescription("(self)");
    final BuiltinDescription _self_other_descr = new BuiltinDescription("(self, other)");
    final BuiltinDescription _self_item_descr = new BuiltinDescription("(self, item)");
    final BuiltinDescription _self_key_descr = new BuiltinDescription("(self, key)");

    _BuiltinMethods.put("__abs__", _only_self_descr);
    _BuiltinMethods.put("__add__", _self_other_descr);
    _BuiltinMethods.put("__and__", _self_other_descr);
    //_BuiltinMethods.put("__all__", _only_self_descr);
    //_BuiltinMethods.put("__author__", _only_self_descr);
    //_BuiltinMethods.put("__bases__", _only_self_descr);
    _BuiltinMethods.put("__call__", new BuiltinDescription("(self, *args, **kwargs)"));
    //_BuiltinMethods.put("__class__", _only_self_descr);
    _BuiltinMethods.put("__cmp__", _self_other_descr);
    _BuiltinMethods.put("__coerce__", _self_other_descr);
    _BuiltinMethods.put("__contains__", _self_item_descr);
    //_BuiltinMethods.put("__debug__", _only_self_descr);
    _BuiltinMethods.put("__del__", _only_self_descr);
    _BuiltinMethods.put("__delattr__", _self_item_descr);
    _BuiltinMethods.put("__delitem__", _self_key_descr);
    _BuiltinMethods.put("__delslice__", new BuiltinDescription("(self, i, j)"));
    //_BuiltinMethods.put("__dict__", _only_self_descr);
    _BuiltinMethods.put("__div__", _self_other_descr);
    _BuiltinMethods.put("__divmod__", _self_other_descr);
    //_BuiltinMethods.put("__doc__", _only_self_descr);
    //_BuiltinMethods.put("__docformat__", _only_self_descr);
    _BuiltinMethods.put("__eq__", _self_other_descr);
    //_BuiltinMethods.put("__file__", _only_self_descr);
    _BuiltinMethods.put("__float__", _only_self_descr);
    _BuiltinMethods.put("__floordiv__", _self_other_descr);
    //_BuiltinMethods.put("__future__", _only_self_descr);
    _BuiltinMethods.put("__ge__", _self_other_descr);
    _BuiltinMethods.put("__getattr__", _self_item_descr);
    _BuiltinMethods.put("__getattribute__", _self_item_descr);
    _BuiltinMethods.put("__getitem__", _self_item_descr);
    //_BuiltinMethods.put("__getslice__", new BuiltinDescription("(self, i, j)"));
    _BuiltinMethods.put("__gt__", _self_other_descr);
    _BuiltinMethods.put("__hash__", _only_self_descr);
    _BuiltinMethods.put("__hex__", _only_self_descr);
    _BuiltinMethods.put("__iadd__", _self_other_descr);
    //_BuiltinMethods.put("__import__", _only_self_descr);
    _BuiltinMethods.put("__imul__", _self_other_descr);
    _BuiltinMethods.put(INIT, _only_self_descr);
    _BuiltinMethods.put("__int__", _only_self_descr);
    _BuiltinMethods.put("__invert__", _only_self_descr);
    _BuiltinMethods.put("__iter__", _only_self_descr);
    _BuiltinMethods.put("__le__", _self_other_descr);
    _BuiltinMethods.put("__len__", _only_self_descr);
    _BuiltinMethods.put("__long__", _only_self_descr);
    _BuiltinMethods.put("__lshift__", _self_other_descr);
    _BuiltinMethods.put("__lt__", _self_other_descr);
    //_BuiltinMethods.put("__members__", _only_self_descr);
    //_BuiltinMethods.put("__metaclass__", _only_self_descr);
    _BuiltinMethods.put("__mod__", _self_other_descr);
    //_BuiltinMethods.put("__mro__", _only_self_descr);
    _BuiltinMethods.put("__mul__", _self_other_descr);
    //_BuiltinMethods.put("__name__", _only_self_descr);
    _BuiltinMethods.put("__ne__", _self_other_descr);
    _BuiltinMethods.put("__neg__", _only_self_descr);
    _BuiltinMethods.put(NEW, new BuiltinDescription("(cls, *args, **kwargs)"));
    _BuiltinMethods.put("__nonzero__", _only_self_descr);
    _BuiltinMethods.put("__oct__", _only_self_descr);
    _BuiltinMethods.put("__or__", _self_other_descr);
    //_BuiltinMethods.put("__path__", _only_self_descr);
    _BuiltinMethods.put("__pos__", _only_self_descr);
    _BuiltinMethods.put("__pow__", new BuiltinDescription("(self, power, modulo=None)"));
    _BuiltinMethods.put("__radd__", _self_other_descr);
    _BuiltinMethods.put("__rand__", _self_other_descr);
    _BuiltinMethods.put("__rdiv__", _self_other_descr);
    _BuiltinMethods.put("__rdivmod__", _self_other_descr);
    _BuiltinMethods.put("__reduce__", _only_self_descr);
    _BuiltinMethods.put("__repr__", _only_self_descr);
    _BuiltinMethods.put("__rfloordiv__", _self_other_descr);
    _BuiltinMethods.put("__rlshift__", _self_other_descr);
    _BuiltinMethods.put("__rmod__", _self_other_descr);
    _BuiltinMethods.put("__rmul__", _self_other_descr);
    _BuiltinMethods.put("__ror__", _self_other_descr);
    _BuiltinMethods.put("__rpow__", new BuiltinDescription("(self, power, modulo=None)"));
    _BuiltinMethods.put("__rrshift__", _self_other_descr);
    _BuiltinMethods.put("__rsub__", _self_other_descr);
    _BuiltinMethods.put("__rtruediv__", _self_other_descr);
    _BuiltinMethods.put("__rxor__", _self_other_descr);
    _BuiltinMethods.put("__setattr__", new BuiltinDescription("(self, key, value)"));
    _BuiltinMethods.put("__setitem__", new BuiltinDescription("(self, key, value)"));
    _BuiltinMethods.put("__setslice__", new BuiltinDescription("(self, i, j, sequence)"));
    //_BuiltinMethods.put("__self__", _only_self_descr);
    //_BuiltinMethods.put("__slots__", _only_self_descr);
    _BuiltinMethods.put("__str__", _only_self_descr);
    _BuiltinMethods.put("__sub__", _self_other_descr);
    _BuiltinMethods.put("__truediv__", _self_other_descr);
    _BuiltinMethods.put("__unicode__", _only_self_descr);
    //_BuiltinMethods.put("__version__", _only_self_descr);
    _BuiltinMethods.put("__xor__", _self_other_descr);
  }

  public static final Map<String, BuiltinDescription> BuiltinMethods = Collections.unmodifiableMap(_BuiltinMethods);

  // canonical names, not forced by interpreter
  public static final String CANONICAL_SELF = "self";
  
  /**
   * Contains keywords as of CPython 2.5.
   */
  public static ImmutableSet<String> Keywords = ImmutableSet.of(
    "and",
    "del",
    "from",
    "not",
    "while",
    "as",
    "elif",
    "global",
    "or",
    "with",
    "assert",
    "else",
    "if",
    "pass",
    "yield",
    "break",
    "except",
    "import",
    "print",
    "class",
    "exec",
    "in",
    "raise",
    "continue",
    "finally",
    "is",
    "return",
    "def",
    "for",
    "lambda",
    "try"
  );

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
  public static boolean isIdentifier(@NotNull @NonNls String name) {
    return ! isReserved(name) && IDENTIFIER_PATTERN.matcher(name).matches(); 
  }


}
