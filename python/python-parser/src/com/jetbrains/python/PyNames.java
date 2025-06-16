// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.util.NlsSafe;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public final @NonNls class PyNames {
  public static final String SITE_PACKAGES = "site-packages";
  public static final String DIST_PACKAGES = "dist-packages";
  /**
   * int type
   */
  public static final String TYPE_INT = "int";
  public static final String TYPE_LONG = "long";
  /**
   * unicode string type (see {@link #TYPE_STRING_TYPES}
   */
  public static final String TYPE_UNICODE = "unicode";
  /**
   * string type (see {@link #TYPE_STRING_TYPES}
   */
  public static final String TYPE_STR = "str";
  /**
   * Any string type
   */
  public static final List<String> TYPE_STRING_TYPES = List.of(TYPE_UNICODE, TYPE_STR);
  /**
   * date type
   */
  public static final String TYPE_DATE = "datetime.date";
  /**
   * datetime type
   */
  public static final String TYPE_DATE_TIME = "datetime.datetime";
  /**
   * time type
   */
  public static final String TYPE_TIME = "datetime.time";

  public static final String TYPE_BYTES = "bytes";

  public static final String TYPE_BYTEARRAY = "bytearray";

  public static final String TYPE_ENUM = "enum.Enum";
  public static final String TYPE_ENUM_META = "enum.EnumMeta";
  public static final String TYPE_ENUM_FLAG = "enum.Flag";
  public static final String TYPE_ENUM_AUTO = "enum.auto";
  public static final String TYPE_ENUM_MEMBER = "enum.member";
  public static final String TYPE_ENUM_NONMEMBER = "enum.nonmember";

  public static final String TYPE_NONE = "_typeshed.NoneType";
  public static final Set<String> TYPE_NONE_NAMES = Set.of("types.NoneType", TYPE_NONE);

  public static final Set<String> BUILTINS_MODULES = Set.of("builtins.py", "__builtin__.py");
  public static final String PYTHON_SDK_ID_NAME = "Python SDK";
  public static final String VERBOSE_REG_EXP_LANGUAGE_ID = "PythonVerboseRegExp";
  public static final @NonNls String PYTHON_MODULE_ID = "PYTHON_MODULE";
  public static final String TESTCASE_SETUP_NAME = "setUp";
  public static final String PY_DOCSTRING_ID = "Doctest";
  public static final String END_WILDCARD = ".*";

  private PyNames() {
  }

  public static final String INIT = "__init__";
  public static final String DUNDER_DICT = "__dict__";
  public static final String DOT_PY = ".py";
  public static final String DOT_PYI = ".pyi";
  public static final String INIT_DOT_PY = INIT + DOT_PY;
  public static final String INIT_DOT_PYI = INIT + DOT_PYI;

  public static final String SETUP_DOT_PY = "setup" + DOT_PY;

  public static final String NEW = "__new__";
  public static final String GETATTR = "__getattr__";
  public static final String GETATTRIBUTE = "__getattribute__";
  public static final String DUNDER_GET = "__get__";
  public static final String DUNDER_SET = "__set__";
  public static final String __CLASS__ = "__class__";
  public static final String DUNDER_METACLASS = "__metaclass__";
  public static final @NlsSafe String METACLASS = "metaclass";
  public static final String TYPE = "type";

  public static final String SUPER = "super";

  public static final String OBJECT = "object";
  public static final String NONE = "None";
  public static final String TRUE = "True";
  public static final String FALSE = "False";
  public static final String ELLIPSIS = "...";
  public static final String FUNCTION = "function";

  public static final String TYPES_FUNCTION_TYPE = "types.FunctionType";
  public static final String TYPES_METHOD_TYPE = "types.UnboundMethodType";

  public static final String FUTURE_MODULE = "__future__";
  public static final String UNICODE_LITERALS = "unicode_literals";

  public static final String TEMPLATELIB_TEMPLATE = "string.templatelib.Template";

  public static final String CLASSMETHOD = "classmethod";
  public static final String STATICMETHOD = "staticmethod";
  public static final String OVERLOAD = "overload";

  public static final String OVERRIDE = "override";

  public static final String PROPERTY = "property";
  public static final String SETTER = "setter";
  public static final String DELETER = "deleter";
  public static final String GETTER = "getter";
  public static final String CACHED_PROPERTY = "cached_property";

  public static final String ALL = "__all__";
  public static final String SLOTS = "__slots__";
  public static final String DEBUG = "__debug__";

  public static final String ISINSTANCE = "isinstance";
  public static final String ASSERT_IS_INSTANCE = "assertIsInstance";
  public static final String HAS_ATTR = "hasattr";
  public static final String ISSUBCLASS = "issubclass";

  public static final String DOC = "__doc__";
  public static final String DOCFORMAT = "__docformat__";

  public static final String DIRNAME = "dirname";
  public static final String ABSPATH = "abspath";
  public static final String NORMPATH = "normpath";
  public static final String REALPATH = "realpath";
  public static final String JOIN = "join";
  public static final String REPLACE = "replace";
  public static final String FILE = "__file__";
  public static final String PARDIR = "pardir";
  public static final String CURDIR = "curdir";

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
  public static final String MUTABLE_MAPPING = "MutableMapping";
  public static final String ABC_SET = "Set";
  public static final String ABC_MUTABLE_SET = "MutableSet";

  public static final String AWAITABLE = "Awaitable";
  public static final String ASYNC_ITERABLE = "AsyncIterable";

  public static final String ABC_NUMBER = "Number";
  public static final String ABC_COMPLEX = "Complex";
  public static final String ABC_REAL = "Real";
  public static final String ABC_RATIONAL = "Rational";
  public static final String ABC_INTEGRAL = "Integral";

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
  public static final String AITER = "__aiter__";
  public static final String ANEXT = "__anext__";
  public static final String AENTER = "__aenter__";
  public static final String AEXIT = "__aexit__";
  public static final String DUNDER_AWAIT = "__await__";
  public static final String SIZEOF = "__sizeof__";
  public static final String INIT_SUBCLASS = "__init_subclass__";
  public static final String COMPLEX = "__complex__";
  public static final String FLOAT = "__float__";
  public static final String INT = "__int__";
  public static final String BYTES = "__bytes__";
  public static final String ABS = "__abs__";
  public static final String ROUND = "__round__";
  public static final String CLASS_GETITEM = "__class_getitem__";
  public static final String PREPARE = "__prepare__";
  public static final String MATCH_ARGS = "__match_args__";

  public static final String NAME = "__name__";
  public static final String ENTER = "__enter__";
  public static final String EXIT = "__exit__";

  public static final String CALLABLE_BUILTIN = "callable";
  public static final String NAMEDTUPLE = "namedtuple";
  public static final String COLLECTIONS = "collections";
  public static final String COLLECTIONS_NAMEDTUPLE_PY2 = COLLECTIONS + "." + NAMEDTUPLE;
  public static final String COLLECTIONS_NAMEDTUPLE_PY3 = COLLECTIONS + "." + INIT + "." + NAMEDTUPLE;

  public static final String FORMAT = "format";

  public static final String ABSTRACTMETHOD = "abstractmethod";
  public static final String ABSTRACTPROPERTY = "abstractproperty";
  public static final String ABC_META_CLASS = "ABCMeta";
  public static final String ABC = "abc.ABC";
  public static final String ABC_META = "abc.ABCMeta";

  public static final String TUPLE = "tuple";
  public static final String SET = "set";
  public static final String SLICE = "slice";
  public static final String DICT = "dict";

  public static final String KEYS = "keys";
  public static final String APPEND = "append";
  public static final String EXTEND = "extend";
  public static final String UPDATE = "update";
  public static final String CLEAR = "clear";
  public static final String POP = "pop";
  public static final String POPITEM = "popitem";
  public static final String SETDEFAULT = "setdefault";

  public static final String PASS = "pass";

  public static final String TEST_CASE = "TestCase";

  public static final String PYCACHE = "__pycache__";

  public static final String NOT_IMPLEMENTED_ERROR = "NotImplementedError";

  public static final @NlsSafe String UNKNOWN_TYPE = "Any";

  public static final @NlsSafe String UNNAMED_ELEMENT = "<unnamed>";

  public static final String UNDERSCORE = "_";

  /**
   * Contains all known predefined names of "__foo__" form.
   */
  public static final Set<String> UNDERSCORED_ATTRIBUTES = Set.of(
    "__all__",
    "__annotations__",
    "__author__",
    "__bases__",
    "__closure__",
    "__code__",
    "__defaults__",
    "__dict__",
    "__dir__",
    "__doc__",
    "__docformat__",
    "__file__",
    "__func__",
    "__globals__",
    "__kwdefaults__",
    "__members__",
    "__metaclass__",
    "__mod__",
    "__module__",
    "__mro__",
    "__name__",
    "__path__",
    "__qualname__",
    "__self__",
    "__slots__",
    "__version__"
  );

  public static final Set<String> COMPARISON_OPERATORS = Set.of(
    "__eq__",
    "__ne__",
    "__lt__",
    "__le__",
    "__gt__",
    "__ge__",
    "__cmp__",
    "__contains__"
  );

  public static final Set<String> SUBSCRIPTION_OPERATORS = Set.of(
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
  private static final BuiltinDescription _exit_descr = new BuiltinDescription("(self, exc_type, exc_val, exc_tb)");

  @SuppressWarnings("JavacQuirks")
  private static final Map<String, BuiltinDescription> BuiltinMethods = Map.ofEntries(
    Map.entry(ABS, _only_self_descr),
    Map.entry("__add__", _self_other_descr),
    Map.entry("__and__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__all__", _only_self_descr);
      //_BuiltinMethodsMap.entry("__author__", _only_self_descr);
      //_BuiltinMethodsMap.entry("__bases__", _only_self_descr);
    Map.entry("__call__", new BuiltinDescription("(self, *args, **kwargs)")),
    Map.entry("__ceil__", _only_self_descr),
      //_BuiltinMethodsMap.entry("__class__", _only_self_descr);
    Map.entry("__cmp__", _self_other_descr),
    Map.entry("__coerce__", _self_other_descr),
    Map.entry(COMPLEX, _only_self_descr),
    Map.entry("__contains__", _self_item_descr),
    Map.entry("__copy__", _only_self_descr),
      //_BuiltinMethodsMap.entry("__debug__", _only_self_descr);
    Map.entry("__deepcopy__", new BuiltinDescription("(self, memo)")),
    Map.entry("__del__", _only_self_descr),
    Map.entry("__delete__", new BuiltinDescription("(self, instance)")),
    Map.entry("__delattr__", _self_item_descr),
    Map.entry("__delitem__", _self_key_descr),
    Map.entry("__delslice__", new BuiltinDescription("(self, i, j)")),
      //_BuiltinMethodsMap.entry("__dict__", _only_self_descr);
    Map.entry("__divmod__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__doc__", _only_self_descr);
      //_BuiltinMethodsMap.entry("__docformat__", _only_self_descr);
    Map.entry("__enter__", _only_self_descr),
    Map.entry("__exit__", _exit_descr),
    Map.entry("__eq__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__file__", _only_self_descr);
    Map.entry(FLOAT, _only_self_descr),
    Map.entry("__floor__", _only_self_descr),
    Map.entry("__floordiv__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__future__", _only_self_descr);
    Map.entry("__ge__", _self_other_descr),
    Map.entry("__get__", new BuiltinDescription("(self, instance, owner)")),
    Map.entry("__getattr__", _self_item_descr),
    Map.entry("__getattribute__", _self_item_descr),
    Map.entry("__getinitargs__", _only_self_descr),
    Map.entry("__getitem__", _self_item_descr),
    Map.entry("__getnewargs__", _only_self_descr),
      //_BuiltinMethodsMap.entry("__getslice__", new BuiltinDescription("(self, i, j)"));
    Map.entry("__getstate__", _only_self_descr),
    Map.entry("__gt__", _self_other_descr),
    Map.entry("__hash__", _only_self_descr),
    Map.entry("__hex__", _only_self_descr),
    Map.entry("__iadd__", _self_other_descr),
    Map.entry("__iand__", _self_other_descr),
    Map.entry("__idiv__", _self_other_descr),
    Map.entry("__ifloordiv__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__import__", _only_self_descr);
    Map.entry("__ilshift__", _self_other_descr),
    Map.entry("__imod__", _self_other_descr),
    Map.entry("__imul__", _self_other_descr),
    Map.entry("__index__", _only_self_descr),
    Map.entry(INIT, _only_self_descr),
    Map.entry(INT, _only_self_descr),
    Map.entry("__invert__", _only_self_descr),
    Map.entry("__ior__", _self_other_descr),
    Map.entry("__ipow__", _self_other_descr),
    Map.entry("__irshift__", _self_other_descr),
    Map.entry("__isub__", _self_other_descr),
    Map.entry("__iter__", _only_self_descr),
    Map.entry("__itruediv__", _self_other_descr),
    Map.entry("__ixor__", _self_other_descr),
    Map.entry("__le__", _self_other_descr),
    Map.entry("__len__", _only_self_descr),
    Map.entry("__long__", _only_self_descr),
    Map.entry("__lshift__", _self_other_descr),
    Map.entry("__lt__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__members__", _only_self_descr);
      //_BuiltinMethodsMap.entry("__metaclass__", _only_self_descr);
    Map.entry("__missing__", _self_key_descr),
    Map.entry("__mod__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__mro__", _only_self_descr);
    Map.entry("__mul__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__name__", _only_self_descr);
    Map.entry("__ne__", _self_other_descr),
    Map.entry("__neg__", _only_self_descr),
    Map.entry(NEW, new BuiltinDescription("(cls, *args, **kwargs)")),
    Map.entry("__oct__", _only_self_descr),
    Map.entry("__or__", _self_other_descr),
      //_BuiltinMethodsMap.entry("__path__", _only_self_descr);
    Map.entry("__pos__", _only_self_descr),
    Map.entry("__pow__", new BuiltinDescription("(self, power, modulo=None)")),
    Map.entry("__radd__", _self_other_descr),
    Map.entry("__rand__", _self_other_descr),
    Map.entry("__rdiv__", _self_other_descr),
    Map.entry("__rdivmod__", _self_other_descr),
    Map.entry("__reduce__", _only_self_descr),
    Map.entry("__reduce_ex__", new BuiltinDescription("(self, protocol)")),
    Map.entry("__repr__", _only_self_descr),
    Map.entry("__reversed__", _only_self_descr),
    Map.entry("__rfloordiv__", _self_other_descr),
    Map.entry("__rlshift__", _self_other_descr),
    Map.entry("__rmod__", _self_other_descr),
    Map.entry("__rmul__", _self_other_descr),
    Map.entry("__ror__", _self_other_descr),
    Map.entry("__rpow__", _self_other_descr),
    Map.entry("__rrshift__", _self_other_descr),
    Map.entry("__rshift__", _self_other_descr),
    Map.entry("__rsub__", _self_other_descr),
    Map.entry("__rtruediv__", _self_other_descr),
    Map.entry("__rxor__", _self_other_descr),
    Map.entry("__set__", new BuiltinDescription("(self, instance, value)")),
    Map.entry("__setattr__", new BuiltinDescription("(self, key, value)")),
    Map.entry("__setitem__", new BuiltinDescription("(self, key, value)")),
    Map.entry("__setslice__", new BuiltinDescription("(self, i, j, sequence)")),
    Map.entry("__setstate__", new BuiltinDescription("(self, state)")),
    Map.entry(SIZEOF, _only_self_descr),
      //_BuiltinMethodsMap.entry("__self__", _only_self_descr);
      //_BuiltinMethodsMap.entry("__slots__", _only_self_descr);
    Map.entry("__str__", _only_self_descr),
    Map.entry("__sub__", _self_other_descr),
    Map.entry("__truediv__", _self_other_descr),
    Map.entry("__trunc__", _only_self_descr),
    Map.entry("__unicode__", _only_self_descr),
      //_BuiltinMethodsMap.entry("__version__", _only_self_descr);
    Map.entry("__xor__", _self_other_descr));

  private static final Map<String, BuiltinDescription> PY2_BUILTIN_METHODS = concat(
    BuiltinMethods,
    Map.entry("__nonzero__", _only_self_descr),
    Map.entry("__div__", _self_other_descr),
    Map.entry(NEXT, _only_self_descr));

  private static final Map<String, BuiltinDescription> PY3_BUILTIN_METHODS = concat(
    BuiltinMethods,
    Map.entry("__bool__", _only_self_descr),
    Map.entry(BYTES, _only_self_descr),
    Map.entry("__format__", new BuiltinDescription("(self, format_spec)")),
    Map.entry("__instancecheck__", new BuiltinDescription("(self, instance)")),
    Map.entry(PREPARE, new BuiltinDescription("(metacls, name, bases)")),
    Map.entry(ROUND, new BuiltinDescription("(self, n=None)")),
    Map.entry("__subclasscheck__", new BuiltinDescription("(self, subclass)")),
    Map.entry(DUNDER_NEXT, _only_self_descr));

  private static final Map<String, BuiltinDescription> PY35_BUILTIN_METHODS = concat(
    PY3_BUILTIN_METHODS,
    Map.entry("__imatmul__", _self_other_descr),
    Map.entry("__matmul__", _self_other_descr),
    Map.entry("__rmatmul__", _self_other_descr),
    Map.entry(DUNDER_AWAIT, _only_self_descr),
    Map.entry(AENTER, _only_self_descr),
    Map.entry(AEXIT, _exit_descr),
    Map.entry(AITER, _only_self_descr),
    Map.entry(ANEXT, _only_self_descr));

  /**
   * @deprecated use {@link #getBuiltinMethods(LanguageLevel)} instead
   */
  @ApiStatus.Internal
  @Deprecated
  public static final Map<String, BuiltinDescription> PY36_BUILTIN_METHODS = concat(
    PY35_BUILTIN_METHODS,
    Map.entry(INIT_SUBCLASS, new BuiltinDescription("(cls, **kwargs)")),
    Map.entry("__set_name__", new BuiltinDescription("(self, owner, name)")),
    Map.entry("__fspath__", _only_self_descr));

  private static final Map<String, BuiltinDescription> PY37_BUILTIN_METHODS = concat(
    PY36_BUILTIN_METHODS,
    Map.entry(CLASS_GETITEM, new BuiltinDescription("(cls, item)")),
    Map.entry("__mro_entries__", new BuiltinDescription("(self, bases)")));

  private static final @NotNull Map<String, BuiltinDescription> PY37_MODULE_BUILTIN_METHODS = Map.of(
    "__getattr__", new BuiltinDescription("(name)"),
    "__dir__", new BuiltinDescription("()"));

  @SafeVarargs
  private static <K,V> Map<K,V> concat(Map<? extends K, ? extends V> map, Map.Entry<K,V>... additional) {
    Map<K, V> r = new HashMap<>(map);
    r.putAll(Map.ofEntries(additional));
    return Map.copyOf(r);
  }

  public static @NotNull Map<String, BuiltinDescription> getBuiltinMethods(@NotNull LanguageLevel level) {
    if (level.isAtLeast(LanguageLevel.PYTHON37)) {
      return PY37_BUILTIN_METHODS;
    }
    else if (level.isAtLeast(LanguageLevel.PYTHON36)) {
      return PY36_BUILTIN_METHODS;
    }
    else if (level.isAtLeast(LanguageLevel.PYTHON35)) {
      return PY35_BUILTIN_METHODS;
    }
    else if (!level.isPython2()) {
      return PY3_BUILTIN_METHODS;
    }
    else {
      return PY2_BUILTIN_METHODS;
    }
  }

  public static @NotNull Map<String, BuiltinDescription> getModuleBuiltinMethods(@NotNull LanguageLevel level) {
    if (level.isAtLeast(LanguageLevel.PYTHON37)) {
      return PY37_MODULE_BUILTIN_METHODS;
    }

    return Collections.emptyMap();
  }

  // canonical names, not forced by interpreter
  public static final String CANONICAL_SELF = "self";
  public static final String CANONICAL_CLS = "cls";
  public static final String BASESTRING = "basestring";

  /*
    Python keywords
   */

  public static final String CLASS = "class";
  public static final String DEF = "def";
  public static final String IF = "if";
  public static final String ELSE = "else";
  public static final String ELIF = "elif";
  public static final String TRY = "try";
  public static final String EXCEPT = "except";
  public static final String FINALLY = "finally";
  public static final String WHILE = "while";
  public static final String FOR = "for";
  public static final String WITH = "with";
  public static final String AS = "as";
  public static final String ASSERT = "assert";
  public static final String DEL = "del";
  public static final String EXEC = "exec";
  public static final String FROM = "from";
  public static final String IMPORT = "import";
  public static final String RAISE = "raise";
  public static final String PRINT = "print";
  public static final String BREAK = "break";
  public static final String CONTINUE = "continue";
  public static final String GLOBAL = "global";
  public static final String RETURN = "return";
  public static final String YIELD = "yield";
  public static final String NONLOCAL = "nonlocal";
  public static final String AND = "and";
  public static final String OR = "or";
  public static final String IS = "is";
  public static final String IN = "in";
  public static final String NOT = "not";
  public static final String LAMBDA = "lambda";
  public static final String ASYNC = "async";
  public static final String AWAIT = "await";
  public static final String MATCH = "match";
  public static final String CASE = "case";

  /**
   * Contains keywords as of CPython 2.5.
   */
  public static final Set<String> KEYWORDS = Set.of(
    AND,
    DEL,
    FROM,
    NOT,
    WHILE,
    AS,
    ELIF,
    GLOBAL,
    OR,
    WITH,
    ASSERT,
    ELSE,
    IF,
    PASS,
    YIELD,
    BREAK,
    EXCEPT,
    IMPORT,
    PRINT,
    CLASS,
    EXEC,
    IN,
    RAISE,
    CONTINUE,
    FINALLY,
    IS,
    RETURN,
    DEF,
    FOR,
    LAMBDA,
    TRY
  );

  // As per: https://docs.python.org/3/reference/lexical_analysis.html#keywords
  public static final Set<String> PY3_KEYWORDS = Set.of(
    FALSE,  AWAIT,    ELSE,    IMPORT,   PASS,
    NONE,   BREAK,    EXCEPT,  IN,       RAISE,
    TRUE,   CLASS,    FINALLY, IS,       RETURN,
    AND,    CONTINUE, FOR,     LAMBDA,   TRY,
    AS,     DEF,      FROM,    NONLOCAL, WHILE,
    ASSERT, DEL,      GLOBAL,  NOT,      WITH,
    ASYNC,  ELIF,     IF,      OR,       YIELD
  );

  public static final Set<String> BUILTIN_INTERFACES = Set.of(
    CALLABLE, HASHABLE, ITERABLE, ITERATOR, SIZED, CONTAINER, SEQUENCE, MAPPING, ABC_COMPLEX, ABC_REAL, ABC_RATIONAL, ABC_INTEGRAL,
    ABC_NUMBER
  );

  /**
   * TODO: dependency on language level.
   *
   * @param name what to check
   * @return true iff the name is either a keyword or a reserved name, like None.
   */
  public static boolean isReserved(@Nullable @NonNls String name) {
    return name != null && KEYWORDS.contains(name) || NONE.equals(name);
  }

  // NOTE: includes unicode only good for py3k
  public static final String IDENTIFIER_RE = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(IDENTIFIER_RE);

  /**
   * TODO: dependency on language level.
   *
   * @param name what to check
   * @return true iff name is not reserved and is a well-formed identifier.
   */
  public static boolean isIdentifier(@NotNull @NonNls String name) {
    return !isReserved(name) && isIdentifierString(name);
  }

  public static boolean isIdentifierString(@NotNull @NonNls String name) {
    return IDENTIFIER_PATTERN.matcher(name).matches();
  }

  public static boolean isRightOperatorName(@Nullable String name) {
    if ("__rshift__".equals(name)) return false;
    return name != null && (name.matches("__r[a-z]+__") || CONTAINS.equals(name));
  }

  public static boolean isRightOperatorName(@Nullable String referencedName, @Nullable String calleeName) {
    if (isRightOperatorName(calleeName)) return true;

    return referencedName != null && calleeName != null && calleeName.equals(leftToRightComparisonOperatorName(referencedName));
  }

  public static @Nullable String leftToRightOperatorName(@Nullable String name) {
    if (name == null) return null;

    final String rightComparisonOperatorName = leftToRightComparisonOperatorName(name);
    if (rightComparisonOperatorName != null) return rightComparisonOperatorName;

    return name.replaceFirst("__([a-z]+)__", "__r$1__");
  }

  private static @Nullable String leftToRightComparisonOperatorName(@NotNull String name) {
    return switch (name) {
      case "__lt__" -> "__gt__";
      case "__gt__" -> "__lt__";
      case "__ge__" -> "__le__";
      case "__le__" -> "__ge__";
      default -> null;
    };
  }

  /**
   * Available in Python 3 and Python 2 starting from 2.6.
   * <p/>
   * Attributes {@code __doc__}, {@code __dict__} and {@code __module__} should be inherited from object.
   */
  public static final Set<String> FUNCTION_SPECIAL_ATTRIBUTES = Set.of(
    "__defaults__",
    "__globals__",
    "__closure__",
    "__code__",
    "__name__"
  );

  public static final Set<String> LEGACY_FUNCTION_SPECIAL_ATTRIBUTES = Set.of(
    "func_defaults",
    "func_globals",
    "func_closure",
    "func_code",
    "func_name",
    "func_doc",
    "func_dict"
  );

  public static final Set<String> PY3_ONLY_FUNCTION_SPECIAL_ATTRIBUTES = Set.of("__annotations__", "__kwdefaults__");

  public static final Set<String> METHOD_SPECIAL_ATTRIBUTES = Set.of("__func__", "__self__", "__name__");

  public static final Set<String> LEGACY_METHOD_SPECIAL_ATTRIBUTES = Set.of("im_func", "im_self", "im_class");

  public static final String MRO = "mro";
}
