// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.NonNls
import java.util.regex.Pattern

@NonNls
object PyNames {
  const val SITE_PACKAGES: String = "site-packages"
  const val DIST_PACKAGES: String = "dist-packages"

  /**
   * int type
   */
  const val TYPE_INT: String = "int"
  const val TYPE_LONG: String = "long"

  /**
   * float type
   */
  const val TYPE_FLOAT: String = "float"

  /**
   * complex type
   */
  const val TYPE_COMPLEX: String = "complex"

  /**
   * unicode string type (see [.TYPE_STRING_TYPES]
   */
  const val TYPE_UNICODE: String = "unicode"

  /**
   * string type (see [.TYPE_STRING_TYPES]
   */
  const val TYPE_STR: String = "str"

  /**
   * Any string type
   */
  @JvmField
  val TYPE_STRING_TYPES: List<String> = listOf(TYPE_UNICODE, TYPE_STR)

  /**
   * date type
   */
  const val TYPE_DATE: String = "datetime.date"

  /**
   * datetime type
   */
  const val TYPE_DATE_TIME: String = "datetime.datetime"

  /**
   * time type
   */
  const val TYPE_TIME: String = "datetime.time"

  const val TYPE_BYTES: String = "bytes"

  const val TYPE_BYTEARRAY: String = "bytearray"

  const val TYPE_ENUM: String = "enum.Enum"
  const val TYPE_ENUM_META: String = "enum.EnumMeta"
  const val TYPE_ENUM_FLAG: String = "enum.Flag"
  const val TYPE_ENUM_AUTO: String = "enum.auto"
  const val TYPE_ENUM_MEMBER: String = "enum.member"
  const val TYPE_ENUM_NONMEMBER: String = "enum.nonmember"

  const val TYPE_NONE: String = "_typeshed.NoneType"
  val TYPE_NONE_NAMES: Set<String> = setOf("types.NoneType", TYPE_NONE)

  @JvmField
  val BUILTINS_MODULES: Set<String> = setOf("builtins.py", "__builtin__.py")
  const val PYTHON_SDK_ID_NAME: String = "Python SDK"
  const val VERBOSE_REG_EXP_LANGUAGE_ID: String = "PythonVerboseRegExp"

  @NonNls
  const val PYTHON_MODULE_ID: @NonNls String = "PYTHON_MODULE"
  const val TESTCASE_SETUP_NAME: String = "setUp"
  const val PY_DOCSTRING_ID: String = "Doctest"
  const val END_WILDCARD: String = ".*"

  const val INIT: String = "__init__"
  const val DUNDER_DICT: String = "__dict__"
  const val DOT_PY: String = ".py"
  const val DOT_PYI: String = ".pyi"

  const val INIT_DOT_PY: String = INIT + DOT_PY

  const val INIT_DOT_PYI: String = INIT + DOT_PYI

  const val SETUP_DOT_PY: String = "setup$DOT_PY"

  const val NEW: String = "__new__"
  const val GETATTR: String = "__getattr__"
  const val GETATTRIBUTE: String = "__getattribute__"
  const val DUNDER_GET: String = "__get__"
  const val DUNDER_SET: String = "__set__"
  const val __CLASS__: String = "__class__"
  const val DUNDER_METACLASS: String = "__metaclass__"

  @NlsSafe
  const val METACLASS: @NlsSafe String = "metaclass"
  const val TYPE: String = "type"

  const val SUPER: String = "super"

  const val OBJECT: String = "object"

  @NlsSafe
  const val NONE: @NlsSafe String = "None"
  const val TRUE: String = "True"
  const val FALSE: String = "False"
  const val ELLIPSIS: String = "..."
  const val FUNCTION: String = "function"

  const val TYPES_FUNCTION_TYPE: String = "types.FunctionType"
  const val TYPES_COROUTINE_TYPE: String = "types.CoroutineType"
  const val TYPES_METHOD_TYPE: String = "types.UnboundMethodType"

  const val FUTURE_MODULE: String = "__future__"
  const val UNICODE_LITERALS: String = "unicode_literals"

  const val TEMPLATELIB_TEMPLATE: String = "string.templatelib.Template"

  const val CLASSMETHOD: String = "classmethod"
  const val STATICMETHOD: String = "staticmethod"
  const val OVERLOAD: String = "overload"

  const val OVERRIDE: String = "override"

  const val PROPERTY: String = "property"
  const val SETTER: String = "setter"
  const val DELETER: String = "deleter"
  const val GETTER: String = "getter"
  const val CACHED_PROPERTY: String = "cached_property"

  const val ALL: String = "__all__"
  const val SLOTS: String = "__slots__"
  const val DEBUG: String = "__debug__"

  const val ISINSTANCE: String = "isinstance"
  const val ASSERT_IS_INSTANCE: String = "assertIsInstance"
  const val HAS_ATTR: String = "hasattr"
  const val ISSUBCLASS: String = "issubclass"

  const val DOC: String = "__doc__"
  const val DOCFORMAT: String = "__docformat__"

  const val DIRNAME: String = "dirname"
  const val ABSPATH: String = "abspath"
  const val NORMPATH: String = "normpath"
  const val REALPATH: String = "realpath"
  const val JOIN: String = "join"
  const val REPLACE: String = "replace"
  const val FILE: String = "__file__"
  const val PARDIR: String = "pardir"
  const val CURDIR: String = "curdir"

  const val WARN: String = "warn"
  const val DEPRECATION_WARNING: String = "DeprecationWarning"
  const val PENDING_DEPRECATION_WARNING: String = "PendingDeprecationWarning"

  const val CONTAINER: String = "Container"
  const val HASHABLE: String = "Hashable"
  const val ITERABLE: String = "Iterable"
  const val ITERATOR: String = "Iterator"
  const val SIZED: String = "Sized"
  const val CALLABLE: String = "Callable"
  const val SEQUENCE: String = "Sequence"
  const val MAPPING: String = "Mapping"
  const val MUTABLE_MAPPING: String = "MutableMapping"
  const val ABC_SET: String = "Set"
  const val ABC_MUTABLE_SET: String = "MutableSet"

  const val AWAITABLE: String = "Awaitable"
  const val ASYNC_ITERABLE: String = "AsyncIterable"
  const val ABSTRACT_CONTEXT_MANAGER: String = "AbstractContextManager"
  const val ABSTRACT_ASYNC_CONTEXT_MANAGER: String = "AbstractAsyncContextManager"

  const val ABC_NUMBER: String = "Number"
  const val ABC_COMPLEX: String = "Complex"
  const val ABC_REAL: String = "Real"
  const val ABC_RATIONAL: String = "Rational"
  const val ABC_INTEGRAL: String = "Integral"

  const val CONTAINS: String = "__contains__"
  const val HASH: String = "__hash__"
  const val HASH_FUNCTION: String = "hash"
  const val ITER: String = "__iter__"
  const val NEXT: String = "next"
  const val DUNDER_NEXT: String = "__next__"
  const val LEN: String = "__len__"
  const val CALL: String = "__call__"
  const val GETITEM: String = "__getitem__"
  const val SETITEM: String = "__setitem__"
  const val DELITEM: String = "__delitem__"
  const val POS: String = "__pos__"
  const val EQ: String = "__eq__"
  const val LT: String = "__lt__"
  const val LE: String = "__le__"
  const val GT: String = "__gt__"
  const val GE: String = "__ge__"
  const val NEG: String = "__neg__"
  const val DIV: String = "__div__"
  const val TRUEDIV: String = "__truediv__"
  const val AITER: String = "__aiter__"
  const val ANEXT: String = "__anext__"
  const val AENTER: String = "__aenter__"
  const val AEXIT: String = "__aexit__"
  const val DUNDER_AWAIT: String = "__await__"
  const val SIZEOF: String = "__sizeof__"
  const val INIT_SUBCLASS: String = "__init_subclass__"
  const val COMPLEX: String = "__complex__"
  const val FLOAT: String = "__float__"
  const val INT: String = "__int__"
  const val BYTES: String = "__bytes__"
  const val ABS: String = "__abs__"
  const val ROUND: String = "__round__"
  const val CLASS_GETITEM: String = "__class_getitem__"
  const val PREPARE: String = "__prepare__"
  const val MATCH_ARGS: String = "__match_args__"

  const val NAME: String = "__name__"
  const val QUALNAME: String = "__qualname__"
  const val ANNOTATIONS: String = "__annotations__"
  const val MODULE: String = "__module__"
  const val ENTER: String = "__enter__"
  const val EXIT: String = "__exit__"

  const val CALLABLE_BUILTIN: String = "callable"
  const val NAMEDTUPLE: String = "namedtuple"
  const val COLLECTIONS: String = "collections"

  const val COLLECTIONS_NAMEDTUPLE_PY2: String = "$COLLECTIONS.$NAMEDTUPLE"
  const val COLLECTIONS_NAMEDTUPLE_PY3: String = "$COLLECTIONS.$INIT.$NAMEDTUPLE"

  const val FORMAT: String = "format"

  const val ABSTRACTMETHOD: String = "abstractmethod"
  const val ABSTRACTPROPERTY: String = "abstractproperty"
  const val ABC_META_CLASS: String = "ABCMeta"
  const val ABC: String = "abc.ABC"
  const val ABC_META: String = "abc.ABCMeta"

  const val TUPLE: String = "tuple"
  const val SET: String = "set"
  const val SLICE: String = "slice"
  const val DICT: String = "dict"

  const val KEYS: String = "keys"
  const val APPEND: String = "append"
  const val EXTEND: String = "extend"
  const val UPDATE: String = "update"
  const val CLEAR: String = "clear"
  const val POP: String = "pop"
  const val POPITEM: String = "popitem"
  const val SETDEFAULT: String = "setdefault"

  const val PASS: String = "pass"

  const val TEST_CASE: String = "TestCase"

  const val PYCACHE: String = "__pycache__"

  const val NOT_IMPLEMENTED_ERROR: String = "NotImplementedError"

  @NlsSafe
  const val ANY_TYPE: @NlsSafe String = "Any"

  @NlsSafe
  const val UNKNOWN_TYPE: @NlsSafe String = "Unknown"

  @NlsSafe
  const val OVERLOAD_TYPE: @NlsSafe String = "Overload"

  @NlsSafe
  const val UNNAMED_ELEMENT: @NlsSafe String = "<unnamed>"

  const val UNDERSCORE: String = "_"

  /**
   * Contains all known predefined names of "__foo__" form.
   */
  @JvmField
  val UNDERSCORED_ATTRIBUTES: Set<String> = setOf(
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
  )

  @JvmField
  val COMPARISON_OPERATORS: Set<String> = setOf(
    "__eq__",
    "__ne__",
    "__lt__",
    "__le__",
    "__gt__",
    "__ge__",
    "__cmp__",
    "__contains__"
  )

  @JvmField
  val SUBSCRIPTION_OPERATORS: Set<String> = setOf(
    GETITEM,
    SETITEM,
    DELITEM
  )

  private val onlySelfDescr = BuiltinDescription("(self)")
  private fun onlySelfDescr(returnType: String, vararg imports: String) = BuiltinDescription("(self) -> $returnType", *imports)
  private val selfOtherDescr = BuiltinDescription("(self, other)")
  private val selfItemDescr = BuiltinDescription("(self, item)")
  private val selfKeyDescr = BuiltinDescription("(self, key)")
  private val exitDescr = BuiltinDescription("(self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None)", "types.TracebackType")

  private val BuiltinMethods = mapOf(
    ABS to onlySelfDescr,
    "__add__" to selfOtherDescr,
    "__and__" to selfOtherDescr,
    //"__all__" to _only_self_descr,
    //"__author__" to _only_self_descr,
    //"__bases__" to _only_self_descr,
    CALL to BuiltinDescription("(self, *args, **kwargs)"),
    "__ceil__" to onlySelfDescr,
    "__class__" to onlySelfDescr,
    "__cmp__" to selfOtherDescr,
    "__coerce__" to selfOtherDescr,
    COMPLEX to onlySelfDescr,
    "__contains__" to selfItemDescr,
    "__copy__" to onlySelfDescr,
    // "__debug__" to _only_self_descr,
    "__deepcopy__" to BuiltinDescription("(self, memo)"),
    "__del__" to onlySelfDescr,
    "__delete__" to BuiltinDescription("(self, instance)"),
    "__delattr__" to selfItemDescr,
    "__delitem__" to selfKeyDescr,

    "__delslice__" to BuiltinDescription("(self, i, j)"),
    // "__dict__", _only_self_descr,
    "__divmod__" to selfOtherDescr,
    //"__doc__", _only_self_descr,
    //"__docformat__" to _only_self_descr,
    "__enter__" to onlySelfDescr,
    "__exit__" to exitDescr,
    "__eq__" to selfOtherDescr,
    // "__file__" to _only_self_descr,
    FLOAT to onlySelfDescr,
    "__floor__" to onlySelfDescr,
    "__floordiv__" to selfOtherDescr,
    //"__future__" to _only_self_descr;
    "__ge__" to selfOtherDescr,
    "__get__" to BuiltinDescription("(self, instance, owner)"),
    "__getattr__" to selfItemDescr,
    "__getattribute__" to selfItemDescr,
    "__getinitargs__" to onlySelfDescr,
    "__getitem__" to selfItemDescr,
    "__getnewargs__" to onlySelfDescr,
    //"__getslice__" to BuiltinDescription("(self, i, j)"),
    "__getstate__" to onlySelfDescr,
    "__gt__" to selfOtherDescr,
    "__hash__" to onlySelfDescr,
    "__hex__" to onlySelfDescr,
    "__iadd__" to selfOtherDescr,
    "__iand__" to selfOtherDescr,
    "__idiv__" to selfOtherDescr,
    "__ifloordiv__" to selfOtherDescr,
    //"__import__" to _only_self_descr
    "__ilshift__" to selfOtherDescr,
    "__imod__" to selfOtherDescr,
    "__imul__" to selfOtherDescr,
    "__index__" to onlySelfDescr,
    INIT to onlySelfDescr,
    INT to onlySelfDescr,
    "__invert__" to onlySelfDescr,
    "__ior__" to selfOtherDescr,
    "__ipow__" to selfOtherDescr,
    "__irshift__" to selfOtherDescr,
    "__isub__" to selfOtherDescr,
    "__iter__" to onlySelfDescr,
    "__itruediv__" to selfOtherDescr,
    "__ixor__" to selfOtherDescr,
    "__le__" to selfOtherDescr,
    "__len__" to onlySelfDescr,
    "__long__" to onlySelfDescr,
    "__lshift__" to selfOtherDescr,
    "__lt__" to selfOtherDescr,
    //"__members__" to_only_self_descr,
    //"__metaclass__" to _only_self_descr,
    "__missing__" to selfKeyDescr,
    "__mod__" to selfOtherDescr,
    //"__mro__" to _only_self_descr,
    "__mul__" to selfOtherDescr,
    //"__name__" to _only_self_descr,
    "__ne__" to selfOtherDescr,
    "__neg__" to onlySelfDescr,
    NEW to BuiltinDescription("(cls, *args, **kwargs)"),
    "__oct__" to onlySelfDescr,
    "__or__" to selfOtherDescr,
    //_"__path__" to _only_self_descr,
    "__pos__" to onlySelfDescr,
    "__pow__" to BuiltinDescription("(self, power, modulo=None)"),
    "__radd__" to selfOtherDescr,
    "__rand__" to selfOtherDescr,
    "__rdiv__" to selfOtherDescr,
    "__rdivmod__" to selfOtherDescr,
    "__reduce__" to onlySelfDescr,
    "__reduce_ex__" to BuiltinDescription("(self, protocol)"),
    "__repr__" to onlySelfDescr,
    "__reversed__" to onlySelfDescr,
    "__rfloordiv__" to selfOtherDescr,
    "__rlshift__" to selfOtherDescr,
    "__rmod__" to selfOtherDescr,
    "__rmul__" to selfOtherDescr,
    "__ror__" to selfOtherDescr,
    "__rpow__" to selfOtherDescr,
    "__rrshift__" to selfOtherDescr,
    "__rshift__" to selfOtherDescr,
    "__rsub__" to selfOtherDescr,
    "__rtruediv__" to selfOtherDescr,
    "__rxor__" to selfOtherDescr,
    "__set__" to BuiltinDescription("(self, instance, value)"),
    "__setattr__" to BuiltinDescription("(self, key, value)"),
    "__setitem__" to BuiltinDescription("(self, key, value)"),
    "__setslice__" to BuiltinDescription("(self, i, j, sequence)"),
    "__setstate__" to BuiltinDescription("(self, state)"),
    SIZEOF to onlySelfDescr,
    //_"__self__" to_only_self_descr),
    //_"__slots__" to_only_self_descr),
    "__str__" to onlySelfDescr,
    "__sub__" to selfOtherDescr,
    "__truediv__" to selfOtherDescr,
    "__trunc__" to onlySelfDescr,
    "__unicode__" to onlySelfDescr,
    //_"__version__" to _only_self_descr,
    "__xor__" to selfOtherDescr,
  )

  private val PY2_BUILTIN_METHODS: Map<String, BuiltinDescription> = BuiltinMethods + mapOf(
    "__nonzero__" to onlySelfDescr,
    "__div__" to selfOtherDescr,
    NEXT to onlySelfDescr
  )

  private val PY3_BUILTIN_METHODS: Map<String, BuiltinDescription> = BuiltinMethods + mapOf(
    "__bool__" to onlySelfDescr,
    BYTES to onlySelfDescr,
    "__format__" to BuiltinDescription("(self, format_spec: str) -> str"),
    "__instancecheck__" to BuiltinDescription("(self, instance)"),
    PREPARE to BuiltinDescription("(metacls, name: str, bases: tuple[type, ...], **kwargs: object) -> Mapping[str, object]", "collections.abc.Mapping"),
    ROUND to BuiltinDescription("(self, n=None)"),
    "__subclasscheck__" to BuiltinDescription("(self, subclass)"),
    DUNDER_NEXT to onlySelfDescr
  )

  private val PY35_BUILTIN_METHODS: Map<String, BuiltinDescription> = PY3_BUILTIN_METHODS + mapOf(
    "__imatmul__" to selfOtherDescr,
    "__matmul__" to selfOtherDescr,
    "__rmatmul__" to selfOtherDescr,
    DUNDER_AWAIT to onlySelfDescr,
    AENTER to onlySelfDescr,
    AEXIT to exitDescr,
    AITER to onlySelfDescr,
    ANEXT to onlySelfDescr,
  )

  private val PY36_BUILTIN_METHODS: Map<String, BuiltinDescription> = PY35_BUILTIN_METHODS + mapOf(
    INIT_SUBCLASS to BuiltinDescription("(cls, **kwargs)"),
    "__set_name__" to BuiltinDescription("(self, owner: object, name: str)"),
    "__fspath__" to onlySelfDescr("AnyStr", "typing.AnyStr"),
  )

  private val PY37_BUILTIN_METHODS: Map<String, BuiltinDescription> = PY36_BUILTIN_METHODS + mapOf(
    CLASS_GETITEM to BuiltinDescription("(cls, item: object) -> object"),
    "__mro_entries__" to BuiltinDescription("(self, bases: tuple[type, ...]) -> tuple[type, ...]"),
  )

  private val PY37_MODULE_BUILTIN_METHODS: Map<String, BuiltinDescription> = mapOf(
    "__getattr__" to BuiltinDescription("(name: str) -> object"),
    "__dir__" to BuiltinDescription("() -> Iterable[object]", "collections.abc.Iterable"),
  )

  @JvmStatic
  fun getBuiltinMethods(level: LanguageLevel): Map<String, BuiltinDescription> {
    if (level.isAtLeast(LanguageLevel.PYTHON37)) {
      return PY37_BUILTIN_METHODS
    }
    else if (level.isAtLeast(LanguageLevel.PYTHON36)) {
      return PY36_BUILTIN_METHODS
    }
    else if (level.isAtLeast(LanguageLevel.PYTHON35)) {
      return PY35_BUILTIN_METHODS
    }
    else if (!level.isPython2) {
      return PY3_BUILTIN_METHODS
    }
    else {
      return PY2_BUILTIN_METHODS
    }
  }

  @JvmStatic
  fun getModuleBuiltinMethods(level: LanguageLevel): Map<String, BuiltinDescription> {
    if (level.isAtLeast(LanguageLevel.PYTHON37)) {
      return PY37_MODULE_BUILTIN_METHODS
    }

    return emptyMap()
  }

  // canonical names, not forced by interpreter
  const val CANONICAL_SELF: String = "self"
  const val CANONICAL_CLS: String = "cls"
  const val BASESTRING: String = "basestring"

  /*
  Python keywords
 */
  const val CLASS: String = "class"
  const val DEF: String = "def"
  const val IF: String = "if"
  const val ELSE: String = "else"
  const val ELIF: String = "elif"
  const val TRY: String = "try"
  const val EXCEPT: String = "except"
  const val FINALLY: String = "finally"
  const val WHILE: String = "while"
  const val FOR: String = "for"
  const val WITH: String = "with"
  const val AS: String = "as"
  const val ASSERT: String = "assert"
  const val DEL: String = "del"
  const val EXEC: String = "exec"
  const val FROM: String = "from"
  const val IMPORT: String = "import"
  const val RAISE: String = "raise"
  const val PRINT: String = "print"
  const val BREAK: String = "break"
  const val CONTINUE: String = "continue"
  const val GLOBAL: String = "global"
  const val RETURN: String = "return"
  const val YIELD: String = "yield"
  const val NONLOCAL: String = "nonlocal"
  const val AND: String = "and"
  const val OR: String = "or"
  const val IS: String = "is"
  const val IN: String = "in"
  const val NOT: String = "not"
  const val LAMBDA: String = "lambda"
  const val ASYNC: String = "async"
  const val AWAIT: String = "await"
  const val MATCH: String = "match"
  const val CASE: String = "case"
  const val LAZY: String = "lazy"

  /**
   * Contains keywords as of CPython 2.5.
   */
  val KEYWORDS: Set<String> = setOf(
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
  )

  // As per: https://docs.python.org/3/reference/lexical_analysis.html#keywords
  @JvmField
  val PY3_KEYWORDS: Set<String> = setOf(
    FALSE, AWAIT, ELSE, IMPORT, PASS,
    NONE, BREAK, EXCEPT, IN, RAISE,
    TRUE, CLASS, FINALLY, IS, RETURN,
    AND, CONTINUE, FOR, LAMBDA, TRY,
    AS, DEF, FROM, NONLOCAL, WHILE,
    ASSERT, DEL, GLOBAL, NOT, WITH,
    ASYNC, ELIF, IF, OR, YIELD
  )

  @JvmField
  val BUILTIN_INTERFACES: Set<String> = setOf(
    CALLABLE, HASHABLE, ITERABLE, ITERATOR, SIZED, CONTAINER, SEQUENCE, MAPPING, ABC_COMPLEX, ABC_REAL, ABC_RATIONAL, ABC_INTEGRAL,
    ABC_NUMBER
  )

  /**
   * TODO: dependency on language level.
   *
   * @param name what to check
   * @return true iff the name is either a keyword or a reserved name, like None.
   */
  @JvmStatic
  fun isReserved(@NonNls name: @NonNls String?): Boolean {
    return name != null && name in KEYWORDS || NONE == name
  }

  // NOTE: includes unicode only good for py3k
  const val IDENTIFIER_RE: String = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*"
  private val IDENTIFIER_PATTERN: Pattern = Pattern.compile(IDENTIFIER_RE)

  /**
   * TODO: dependency on language level.
   *
   * @param name what to check
   * @return true iff name is not reserved and is a well-formed identifier.
   */
  @JvmStatic
  fun isIdentifier(@NonNls name: @NonNls String): Boolean {
    return !isReserved(name) && isIdentifierString(name)
  }

  @JvmStatic
  fun isIdentifierString(@NonNls name: @NonNls String): Boolean {
    return IDENTIFIER_PATTERN.matcher(name).matches()
  }

  @JvmStatic
  fun isRightOperatorName(name: String?): Boolean {
    if ("__rshift__" == name) return false
    return name != null && (name.matches("__r[a-z]+__".toRegex()) || CONTAINS == name)
  }

  @JvmStatic
  fun isRightOperatorName(referencedName: String?, calleeName: String?): Boolean {
    if (isRightOperatorName(calleeName)) return true

    return referencedName != null && calleeName != null && calleeName == leftToRightComparisonOperatorName(referencedName)
  }

  private val INPLACE_OPERATOR_PATTERN = "__i([a-z]+)__".toRegex()

  @JvmStatic
  private fun isInplaceOperatorName(name: String?): Boolean {
    return name != null && (name.matches(INPLACE_OPERATOR_PATTERN))
  }

  @JvmStatic
  fun isInplaceOperatorName(referencedName: String?, calleeName: String?): Boolean {
    if (isInplaceOperatorName(calleeName)) return true

    return referencedName != null && calleeName != null && calleeName == leftToRightComparisonOperatorName(referencedName)
  }

  @JvmStatic
  fun leftToRightOperatorName(name: String?): String? {
    if (name == null) return null

    val rightComparisonOperatorName = leftToRightComparisonOperatorName(name)
    if (rightComparisonOperatorName != null) return rightComparisonOperatorName

    return name.replaceFirst("__([a-z]+)__".toRegex(), "__r$1__")
  }

  @JvmStatic
  fun inplaceToLeftOperatorName(name: String?): String? {
    if (name == null) return null

    return name.replaceFirst(INPLACE_OPERATOR_PATTERN, "__$1__")
  }

  @JvmStatic
  fun inplaceToRightOperatorName(name: String?): String? {
    if (name == null) return null

    return name.replaceFirst(INPLACE_OPERATOR_PATTERN, "__r$1__")
  }

  @Deprecated("use `PyAstElement.protectionLevel` instead")
  fun isProtected(name: @NonNls String): Boolean =
    ProtectionLevel.forName(name) == ProtectionLevel.PROTECTED

  @JvmStatic
  @Deprecated("use `PyAstElement.protectionLevel` instead")
  fun isPrivate(name: @NonNls String): Boolean =
    ProtectionLevel.forName(name) == ProtectionLevel.PRIVATE

  fun isSunder(name: @NonNls String): Boolean =
    name.length > 2 && name.startsWith("_") && name.endsWith("_") && name[1] != '_' && name[name.length - 2] != '_'

  fun isDunder(name: @NonNls String): Boolean =
    name.length > 4 && name.startsWith("__") && name.endsWith("__") && name[2] != '_' && name[name.length - 3] != '_'

  private fun leftToRightComparisonOperatorName(name: String): String? {
    return when (name) {
      "__lt__" -> "__gt__"
      "__gt__" -> "__lt__"
      "__ge__" -> "__le__"
      "__le__" -> "__ge__"
      else -> null
    }
  }

  /**
   * Available in Python 3 and Python 2 starting from 2.6.
   *
   *
   * Attributes `__doc__`, `__dict__` and `__module__` should be inherited from object.
   */
  @JvmField
  val FUNCTION_SPECIAL_ATTRIBUTES: Set<String> = setOf(
    "__defaults__",
    "__globals__",
    "__closure__",
    "__code__",
    "__name__"
  )

  @JvmField
  val LEGACY_FUNCTION_SPECIAL_ATTRIBUTES: Set<String> = setOf(
    "func_defaults",
    "func_globals",
    "func_closure",
    "func_code",
    "func_name",
    "func_doc",
    "func_dict"
  )

  @JvmField
  val PY3_ONLY_FUNCTION_SPECIAL_ATTRIBUTES: Set<String> = setOf("__annotations__", "__kwdefaults__")

  @JvmField
  val METHOD_SPECIAL_ATTRIBUTES: Set<String> = setOf("__func__", "__self__", "__name__")

  @JvmField
  val LEGACY_METHOD_SPECIAL_ATTRIBUTES: Set<String> = setOf("im_func", "im_self", "im_class")

  const val MRO: String = "mro"

  class BuiltinDescription(
    val signature: String,
    vararg imports: String,
    val doc: String = "",
  ) {
    val imports: Set<String> = imports.toSet()
  }
}
