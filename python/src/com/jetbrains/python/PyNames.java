package com.jetbrains.python;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * @author dcheryasov
 */
@NonNls
public class PyNames {
  private PyNames() {
  }

  public static final String INIT = "__init__";
  public static final String DOT_PY = ".py";
  public static final String INIT_DOT_PY = INIT + DOT_PY;

  public static final String NEW = "__new__";
  public static final String GETATTR = "__getattr__";
  public static final String GETATTRIBUTE = "__getattribute__";
  public static final String CLASS = "__class__";
  public static final String METACLASS = "__metaclass__";

  public static final String SUPER = "super";

  public static final String OBJECT = "object";
  public static final String NONE = "None";
  public static final String TRUE = "True";
  public static final String FALSE = "False";
  public static final String FAKE_OLD_BASE = "___Classobj";

  public static final String FUTURE_MODULE = "__future__";
  public static final String UNICODE_LITERALS = "unicode_literals";

  public static final String CLASSMETHOD = "classmethod";
  public static final String STATICMETHOD = "staticmethod";

  public static final String PROPERTY = "property";

  public static final String ALL = "__all__";
  public static final String SLOTS = "__slots__";
  public static final String DEBUG = "__debug__";

  public static final String ISINSTANCE = "isinstance";
  public static final String ASSERT_IS_INSTANCE = "assertIsInstance";
  public static final String HAS_ATTR = "hasattr";

  public static final String DOCFORMAT = "__docformat__";

  public static final String DIRNAME = "dirname";
  public static final String ABSPATH = "abspath";
  public static final String JOIN = "join";
  public static final String REPLACE = "replace";
  public static final String FILE = "__file__";

  public static final String WARN = "warn";
  public static final String DEPRECATION_WARNING = "DeprecationWarning";
  public static final String PENDING_DEPRECATION_WARNING = "PendingDeprecationWarning";

  public static final String CONTAINER = "Container";
  public static final String HASHABLE = "Hashable";
  public static final String ITERABLE = "Iterable";
  public static final String ITERATOR = "Iterator";
  public static final String SIZED = "Sized";
  public static final String CALLABLE = "Callable";
  public static final String SEQUENCE = "Sequence";
  public static final String MAPPING = "Mapping";

  public static final String CONTAINS = "__contains__";
  public static final String HASH = "__hash__";
  public static final String ITER = "__iter__";
  public static final String NEXT = "next";
  public static final String DUNDER_NEXT = "__next__";
  public static final String LEN = "__len__";
  public static final String CALL = "__call__";
  public static final String GETITEM = "__getitem__";
  public static final String SETITEM = "__setitem__";
  public static final String DELITEM = "__delitem__";
  public static final String POS = "__pos__";
  public static final String NEG = "__neg__";
  public static final String DIV = "__div__";
  public static final String TRUEDIV = "__truediv__";

  public static final String NAME = "__name__";
  public static final String ENTER = "__enter__";

  public static final String CALLABLE_BUILTIN = "callable";
  public static final String NAMEDTUPLE = "namedtuple";
  public static final String COLLECTIONS_PY = "collections.py";

  public static final String SET = "set";

  public static final String KEYS = "keys";

  public static final String PASS = "pass";

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

  public static ImmutableSet<String> COMPARISON_OPERATORS = ImmutableSet.of(
    "__eq__",
    "__ne__",
    "__lt__",
    "__le__",
    "__gt__",
    "__ge__",
    "__cmp__",
    "__contains__"
  );

  public static ImmutableSet<String> SUBSCRIPTION_OPERATORS = ImmutableSet.of(
    GETITEM,
    SETITEM,
    DELITEM
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

  private static final BuiltinDescription _only_self_descr = new BuiltinDescription("(self)");
  private static final BuiltinDescription _self_other_descr = new BuiltinDescription("(self, other)");
  private static final BuiltinDescription _self_item_descr = new BuiltinDescription("(self, item)");
  private static final BuiltinDescription _self_key_descr = new BuiltinDescription("(self, key)");

  public static final ImmutableMap<String, BuiltinDescription> BuiltinMethods = ImmutableMap.<String, BuiltinDescription>builder()
    .put("__abs__", _only_self_descr)
    .put("__add__", _self_other_descr)
    .put("__and__", _self_other_descr)
    //_BuiltinMethods.put("__all__", _only_self_descr);
    //_BuiltinMethods.put("__author__", _only_self_descr);
    //_BuiltinMethods.put("__bases__", _only_self_descr);
    .put("__call__", new BuiltinDescription("(self, *args, **kwargs)"))
    //_BuiltinMethods.put("__class__", _only_self_descr);
    .put("__cmp__", _self_other_descr)
    .put("__coerce__", _self_other_descr)
    .put("__complex__", _only_self_descr)
    .put("__contains__", _self_item_descr)
    //_BuiltinMethods.put("__debug__", _only_self_descr);
    .put("__del__", _only_self_descr)
    .put("__delete__", new BuiltinDescription("(self, instance)"))
    .put("__delattr__", _self_item_descr)
    .put("__delitem__", _self_key_descr)
    .put("__delslice__", new BuiltinDescription("(self, i, j)"))
    //_BuiltinMethods.put("__dict__", _only_self_descr);
    .put("__div__", _self_other_descr)
    .put("__divmod__", _self_other_descr)
    //_BuiltinMethods.put("__doc__", _only_self_descr);
    //_BuiltinMethods.put("__docformat__", _only_self_descr);
    .put("__enter__", _only_self_descr)
    .put("__exit__", new BuiltinDescription("(self, exc_type, exc_val, exc_tb)"))
    .put("__eq__", _self_other_descr)
    //_BuiltinMethods.put("__file__", _only_self_descr);
    .put("__float__", _only_self_descr)
    .put("__floordiv__", _self_other_descr)
    //_BuiltinMethods.put("__future__", _only_self_descr);
    .put("__ge__", _self_other_descr)
    .put("__get__", new BuiltinDescription("(self, instance, owner)"))
    .put("__getattr__", _self_item_descr)
    .put("__getattribute__", _self_item_descr)
    .put("__getitem__", _self_item_descr)
    //_BuiltinMethods.put("__getslice__", new BuiltinDescription("(self, i, j)"));
    .put("__gt__", _self_other_descr)
    .put("__hash__", _only_self_descr)
    .put("__hex__", _only_self_descr)
    .put("__iadd__", _self_other_descr)
    .put("__iand__", _self_other_descr)
    .put("__idiv__", _self_other_descr)
    .put("__ifloordiv__", _self_other_descr)
    //_BuiltinMethods.put("__import__", _only_self_descr);
    .put("__ilshift__", _self_other_descr)
    .put("__imod__", _self_other_descr)
    .put("__imul__", _self_other_descr)
    .put("__index__", _only_self_descr)
    .put(INIT, _only_self_descr)
    .put("__int__", _only_self_descr)
    .put("__invert__", _only_self_descr)
    .put("__ior__", _self_other_descr)
    .put("__ipow__", _self_other_descr)
    .put("__irshift__", _self_other_descr)
    .put("__isub__", _self_other_descr)
    .put("__iter__", _only_self_descr)
    .put("__itruediv__", _self_other_descr)
    .put("__ixor__", _self_other_descr)
    .put("__le__", _self_other_descr)
    .put("__len__", _only_self_descr)
    .put("__long__", _only_self_descr)
    .put("__lshift__", _self_other_descr)
    .put("__lt__", _self_other_descr)
    //_BuiltinMethods.put("__members__", _only_self_descr);
    //_BuiltinMethods.put("__metaclass__", _only_self_descr);
    .put("__mod__", _self_other_descr)
    //_BuiltinMethods.put("__mro__", _only_self_descr);
    .put("__mul__", _self_other_descr)
    //_BuiltinMethods.put("__name__", _only_self_descr);
    .put("__ne__", _self_other_descr)
    .put("__neg__", _only_self_descr)
    .put(NEW, new BuiltinDescription("(cls, *args, **kwargs)"))
    .put("__nonzero__", _only_self_descr)
    .put("__oct__", _only_self_descr)
    .put("__or__", _self_other_descr)
    //_BuiltinMethods.put("__path__", _only_self_descr);
    .put("__pos__", _only_self_descr)
    .put("__pow__", new BuiltinDescription("(self, power, modulo=None)"))
    .put("__radd__", _self_other_descr)
    .put("__rand__", _self_other_descr)
    .put("__rdiv__", _self_other_descr)
    .put("__rdivmod__", _self_other_descr)
    .put("__reduce__", _only_self_descr)
    .put("__repr__", _only_self_descr)
    .put("__rfloordiv__", _self_other_descr)
    .put("__rlshift__", _self_other_descr)
    .put("__rmod__", _self_other_descr)
    .put("__rmul__", _self_other_descr)
    .put("__ror__", _self_other_descr)
    .put("__rpow__", new BuiltinDescription("(self, power, modulo=None)"))
    .put("__rrshift__", _self_other_descr)
    .put("__rshift__", _self_other_descr)
    .put("__rsub__", _self_other_descr)
    .put("__rtruediv__", _self_other_descr)
    .put("__rxor__", _self_other_descr)
    .put("__set__", new BuiltinDescription("(self, instance, value)"))
    .put("__setattr__", new BuiltinDescription("(self, key, value)"))
    .put("__setitem__", new BuiltinDescription("(self, key, value)"))
    .put("__setslice__", new BuiltinDescription("(self, i, j, sequence)"))
    //_BuiltinMethods.put("__self__", _only_self_descr);
    //_BuiltinMethods.put("__slots__", _only_self_descr);
    .put("__str__", _only_self_descr)
    .put("__sub__", _self_other_descr)
    .put("__truediv__", _self_other_descr)
    .put("__unicode__", _only_self_descr)
    //_BuiltinMethods.put("__version__", _only_self_descr);
    .put("__xor__", _self_other_descr)
    .build();

  // canonical names, not forced by interpreter
  public static final String CANONICAL_SELF = "self";
  public static final String BASESTRING = "basestring";

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

  public static boolean isRightOperatorName(@Nullable String name) {
    return name != null && name.matches("__r[a-z]+__");
  }
}
