import os
import re
import string
import sys
import time
import types

# !!! Don't forget to update VERSION and required_gen_version if necessary !!!
VERSION = "1.147"

OUT_ENCODING = 'utf-8'

version = (
    (sys.hexversion & (0xff << 24)) >> 24,
    (sys.hexversion & (0xff << 16)) >> 16
)

if version[0] >= 3:
    #noinspection PyUnresolvedReferences
    import builtins as the_builtins

    string = "".__class__

    STR_TYPES = (getattr(the_builtins, "bytes"), str)

    NUM_TYPES = (int, float)
    SIMPLEST_TYPES = NUM_TYPES + STR_TYPES + (None.__class__,)
    EASY_TYPES = NUM_TYPES + STR_TYPES + (None.__class__, dict, tuple, list)

    def the_exec(source, context):
        exec (source, context)

else: # < 3.0
    import __builtin__ as the_builtins

    STR_TYPES = (getattr(the_builtins, "unicode"), str)

    NUM_TYPES = (int, long, float)
    SIMPLEST_TYPES = NUM_TYPES + STR_TYPES + (types.NoneType,)
    EASY_TYPES = NUM_TYPES + STR_TYPES + (types.NoneType, dict, tuple, list)

    def the_exec(source, context):
        #noinspection PyRedundantParentheses
        exec (source) in context

if version[0] == 2 and version[1] < 4:
    HAS_DECORATORS = False

    def lstrip(s, prefix):
        i = 0
        while s[i] == prefix:
            i += 1
        return s[i:]

else:
    HAS_DECORATORS = True
    lstrip = string.lstrip

# return type inference helper table
INT_LIT = '0'
FLOAT_LIT = '0.0'
DICT_LIT = '{}'
LIST_LIT = '[]'
TUPLE_LIT = '()'
BOOL_LIT = 'False'
RET_TYPE = {# {'type_name': 'value_string'} lookup table
            # chaining
            "self": "self",
            "self.": "self",
            # int
            "int": INT_LIT,
            "Int": INT_LIT,
            "integer": INT_LIT,
            "Integer": INT_LIT,
            "short": INT_LIT,
            "long": INT_LIT,
            "number": INT_LIT,
            "Number": INT_LIT,
            # float
            "float": FLOAT_LIT,
            "Float": FLOAT_LIT,
            "double": FLOAT_LIT,
            "Double": FLOAT_LIT,
            "floating": FLOAT_LIT,
            # boolean
            "bool": BOOL_LIT,
            "boolean": BOOL_LIT,
            "Bool": BOOL_LIT,
            "Boolean": BOOL_LIT,
            "True": BOOL_LIT,
            "true": BOOL_LIT,
            "False": BOOL_LIT,
            "false": BOOL_LIT,
            # list
            'list': LIST_LIT,
            'List': LIST_LIT,
            '[]': LIST_LIT,
            # tuple
            "tuple": TUPLE_LIT,
            "sequence": TUPLE_LIT,
            "Sequence": TUPLE_LIT,
            # dict
            "dict": DICT_LIT,
            "Dict": DICT_LIT,
            "dictionary": DICT_LIT,
            "Dictionary": DICT_LIT,
            "map": DICT_LIT,
            "Map": DICT_LIT,
            "hashtable": DICT_LIT,
            "Hashtable": DICT_LIT,
            "{}": DICT_LIT,
            # "objects"
            "object": "object()",
}
if version[0] < 3:
    UNICODE_LIT = 'u""'
    BYTES_LIT = '""'
    RET_TYPE.update({
        'string': BYTES_LIT,
        'String': BYTES_LIT,
        'str': BYTES_LIT,
        'Str': BYTES_LIT,
        'character': BYTES_LIT,
        'char': BYTES_LIT,
        'unicode': UNICODE_LIT,
        'Unicode': UNICODE_LIT,
        'bytes': BYTES_LIT,
        'byte': BYTES_LIT,
        'Bytes': BYTES_LIT,
        'Byte': BYTES_LIT,
    })
    DEFAULT_STR_LIT = BYTES_LIT
    # also, files:
    RET_TYPE.update({
        'file': "file('/dev/null')",
    })

    def ensureUnicode(data):
        if type(data) == str:
            return data.decode(OUT_ENCODING, 'replace')
        return unicode(data)
else:
    UNICODE_LIT = '""'
    BYTES_LIT = 'b""'
    RET_TYPE.update({
        'string': UNICODE_LIT,
        'String': UNICODE_LIT,
        'str': UNICODE_LIT,
        'Str': UNICODE_LIT,
        'character': UNICODE_LIT,
        'char': UNICODE_LIT,
        'unicode': UNICODE_LIT,
        'Unicode': UNICODE_LIT,
        'bytes': BYTES_LIT,
        'byte': BYTES_LIT,
        'Bytes': BYTES_LIT,
        'Byte': BYTES_LIT,
    })
    DEFAULT_STR_LIT = UNICODE_LIT
    # also, files: we can't provide an easy expression on py3k
    RET_TYPE.update({
        'file': None,
    })

    def ensureUnicode(data):
        if type(data) == bytes:
            return data.decode(OUT_ENCODING, 'replace')
        return str(data)

if version[0] > 2:
    import io  # in 3.0


    def fopen(name, mode):
        kwargs = {}
        if 'b' not in mode:
            kwargs['encoding'] = OUT_ENCODING
        return io.open(name, mode, **kwargs)
else:
    fopen = open

if sys.platform == 'cli':
    #noinspection PyUnresolvedReferences
    from System import DateTime

    class Timer(object):
        def __init__(self):
            self.started = DateTime.Now

        def elapsed(self):
            return (DateTime.Now - self.started).TotalMilliseconds
else:
    class Timer(object):
        def __init__(self):
            self.started = time.time()

        def elapsed(self):
            return int((time.time() - self.started) * 1000)

IS_JAVA = hasattr(os, "java")

BUILTIN_MOD_NAME = the_builtins.__name__

IDENT_PATTERN = "[A-Za-z_][0-9A-Za-z_]*" # re pattern for identifier
NUM_IDENT_PATTERN = re.compile("([A-Za-z_]+)[0-9]?[A-Za-z_]*") # 'foo_123' -> $1 = 'foo_'
STR_CHAR_PATTERN = "[0-9A-Za-z_.,\+\-&\*% ]"

DOC_FUNC_RE = re.compile("(?:.*\.)?(\w+)\(([^\)]*)\).*") # $1 = function name, $2 = arglist

SANE_REPR_RE = re.compile(IDENT_PATTERN + "(?:\(.*\))?") # identifier with possible (...), go catches

IDENT_RE = re.compile("(" + IDENT_PATTERN + ")") # $1 = identifier

STARS_IDENT_RE = re.compile("(\*?\*?" + IDENT_PATTERN + ")") # $1 = identifier, maybe with a * or **

IDENT_EQ_RE = re.compile("(" + IDENT_PATTERN + "\s*=)") # $1 = identifier with a following '='

SIMPLE_VALUE_RE = re.compile(
    "(\([+-]?[0-9](?:\s*,\s*[+-]?[0-9])*\))|" + # a numeric tuple, e.g. in pygame
    "([+-]?[0-9]+\.?[0-9]*(?:[Ee]?[+-]?[0-9]+\.?[0-9]*)?)|" + # number
    "('" + STR_CHAR_PATTERN + "*')|" + # single-quoted string
    '("' + STR_CHAR_PATTERN + '*")|' + # double-quoted string
    "(\[\])|" +
    "(\{\})|" +
    "(\(\))|" +
    "(True|False|None)"
) # $? = sane default value

###########################   parsing   ###########################################################
if version[0] < 3:
    from pycharm_generator_utils.pyparsing_py2 import *
else:
    #noinspection PyUnresolvedReferences
    from pycharm_generator_utils.pyparsing_py3 import *

# grammar to parse parameter lists

# // snatched from parsePythonValue.py, from pyparsing samples, copyright 2006 by Paul McGuire but under BSD license.
# we don't suppress lots of punctuation because we want it back when we reconstruct the lists

lparen, rparen, lbrack, rbrack, lbrace, rbrace, colon = map(Literal, "()[]{}:")

integer = Combine(Optional(oneOf("+ -")) + Word(nums)).setName("integer")
real = Combine(Optional(oneOf("+ -")) + Word(nums) + "." +
               Optional(Word(nums)) +
               Optional(oneOf("e E") + Optional(oneOf("+ -")) + Word(nums))).setName("real")
tupleStr = Forward()
listStr = Forward()
dictStr = Forward()

boolLiteral = oneOf("True False")
noneLiteral = Literal("None")

listItem = real | integer | quotedString | unicodeString | boolLiteral | noneLiteral | \
           Group(listStr) | tupleStr | dictStr

tupleStr << ( Suppress("(") + Optional(delimitedList(listItem)) +
              Optional(Literal(",")) + Suppress(")") ).setResultsName("tuple")

listStr << (lbrack + Optional(delimitedList(listItem) +
                              Optional(Literal(","))) + rbrack).setResultsName("list")

dictEntry = Group(listItem + colon + listItem)
dictStr << (lbrace + Optional(delimitedList(dictEntry) + Optional(Literal(","))) + rbrace).setResultsName("dict")
# \\ end of the snatched part

# our output format is s-expressions:
# (simple name optional_value) is name or name=value
# (nested (simple ...) (simple ...)) is (name, name,...)
# (opt ...) is [, ...] or suchlike.

T_SIMPLE = 'Simple'
T_NESTED = 'Nested'
T_OPTIONAL = 'Opt'
T_RETURN = "Ret"

TRIPLE_DOT = '...'

COMMA = Suppress(",")
APOS = Suppress("'")
QUOTE = Suppress('"')
SP = Suppress(Optional(White()))

ident = Word(alphas + "_", alphanums + "_-.").setName("ident") # we accept things like "foo-or-bar"
decorated_ident = ident + Optional(Suppress(SP + Literal(":") + SP + ident)) # accept "foo: bar", ignore "bar"
spaced_ident = Combine(decorated_ident + ZeroOrMore(Literal(' ') + decorated_ident)) # we accept 'list or tuple' or 'C struct'

# allow quoted names, because __setattr__, etc docs use it
paramname = spaced_ident | \
            APOS + spaced_ident + APOS | \
            QUOTE + spaced_ident + QUOTE

parenthesized_tuple = ( Literal("(") + Optional(delimitedList(listItem, combine=True)) +
                        Optional(Literal(",")) + Literal(")") ).setResultsName("(tuple)")

initializer = (SP + Suppress("=") + SP + Combine(parenthesized_tuple | listItem | ident)).setName("=init") # accept foo=defaultfoo

param = Group(Empty().setParseAction(replaceWith(T_SIMPLE)) + Combine(Optional(oneOf("* **")) + paramname) + Optional(initializer))

ellipsis = Group(
    Empty().setParseAction(replaceWith(T_SIMPLE)) + \
    (Literal("..") +
     ZeroOrMore(Literal('.'))).setParseAction(replaceWith(TRIPLE_DOT)) # we want to accept both 'foo,..' and 'foo, ...'
)

paramSlot = Forward()

simpleParamSeq = ZeroOrMore(paramSlot + COMMA) + Optional(paramSlot + Optional(COMMA))
nestedParamSeq = Group(
    Suppress('(').setParseAction(replaceWith(T_NESTED)) + \
    simpleParamSeq + Optional(ellipsis + Optional(COMMA) + Optional(simpleParamSeq)) + \
    Suppress(')')
) # we accept "(a1, ... an)"

paramSlot << (param | nestedParamSeq)

optionalPart = Forward()

paramSeq = simpleParamSeq + Optional(optionalPart) # this is our approximate target

optionalPart << (
    Group(
        Suppress('[').setParseAction(replaceWith(T_OPTIONAL)) + Optional(COMMA) +
        paramSeq + Optional(ellipsis) +
        Suppress(']')
    )
    | ellipsis
)

return_type = Group(
    Empty().setParseAction(replaceWith(T_RETURN)) +
    Suppress(SP + (Literal("->") | (Literal(":") + SP + Literal("return"))) + SP) +
    ident
)

# this is our ideal target, with balancing paren and a multiline rest of doc.
paramSeqAndRest = paramSeq + Suppress(')') + Optional(return_type) + Suppress(Optional(Regex(".*(?s)")))
############################################################################################


# Some values are known to be of no use in source and needs to be suppressed.
# Dict is keyed by module names, with "*" meaning "any module";
# values are lists of names of members whose value must be pruned.
SKIP_VALUE_IN_MODULE = {
    "sys": (
        "modules", "path_importer_cache", "argv", "builtins",
        "last_traceback", "last_type", "last_value", "builtin_module_names",
    ),
    "posix": (
        "environ",
    ),
    "zipimport": (
        "_zip_directory_cache",
    ),
    "*": (BUILTIN_MOD_NAME,)
}
# {"module": ("name",..)}: omit the names from the skeleton at all.
OMIT_NAME_IN_MODULE = {}

if version[0] >= 3:
    v = OMIT_NAME_IN_MODULE.get(BUILTIN_MOD_NAME, []) + ["True", "False", "None", "__debug__"]
    OMIT_NAME_IN_MODULE[BUILTIN_MOD_NAME] = v

if IS_JAVA and version > (2, 4):  # in 2.5.1 things are way weird!
    OMIT_NAME_IN_MODULE['_codecs'] = ['EncodingMap']
    OMIT_NAME_IN_MODULE['_hashlib'] = ['Hash']

ADD_VALUE_IN_MODULE = {
    "sys": ("exc_value = Exception()", "exc_traceback=None"), # only present after an exception in current thread
}

# Some values are special and are better represented by hand-crafted constructs.
# Dict is keyed by (module name, member name) and value is the replacement.
REPLACE_MODULE_VALUES = {
    ("numpy.core.multiarray", "typeinfo"): "{}",
    ("psycopg2._psycopg", "string_types"): "{}", # badly mangled __eq__ breaks fmtValue
    ("PyQt5.QtWidgets", "qApp") : "QApplication()",  # instead of None
}
if version[0] <= 2:
    REPLACE_MODULE_VALUES[(BUILTIN_MOD_NAME, "None")] = "object()"
    for std_file in ("stdin", "stdout", "stderr"):
        REPLACE_MODULE_VALUES[("sys", std_file)] = "open('')" #

# Some functions and methods of some builtin classes have special signatures.
# {("class", "method"): ("signature_string")}
PREDEFINED_BUILTIN_SIGS = {                                             #TODO: user-skeleton
    ("type", "__init__"): "(cls, what, bases=None, dict=None)", # two sigs squeezed into one
    ("object", "__init__"): "(self)",
    ("object", "__new__"): "(cls, *more)", # only for the sake of parameter names readability
    ("object", "__subclasshook__"): "(cls, subclass)", # trusting PY-1818 on sig
    ("int", "__init__"): "(self, x, base=10)", # overrides a fake
    ("list", "__init__"): "(self, seq=())",
    ("tuple", "__init__"): "(self, seq=())", # overrides a fake
    ("set", "__init__"): "(self, seq=())",
    ("dict", "__init__"): "(self, seq=None, **kwargs)",
    ("property", "__init__"): "(self, fget=None, fset=None, fdel=None, doc=None)",
    # TODO: infer, doc comments have it
    ("dict", "update"): "(self, E=None, **F)", # docstring nearly lies
    (None, "zip"): "(seq1, seq2, *more_seqs)",
    (None, "range"): "(start=None, stop=None, step=None)", # suboptimal: allows empty arglist
    (None, "filter"): "(function_or_none, sequence)",
    (None, "iter"): "(source, sentinel=None)",
    (None, "getattr"): "(object, name, default=None)",
    ('frozenset', "__init__"): "(self, seq=())",
    ("bytearray", "__init__"): "(self, source=None, encoding=None, errors='strict')",
}

if version[0] < 3:
    PREDEFINED_BUILTIN_SIGS[
        ("unicode", "__init__")] = "(self, string=u'', encoding=None, errors='strict')" # overrides a fake
    PREDEFINED_BUILTIN_SIGS[("super", "__init__")] = "(self, type1, type2=None)"
    PREDEFINED_BUILTIN_SIGS[
        (None, "min")] = "(*args, **kwargs)" # too permissive, but py2.x won't allow a better sig
    PREDEFINED_BUILTIN_SIGS[(None, "max")] = "(*args, **kwargs)"
    PREDEFINED_BUILTIN_SIGS[("str", "__init__")] = "(self, string='')" # overrides a fake
    PREDEFINED_BUILTIN_SIGS[(None, "print")] = "(*args, **kwargs)" # can't do better in 2.x
else:
    PREDEFINED_BUILTIN_SIGS[("super", "__init__")] = "(self, type1=None, type2=None)"
    PREDEFINED_BUILTIN_SIGS[(None, "min")] = "(*args, key=None)"
    PREDEFINED_BUILTIN_SIGS[(None, "max")] = "(*args, key=None)"
    PREDEFINED_BUILTIN_SIGS[
        (None, "open")] = "(file, mode='r', buffering=None, encoding=None, errors=None, newline=None, closefd=True)"
    PREDEFINED_BUILTIN_SIGS[
        ("str", "__init__")] = "(self, value='', encoding=None, errors='strict')" # overrides a fake
    PREDEFINED_BUILTIN_SIGS[("str", "format")] = "(self, *args, **kwargs)"
    PREDEFINED_BUILTIN_SIGS[
        ("bytes", "__init__")] = "(self, value=b'', encoding=None, errors='strict')" # overrides a fake
    PREDEFINED_BUILTIN_SIGS[("bytes", "format")] = "(self, *args, **kwargs)"
    PREDEFINED_BUILTIN_SIGS[(None, "print")] = "(self, *args, sep=' ', end='\\n', file=None)" # proper signature

if (2, 6) <= version < (3, 0):
    PREDEFINED_BUILTIN_SIGS[("unicode", "format")] = "(self, *args, **kwargs)"
    PREDEFINED_BUILTIN_SIGS[("str", "format")] = "(self, *args, **kwargs)"

if version == (2, 5):
    PREDEFINED_BUILTIN_SIGS[("unicode", "splitlines")] = "(keepends=None)" # a typo in docstring there

if version >= (2, 7):
    PREDEFINED_BUILTIN_SIGS[
        ("enumerate", "__init__")] = "(self, iterable, start=0)" # dosctring omits this completely.

if version < (3, 3):
    datetime_mod = "datetime"
else:
    datetime_mod = "_datetime"


# NOTE: per-module signature data may be lazily imported
# keyed by (module_name, class_name, method_name). PREDEFINED_BUILTIN_SIGS might be a layer of it.
# value is ("signature", "return_literal")
PREDEFINED_MOD_CLASS_SIGS = {                                       #TODO: user-skeleton
    (BUILTIN_MOD_NAME, None, 'divmod'): ("(x, y)", "(0, 0)"),

    ("binascii", None, "hexlify"): ("(data)", BYTES_LIT),
    ("binascii", None, "unhexlify"): ("(hexstr)", BYTES_LIT),

    ("time", None, "ctime"): ("(seconds=None)", DEFAULT_STR_LIT),

    ("_struct", None, "pack"): ("(fmt, *args)", BYTES_LIT),
    ("_struct", None, "pack_into"): ("(fmt, buffer, offset, *args)", None),
    ("_struct", None, "unpack"): ("(fmt, string)", None),
    ("_struct", None, "unpack_from"): ("(fmt, buffer, offset=0)", None),
    ("_struct", None, "calcsize"): ("(fmt)", INT_LIT),
    ("_struct", "Struct", "__init__"): ("(self, fmt)", None),
    ("_struct", "Struct", "pack"): ("(self, *args)", BYTES_LIT),
    ("_struct", "Struct", "pack_into"): ("(self, buffer, offset, *args)", None),
    ("_struct", "Struct", "unpack"): ("(self, string)", None),
    ("_struct", "Struct", "unpack_from"): ("(self, buffer, offset=0)", None),

    (datetime_mod, "date", "__new__"): ("(cls, year=None, month=None, day=None)", None),
    (datetime_mod, "date", "fromordinal"): ("(cls, ordinal)", "date(1,1,1)"),
    (datetime_mod, "date", "fromtimestamp"): ("(cls, timestamp)", "date(1,1,1)"),
    (datetime_mod, "date", "isocalendar"): ("(self)", "(1, 1, 1)"),
    (datetime_mod, "date", "isoformat"): ("(self)", DEFAULT_STR_LIT),
    (datetime_mod, "date", "isoweekday"): ("(self)", INT_LIT),
    (datetime_mod, "date", "replace"): ("(self, year=None, month=None, day=None)", "date(1,1,1)"),
    (datetime_mod, "date", "strftime"): ("(self, format)", DEFAULT_STR_LIT),
    (datetime_mod, "date", "timetuple"): ("(self)", "(0, 0, 0, 0, 0, 0, 0, 0, 0)"),
    (datetime_mod, "date", "today"): ("(self)", "date(1, 1, 1)"),
    (datetime_mod, "date", "toordinal"): ("(self)", INT_LIT),
    (datetime_mod, "date", "weekday"): ("(self)", INT_LIT),
    (datetime_mod, "timedelta", "__new__"
    ): (
    "(cls, days=None, seconds=None, microseconds=None, milliseconds=None, minutes=None, hours=None, weeks=None)",
    None),
    (datetime_mod, "datetime", "__new__"
    ): (
    "(cls, year=None, month=None, day=None, hour=None, minute=None, second=None, microsecond=None, tzinfo=None)",
    None),
    (datetime_mod, "datetime", "astimezone"): ("(self, tz)", "datetime(1, 1, 1)"),
    (datetime_mod, "datetime", "combine"): ("(cls, date, time)", "datetime(1, 1, 1)"),
    (datetime_mod, "datetime", "date"): ("(self)", "datetime(1, 1, 1)"),
    (datetime_mod, "datetime", "fromtimestamp"): ("(cls, timestamp, tz=None)", "datetime(1, 1, 1)"),
    (datetime_mod, "datetime", "isoformat"): ("(self, sep='T')", DEFAULT_STR_LIT),
    (datetime_mod, "datetime", "now"): ("(cls, tz=None)", "datetime(1, 1, 1)"),
    (datetime_mod, "datetime", "strptime"): ("(cls, date_string, format)", DEFAULT_STR_LIT),
    (datetime_mod, "datetime", "replace" ):
        (
        "(self, year=None, month=None, day=None, hour=None, minute=None, second=None, microsecond=None, tzinfo=None)",
        "datetime(1, 1, 1)"),
    (datetime_mod, "datetime", "time"): ("(self)", "time(0, 0)"),
    (datetime_mod, "datetime", "timetuple"): ("(self)", "(0, 0, 0, 0, 0, 0, 0, 0, 0)"),
    (datetime_mod, "datetime", "timetz"): ("(self)", "time(0, 0)"),
    (datetime_mod, "datetime", "utcfromtimestamp"): ("(self, timestamp)", "datetime(1, 1, 1)"),
    (datetime_mod, "datetime", "utcnow"): ("(cls)", "datetime(1, 1, 1)"),
    (datetime_mod, "datetime", "utctimetuple"): ("(self)", "(0, 0, 0, 0, 0, 0, 0, 0, 0)"),
    (datetime_mod, "time", "__new__"): (
    "(cls, hour=None, minute=None, second=None, microsecond=None, tzinfo=None)", None),
    (datetime_mod, "time", "isoformat"): ("(self)", DEFAULT_STR_LIT),
    (datetime_mod, "time", "replace"): (
    "(self, hour=None, minute=None, second=None, microsecond=None, tzinfo=None)", "time(0, 0)"),
    (datetime_mod, "time", "strftime"): ("(self, format)", DEFAULT_STR_LIT),
    (datetime_mod, "tzinfo", "dst"): ("(self, date_time)", INT_LIT),
    (datetime_mod, "tzinfo", "fromutc"): ("(self, date_time)", "datetime(1, 1, 1)"),
    (datetime_mod, "tzinfo", "tzname"): ("(self, date_time)", DEFAULT_STR_LIT),
    (datetime_mod, "tzinfo", "utcoffset"): ("(self, date_time)", INT_LIT),

    ("_io", None, "open"): ("(name, mode=None, buffering=None)", "file('/dev/null')"),
    ("_io", "FileIO", "read"): ("(self, size=-1)", DEFAULT_STR_LIT),
    ("_fileio", "_FileIO", "read"): ("(self, size=-1)", DEFAULT_STR_LIT),

    ("thread", None, "start_new"): ("(function, args, kwargs=None)", INT_LIT),
    ("_thread", None, "start_new"): ("(function, args, kwargs=None)", INT_LIT),

    ("itertools", "groupby", "__init__"): ("(self, iterable, key=None)", None),
    ("itertools", None, "groupby"): ("(iterable, key=None)", LIST_LIT),

    ("cStringIO", "OutputType", "seek"): ("(self, position, mode=0)", None),
    ("cStringIO", "InputType", "seek"): ("(self, position, mode=0)", None),

    # NOTE: here we stand on shaky ground providing sigs for 3rd-party modules, though well-known
    ("numpy.core.multiarray", "ndarray", "__array__"): ("(self, dtype=None)", None),
    ("numpy.core.multiarray", None, "arange"): ("(start=None, stop=None, step=None, dtype=None)", None),
    # same as range()
    ("numpy.core.multiarray", None, "set_numeric_ops"): ("(**ops)", None),
    ("numpy.random.mtrand", None, "rand"): ("(*dn)", None),
    ("numpy.random.mtrand", None, "randn"): ("(*dn)", None),
    ("numpy.core.multiarray", "ndarray", "reshape"): ("(self, shape, *shapes, order='C')", None),
    ("numpy.core.multiarray", "ndarray", "resize"): ("(self, *new_shape, refcheck=True)", None),
}

bin_collections_names = ['collections', '_collections']

for name in bin_collections_names:
    PREDEFINED_MOD_CLASS_SIGS[(name, "deque", "__init__")] = ("(self, iterable=(), maxlen=None)", None)
    PREDEFINED_MOD_CLASS_SIGS[(name, "defaultdict", "__init__")] = ("(self, default_factory=None, **kwargs)", None)

if version[0] < 3:
    PREDEFINED_MOD_CLASS_SIGS[("exceptions", "BaseException", "__unicode__")] = ("(self)", UNICODE_LIT)
    PREDEFINED_MOD_CLASS_SIGS[("itertools", "product", "__init__")] = ("(self, *iterables, **kwargs)", LIST_LIT)
else:
    PREDEFINED_MOD_CLASS_SIGS[("itertools", "product", "__init__")] = ("(self, *iterables, repeat=1)", LIST_LIT)

if version[0] < 3:
    PREDEFINED_MOD_CLASS_SIGS[("PyQt4.QtCore", None, "pyqtSlot")] = (
    "(*types, **keywords)", None) # doc assumes py3k syntax

# known properties of modules
# {{"module": {"class", "property" : ("letters", ("getter", "type"))}},
# where letters is any set of r,w,d (read, write, del) and "getter" is a source of typed getter.
# if value is None, the property should be omitted.
# read-only properties that return an object are not listed.
G_OBJECT = ("lambda self: object()", None)
G_TYPE = ("lambda self: type(object)", "type")
G_DICT = ("lambda self: {}", "dict")
G_STR = ("lambda self: ''", "string")
G_TUPLE = ("lambda self: tuple()", "tuple")
G_FLOAT = ("lambda self: 0.0", "float")
G_INT = ("lambda self: 0", "int")
G_BOOL = ("lambda self: True", "bool")

KNOWN_PROPS = {
    BUILTIN_MOD_NAME: {
        ("object", '__class__'): ('r', G_TYPE),
        ('complex', 'real'): ('r', G_FLOAT),
        ('complex', 'imag'): ('r', G_FLOAT),
        ("file", 'softspace'): ('r', G_BOOL),
        ("file", 'name'): ('r', G_STR),
        ("file", 'encoding'): ('r', G_STR),
        ("file", 'mode'): ('r', G_STR),
        ("file", 'closed'): ('r', G_BOOL),
        ("file", 'newlines'): ('r', G_STR),
        ("slice", 'start'): ('r', G_INT),
        ("slice", 'step'): ('r', G_INT),
        ("slice", 'stop'): ('r', G_INT),
        ("super", '__thisclass__'): ('r', G_TYPE),
        ("super", '__self__'): ('r', G_TYPE),
        ("super", '__self_class__'): ('r', G_TYPE),
        ("type", '__basicsize__'): ('r', G_INT),
        ("type", '__itemsize__'): ('r', G_INT),
        ("type", '__base__'): ('r', G_TYPE),
        ("type", '__flags__'): ('r', G_INT),
        ("type", '__mro__'): ('r', G_TUPLE),
        ("type", '__bases__'): ('r', G_TUPLE),
        ("type", '__dictoffset__'): ('r', G_INT),
        ("type", '__dict__'): ('r', G_DICT),
        ("type", '__name__'): ('r', G_STR),
        ("type", '__weakrefoffset__'): ('r', G_INT),
    },
    "exceptions": {
        ("BaseException", '__dict__'): ('r', G_DICT),
        ("BaseException", 'message'): ('rwd', G_STR),
        ("BaseException", 'args'): ('r', G_TUPLE),
        ("EnvironmentError", 'errno'): ('rwd', G_INT),
        ("EnvironmentError", 'message'): ('rwd', G_STR),
        ("EnvironmentError", 'strerror'): ('rwd', G_INT),
        ("EnvironmentError", 'filename'): ('rwd', G_STR),
        ("SyntaxError", 'text'): ('rwd', G_STR),
        ("SyntaxError", 'print_file_and_line'): ('rwd', G_BOOL),
        ("SyntaxError", 'filename'): ('rwd', G_STR),
        ("SyntaxError", 'lineno'): ('rwd', G_INT),
        ("SyntaxError", 'offset'): ('rwd', G_INT),
        ("SyntaxError", 'msg'): ('rwd', G_STR),
        ("SyntaxError", 'message'): ('rwd', G_STR),
        ("SystemExit", 'message'): ('rwd', G_STR),
        ("SystemExit", 'code'): ('rwd', G_OBJECT),
        ("UnicodeDecodeError", '__basicsize__'): None,
        ("UnicodeDecodeError", '__itemsize__'): None,
        ("UnicodeDecodeError", '__base__'): None,
        ("UnicodeDecodeError", '__flags__'): ('rwd', G_INT),
        ("UnicodeDecodeError", '__mro__'): None,
        ("UnicodeDecodeError", '__bases__'): None,
        ("UnicodeDecodeError", '__dictoffset__'): None,
        ("UnicodeDecodeError", '__dict__'): None,
        ("UnicodeDecodeError", '__name__'): None,
        ("UnicodeDecodeError", '__weakrefoffset__'): None,
        ("UnicodeEncodeError", 'end'): ('rwd', G_INT),
        ("UnicodeEncodeError", 'encoding'): ('rwd', G_STR),
        ("UnicodeEncodeError", 'object'): ('rwd', G_OBJECT),
        ("UnicodeEncodeError", 'start'): ('rwd', G_INT),
        ("UnicodeEncodeError", 'reason'): ('rwd', G_STR),
        ("UnicodeEncodeError", 'message'): ('rwd', G_STR),
        ("UnicodeTranslateError", 'end'): ('rwd', G_INT),
        ("UnicodeTranslateError", 'encoding'): ('rwd', G_STR),
        ("UnicodeTranslateError", 'object'): ('rwd', G_OBJECT),
        ("UnicodeTranslateError", 'start'): ('rwd', G_INT),
        ("UnicodeTranslateError", 'reason'): ('rwd', G_STR),
        ("UnicodeTranslateError", 'message'): ('rwd', G_STR),
    },
    '_ast': {
        ("AST", '__dict__'): ('rd', G_DICT),
    },
    'posix': {
        ("statvfs_result", 'f_flag'): ('r', G_INT),
        ("statvfs_result", 'f_bavail'): ('r', G_INT),
        ("statvfs_result", 'f_favail'): ('r', G_INT),
        ("statvfs_result", 'f_files'): ('r', G_INT),
        ("statvfs_result", 'f_frsize'): ('r', G_INT),
        ("statvfs_result", 'f_blocks'): ('r', G_INT),
        ("statvfs_result", 'f_ffree'): ('r', G_INT),
        ("statvfs_result", 'f_bfree'): ('r', G_INT),
        ("statvfs_result", 'f_namemax'): ('r', G_INT),
        ("statvfs_result", 'f_bsize'): ('r', G_INT),

        ("stat_result", 'st_ctime'): ('r', G_INT),
        ("stat_result", 'st_rdev'): ('r', G_INT),
        ("stat_result", 'st_mtime'): ('r', G_INT),
        ("stat_result", 'st_blocks'): ('r', G_INT),
        ("stat_result", 'st_gid'): ('r', G_INT),
        ("stat_result", 'st_nlink'): ('r', G_INT),
        ("stat_result", 'st_ino'): ('r', G_INT),
        ("stat_result", 'st_blksize'): ('r', G_INT),
        ("stat_result", 'st_dev'): ('r', G_INT),
        ("stat_result", 'st_size'): ('r', G_INT),
        ("stat_result", 'st_mode'): ('r', G_INT),
        ("stat_result", 'st_uid'): ('r', G_INT),
        ("stat_result", 'st_atime'): ('r', G_INT),
    },
    "pwd": {
        ("struct_pwent", 'pw_dir'): ('r', G_STR),
        ("struct_pwent", 'pw_gid'): ('r', G_INT),
        ("struct_pwent", 'pw_passwd'): ('r', G_STR),
        ("struct_pwent", 'pw_gecos'): ('r', G_STR),
        ("struct_pwent", 'pw_shell'): ('r', G_STR),
        ("struct_pwent", 'pw_name'): ('r', G_STR),
        ("struct_pwent", 'pw_uid'): ('r', G_INT),

        ("struct_passwd", 'pw_dir'): ('r', G_STR),
        ("struct_passwd", 'pw_gid'): ('r', G_INT),
        ("struct_passwd", 'pw_passwd'): ('r', G_STR),
        ("struct_passwd", 'pw_gecos'): ('r', G_STR),
        ("struct_passwd", 'pw_shell'): ('r', G_STR),
        ("struct_passwd", 'pw_name'): ('r', G_STR),
        ("struct_passwd", 'pw_uid'): ('r', G_INT),
    },
    "thread": {
        ("_local", '__dict__'): None
    },
    "xxsubtype": {
        ("spamdict", 'state'): ('r', G_INT),
        ("spamlist", 'state'): ('r', G_INT),
    },
    "zipimport": {
        ("zipimporter", 'prefix'): ('r', G_STR),
        ("zipimporter", 'archive'): ('r', G_STR),
        ("zipimporter", '_files'): ('r', G_DICT),
    },
    "_struct": {
        ("Struct", "size"): ('r', G_INT),
        ("Struct", "format"): ('r', G_STR),
    },
    datetime_mod: {
        ("datetime", "hour"): ('r', G_INT),
        ("datetime", "minute"): ('r', G_INT),
        ("datetime", "second"): ('r', G_INT),
        ("datetime", "microsecond"): ('r', G_INT),
        ("date", "day"): ('r', G_INT),
        ("date", "month"): ('r', G_INT),
        ("date", "year"): ('r', G_INT),
        ("time", "hour"): ('r', G_INT),
        ("time", "minute"): ('r', G_INT),
        ("time", "second"): ('r', G_INT),
        ("time", "microsecond"): ('r', G_INT),
        ("timedelta", "days"): ('r', G_INT),
        ("timedelta", "seconds"): ('r', G_INT),
        ("timedelta", "microseconds"): ('r', G_INT),
    },
}

# Sometimes module X defines item foo but foo.__module__ == 'Y' instead of 'X';
# module Y just re-exports foo, and foo fakes being defined in Y.
# We list all such Ys keyed by X, all fully-qualified names:
# {"real_definer_module": ("fake_reexporter_module",..)}
KNOWN_FAKE_REEXPORTERS = {
    "_collections": ('collections',),
    "_functools": ('functools',),
    "_socket": ('socket',), # .error, etc
    "pyexpat": ('xml.parsers.expat',),
    "_bsddb": ('bsddb.db',),
    "pysqlite2._sqlite": ('pysqlite2.dbapi2',), # errors
    "numpy.core.multiarray": ('numpy', 'numpy.core'),
    "numpy.core._dotblas": ('numpy', 'numpy.core'),
    "numpy.core.umath": ('numpy', 'numpy.core'),
    "gtk._gtk": ('gtk', 'gtk.gdk',),
    "gobject._gobject": ('gobject',),
    "gnomecanvas": ("gnome.canvas",),
}

KNOWN_FAKE_BASES = []
# list of classes that pretend to be base classes but are mere wrappers, and their defining modules
# [(class, module),...] -- real objects, not names
#noinspection PyBroadException
try:
    #noinspection PyUnresolvedReferences
    import sip as sip_module # Qt specifically likes it

    if hasattr(sip_module, 'wrapper'):
        KNOWN_FAKE_BASES.append((sip_module.wrapper, sip_module))
    if hasattr(sip_module, 'simplewrapper'):
        KNOWN_FAKE_BASES.append((sip_module.simplewrapper, sip_module))
    del sip_module
except:
    pass

# This is a list of builtin classes to use fake init
FAKE_BUILTIN_INITS = (tuple, type, int, str)
if version[0] < 3:
    FAKE_BUILTIN_INITS = FAKE_BUILTIN_INITS + (getattr(the_builtins, "unicode"),)
else:
    FAKE_BUILTIN_INITS = FAKE_BUILTIN_INITS + (getattr(the_builtins, "str"), getattr(the_builtins, "bytes"))

# Some builtin methods are decorated, but this is hard to detect.
# {("class_name", "method_name"): "decorator"}
KNOWN_DECORATORS = {
    ("dict", "fromkeys"): "staticmethod",
    ("object", "__subclasshook__"): "classmethod",
    ("bytearray", "fromhex"): "classmethod",
    ("bytes", "fromhex"): "classmethod",
    ("bytearray", "maketrans"): "staticmethod",
    ("bytes", "maketrans"): "staticmethod",
    ("int", "from_bytes"): "classmethod",
    ("float", "fromhex"): "staticmethod",
}

classobj_txt = (                        #TODO: user-skeleton
"class ___Classobj:" "\n"
"    '''A mock class representing the old style class base.'''" "\n"
"    __module__ = ''" "\n"
"    __class__ = None" "\n"
"\n"
"    def __init__(self):" "\n"
"        pass" "\n"
"    __dict__ = {}" "\n"
"    __doc__ = ''" "\n"
)

MAC_STDLIB_PATTERN = re.compile("/System/Library/Frameworks/Python\\.framework/Versions/(.+)/lib/python\\1/(.+)")
MAC_SKIP_MODULES = ["test", "ctypes/test", "distutils/tests", "email/test",
                    "importlib/test", "json/tests", "lib2to3/tests",
                    "bsddb/test",
                    "sqlite3/test", "tkinter/test", "idlelib", "antigravity"]

POSIX_SKIP_MODULES = ["vtemodule", "PAMmodule", "_snackmodule", "/quodlibet/_mmkeys"]

BIN_MODULE_FNAME_PAT = re.compile(r'([a-zA-Z_][0-9a-zA-Z_]*)\.(?:pyc|pyo|(?:(?:[a-zA-Z_0-9\-]+\.)?(?:so|pyd)))$')
# possible binary module filename: letter,    alphanum                    architecture per PEP-3149
TYPELIB_MODULE_FNAME_PAT = re.compile("([a-zA-Z_]+[0-9a-zA-Z]*)[0-9a-zA-Z-.]*\\.typelib")

MODULES_INSPECT_DIR = ['gi.repository']
TENSORFLOW_CONTRIB_OPS_MODULE_PATTERN = re.compile(r'tensorflow\.contrib\.(?:.+)\.(?:python\.ops\.|_dataset_ops$)')

CLASS_ATTR_BLACKLIST = [
    'google.protobuf.pyext._message.Message._extensions_by_name',
    'google.protobuf.pyext._message.Message._extensions_by_number',
    'panda3d.core.ExecutionEnvironment.environment_variables',
]

SKELETON_HEADER_VERSION_LINE = re.compile(r'# by generator (?P<version>\d+\.\d+)')
SKELETON_HEADER_ORIGIN_LINE = re.compile(r'# from (?P<path>.*)')
REQUIRED_GEN_VERSION_LINE = re.compile(r'(?P<name>\S+)\s+(?P<version>\d+\.\d+)')
# "mod_path" and "mod_mtime" markers are used in tests
BLACKLIST_VERSION_LINE = re.compile(r'(?P<path>{mod_path}|[^=]+) = (?P<version>\d+\.\d+) (?P<mtime>{mod_mtime}|\d+)')

ENV_TEST_MODE_FLAG = 'GENERATOR3_TEST_MODE'
ENV_STANDALONE_MODE_FLAG = 'GENERATOR3_STANDALONE_MODE'
ENV_PREGENERATION_MODE_FLAG = "IS_PREGENERATED_SKELETONS"
ENV_VERSION = 'GENERATOR3_VERSION'
ENV_REQUIRED_GEN_VERSION_FILE = 'GENERATOR3_REQUIRED_GEN_VERSION_FILE'

FAILED_VERSION_STAMP_PREFIX = '.failed__'

CACHE_DIR_NAME = 'cache'
