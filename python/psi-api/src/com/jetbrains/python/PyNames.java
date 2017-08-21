/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author dcheryasov
 */
@NonNls
public class PyNames {
  public static final String SITE_PACKAGES = "site-packages";
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
  public static final List<String> TYPE_STRING_TYPES = Collections.unmodifiableList(Arrays.asList(TYPE_UNICODE, TYPE_STR));
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

  private PyNames() {
  }

  public static final String INIT = "__init__";
  public static final String DICT = "__dict__";
  public static final String DOT_PY = ".py";
  public static final String DOT_PYI = ".pyi";
  public static final String INIT_DOT_PY = INIT + DOT_PY;
  public static final String INIT_DOT_PYI = INIT + DOT_PYI;

  public static final String SETUP_DOT_PY = "setup" + DOT_PY;

  public static final String NEW = "__new__";
  public static final String GETATTR = "__getattr__";
  public static final String GETATTRIBUTE = "__getattribute__";
  public static final String GET = "__get__";
  public static final String __CLASS__ = "__class__";
  public static final String DUNDER_METACLASS = "__metaclass__";
  public static final String METACLASS = "metaclass";
  public static final String TYPE = "type";

  public static final String SUPER = "super";

  public static final String OBJECT = "object";
  public static final String NONE = "None";
  public static final String TRUE = "True";
  public static final String FALSE = "False";

  public static final String TYPES_FUNCTION_TYPE = "types.FunctionType";
  public static final String TYPES_METHOD_TYPE = "types.UnboundMethodType";
  public static final String TYPES_INSTANCE_TYPE = "types.InstanceType";

  public static final String FUTURE_MODULE = "__future__";
  public static final String UNICODE_LITERALS = "unicode_literals";

  public static final String CLASSMETHOD = "classmethod";
  public static final String STATICMETHOD = "staticmethod";

  public static final String PROPERTY = "property";
  public static final String SETTER = "setter";
  public static final String DELETER = "deleter";
  public static final String GETTER = "getter";

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
  public static final String PATH_LIKE = "PathLike";

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
  public static final String FSPATH = "__fspath__";

  public static final String NAME = "__name__";
  public static final String ENTER = "__enter__";
  public static final String EXIT = "__exit__";

  public static final String CALLABLE_BUILTIN = "callable";
  public static final String NAMEDTUPLE = "namedtuple";
  public static final String COLLECTIONS = "collections";
  public static final String COLLECTIONS_NAMEDTUPLE = COLLECTIONS + "." + NAMEDTUPLE;

  public static final String FORMAT = "format";

  public static final String ABSTRACTMETHOD = "abstractmethod";
  public static final String ABSTRACTPROPERTY = "abstractproperty";
  public static final String ABC_META_CLASS = "ABCMeta";

  public static final String TUPLE = "tuple";
  public static final String SET = "set";
  public static final String SLICE = "slice";

  public static final String KEYS = "keys";
  public static final String EXTEND = "extend";
  public static final String UPDATE = "update";

  public static final String PASS = "pass";

  public static final String NOSE_TEST = "nose";
  public static final String PY_TEST = "pytest";
  public static final String TRIAL_TEST = "Twisted";

  public static final String TEST_CASE = "TestCase";

  public static final String PYCACHE = "__pycache__";

  public static final String NOT_IMPLEMENTED_ERROR = "NotImplementedError";

  public static final String UNKNOWN_TYPE = "Any";

  public static final String UNNAMED_ELEMENT = "<unnamed>";

  public static final String UNDERSCORE = "_";

  /**
   * Contains all known predefined names of "__foo__" form.
   */
  public static final ImmutableSet<String> UNDERSCORED_ATTRIBUTES = ImmutableSet.of(
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

  public static final ImmutableSet<String> COMPARISON_OPERATORS = ImmutableSet.of(
    "__eq__",
    "__ne__",
    "__lt__",
    "__le__",
    "__gt__",
    "__ge__",
    "__cmp__",
    "__contains__"
  );

  public static final ImmutableSet<String> SUBSCRIPTION_OPERATORS = ImmutableSet.of(
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

  private static final ImmutableMap<String, BuiltinDescription> BuiltinMethods = ImmutableMap.<String, BuiltinDescription>builder()
    .put("__abs__", _only_self_descr)
    .put("__add__", _self_other_descr)
    .put("__and__", _self_other_descr)
      //_BuiltinMethods.put("__all__", _only_self_descr);
      //_BuiltinMethods.put("__author__", _only_self_descr);
      //_BuiltinMethods.put("__bases__", _only_self_descr);
    .put("__call__", new BuiltinDescription("(self, *args, **kwargs)"))
    .put("__ceil__", _only_self_descr)
      //_BuiltinMethods.put("__class__", _only_self_descr);
    .put("__cmp__", _self_other_descr)
    .put("__coerce__", _self_other_descr)
    .put("__complex__", _only_self_descr)
    .put("__contains__", _self_item_descr)
    .put("__copy__", _only_self_descr)
      //_BuiltinMethods.put("__debug__", _only_self_descr);
    .put("__deepcopy__", new BuiltinDescription("(self, memodict={})"))
    .put("__del__", _only_self_descr)
    .put("__delete__", new BuiltinDescription("(self, instance)"))
    .put("__delattr__", _self_item_descr)
    .put("__delitem__", _self_key_descr)
    .put("__delslice__", new BuiltinDescription("(self, i, j)"))
      //_BuiltinMethods.put("__dict__", _only_self_descr);
    .put("__divmod__", _self_other_descr)
      //_BuiltinMethods.put("__doc__", _only_self_descr);
      //_BuiltinMethods.put("__docformat__", _only_self_descr);
    .put("__enter__", _only_self_descr)
    .put("__exit__", _exit_descr)
    .put("__eq__", _self_other_descr)
      //_BuiltinMethods.put("__file__", _only_self_descr);
    .put("__float__", _only_self_descr)
    .put("__floor__", _only_self_descr)
    .put("__floordiv__", _self_other_descr)
      //_BuiltinMethods.put("__future__", _only_self_descr);
    .put("__ge__", _self_other_descr)
    .put("__get__", new BuiltinDescription("(self, instance, owner)"))
    .put("__getattr__", _self_item_descr)
    .put("__getattribute__", _self_item_descr)
    .put("__getinitargs__", _only_self_descr)
    .put("__getitem__", _self_item_descr)
    .put("__getnewargs__", _only_self_descr)
      //_BuiltinMethods.put("__getslice__", new BuiltinDescription("(self, i, j)"));
    .put("__getstate__", _only_self_descr)
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
    .put("__missing__", _self_key_descr)
    .put("__mod__", _self_other_descr)
      //_BuiltinMethods.put("__mro__", _only_self_descr);
    .put("__mul__", _self_other_descr)
      //_BuiltinMethods.put("__name__", _only_self_descr);
    .put("__ne__", _self_other_descr)
    .put("__neg__", _only_self_descr)
    .put(NEW, new BuiltinDescription("(cls, *args, **kwargs)"))
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
    .put("__reduce_ex__", new BuiltinDescription("(self, protocol)"))
    .put("__repr__", _only_self_descr)
    .put("__reversed__", _only_self_descr)
    .put("__rfloordiv__", _self_other_descr)
    .put("__rlshift__", _self_other_descr)
    .put("__rmod__", _self_other_descr)
    .put("__rmul__", _self_other_descr)
    .put("__ror__", _self_other_descr)
    .put("__rpow__", _self_other_descr)
    .put("__rrshift__", _self_other_descr)
    .put("__rshift__", _self_other_descr)
    .put("__rsub__", _self_other_descr)
    .put("__rtruediv__", _self_other_descr)
    .put("__rxor__", _self_other_descr)
    .put("__set__", new BuiltinDescription("(self, instance, value)"))
    .put("__setattr__", new BuiltinDescription("(self, key, value)"))
    .put("__setitem__", new BuiltinDescription("(self, key, value)"))
    .put("__setslice__", new BuiltinDescription("(self, i, j, sequence)"))
    .put("__setstate__", new BuiltinDescription("(self, state)"))
    .put(SIZEOF, _only_self_descr)
      //_BuiltinMethods.put("__self__", _only_self_descr);
      //_BuiltinMethods.put("__slots__", _only_self_descr);
    .put("__str__", _only_self_descr)
    .put("__sub__", _self_other_descr)
    .put("__truediv__", _self_other_descr)
    .put("__trunc__", _only_self_descr)
    .put("__unicode__", _only_self_descr)
      //_BuiltinMethods.put("__version__", _only_self_descr);
    .put("__xor__", _self_other_descr)
    .build();

  public static final ImmutableMap<String, BuiltinDescription> PY2_BUILTIN_METHODS = ImmutableMap.<String, BuiltinDescription>builder()
    .putAll(BuiltinMethods)
    .put("__nonzero__", _only_self_descr)
    .put("__div__", _self_other_descr)
    .put(NEXT, _only_self_descr)
    .build();

  public static final ImmutableMap<String, BuiltinDescription> PY3_BUILTIN_METHODS = ImmutableMap.<String, BuiltinDescription>builder()
    .putAll(BuiltinMethods)
    .put("__bool__", _only_self_descr)
    .put("__bytes__", _only_self_descr)
    .put("__format__", new BuiltinDescription("(self, format_spec)"))
    .put("__instancecheck__", new BuiltinDescription("(self, instance)"))
    .put("__prepare__", new BuiltinDescription("(metacls, name, bases)"))
    .put("__round__", new BuiltinDescription("(self, n=None)"))
    .put("__subclasscheck__", new BuiltinDescription("(self, subclass)"))
    .put(DUNDER_NEXT, _only_self_descr)
    .build();

  public static final ImmutableMap<String, BuiltinDescription> PY35_BUILTIN_METHODS = ImmutableMap.<String, BuiltinDescription>builder()
    .putAll(PY3_BUILTIN_METHODS)
    .put("__imatmul__", _self_other_descr)
    .put("__matmul__", _self_other_descr)
    .put("__rmatmul__", _self_other_descr)
    .put(DUNDER_AWAIT, _only_self_descr)
    .put(AENTER, _only_self_descr)
    .put(AEXIT, _exit_descr)
    .put(AITER, _only_self_descr)
    .put(ANEXT, _only_self_descr)
    .build();

  public static final ImmutableMap<String, BuiltinDescription> PY36_BUILTIN_METHODS = ImmutableMap.<String, BuiltinDescription>builder()
    .putAll(PY35_BUILTIN_METHODS)
    .put(INIT_SUBCLASS, new BuiltinDescription("(cls, **kwargs)"))
    .put("__set_name__", new BuiltinDescription("(self, owner, name)"))
    .put("__fspath__", _only_self_descr)
    .build();

  public static ImmutableMap<String, BuiltinDescription> getBuiltinMethods(LanguageLevel level) {
    if (level.isAtLeast(LanguageLevel.PYTHON36)) {
      return PY36_BUILTIN_METHODS;
    }
    else if (level.isAtLeast(LanguageLevel.PYTHON35)) {
      return PY35_BUILTIN_METHODS;
    }
    else if (level.isAtLeast(LanguageLevel.PYTHON30)) {
      return PY3_BUILTIN_METHODS;
    }
    else {
      return PY2_BUILTIN_METHODS;
    }
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

  /**
   * Contains keywords as of CPython 2.5.
   */
  public static final ImmutableSet<String> KEYWORDS = ImmutableSet.of(
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

  public static final Set<String> BUILTIN_INTERFACES = ImmutableSet.of(
    CALLABLE, HASHABLE, ITERABLE, ITERATOR, SIZED, CONTAINER, SEQUENCE, MAPPING, ABC_COMPLEX, ABC_REAL, ABC_RATIONAL, ABC_INTEGRAL,
    ABC_NUMBER
  );

  /**
   * TODO: dependency on language level.
   *
   * @param name what to check
   * @return true iff the name is either a keyword or a reserved name, like None.
   */
  public static boolean isReserved(@NonNls String name) {
    return KEYWORDS.contains(name) || NONE.equals(name);
  }

  // NOTE: includes unicode only good for py3k
  private final static Pattern IDENTIFIER_PATTERN = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

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
    return name != null && (name.matches("__r[a-z]+__") || CONTAINS.equals(name));
  }

  public static boolean isRightOperatorName(@Nullable String referencedName, @Nullable String calleeName) {
    if (isRightOperatorName(calleeName)) return true;

    return referencedName != null && calleeName != null && calleeName.equals(leftToRightComparisonOperatorName(referencedName));
  }

  @Nullable
  public static String leftToRightOperatorName(@Nullable String name) {
    if (name == null) return null;

    final String rightComparisonOperatorName = leftToRightComparisonOperatorName(name);
    if (rightComparisonOperatorName != null) return rightComparisonOperatorName;

    return name.replaceFirst("__([a-z]+)__", "__r$1__");
  }

  @Nullable
  private static String leftToRightComparisonOperatorName(@NotNull String name) {
    switch (name) {
      case "__lt__":
        return "__gt__";
      case "__gt__":
        return "__lt__";
      case "__ge__":
      return "__le__";
      case "__le__":
        return "__ge__";
      case "__eq__":
      case "__ne__":
        return name;
      default:
        return null;
    }
  }

  /**
   * Available in Python 3 and Python 2 starting from 2.6.
   * <p/>
   * Attributes {@code __doc__}, {@code __dict__} and {@code __module__} should be inherited from object.
   */
  public static final ImmutableSet<String> FUNCTION_SPECIAL_ATTRIBUTES = ImmutableSet.of(
    "__defaults__",
    "__globals__",
    "__closure__",
    "__code__",
    "__name__"
  );

  public static final ImmutableSet<String> LEGACY_FUNCTION_SPECIAL_ATTRIBUTES = ImmutableSet.of(
    "func_defaults",
    "func_globals",
    "func_closure",
    "func_code",
    "func_name",
    "func_doc",
    "func_dict"
  );

  public static final ImmutableSet<String> PY3_ONLY_FUNCTION_SPECIAL_ATTRIBUTES = ImmutableSet.of("__annotations__", "__kwdefaults__");

  public static final ImmutableSet<String> METHOD_SPECIAL_ATTRIBUTES = ImmutableSet.of("__func__", "__self__");

  public static final ImmutableSet<String> LEGACY_METHOD_SPECIAL_ATTRIBUTES = ImmutableSet.of("im_func", "im_self", "im_class");

  public static final String MRO = "mro";
}
