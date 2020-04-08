import re
import sys

from generator3.constants import STR_TYPES
from generator3.util_methods import sanitize_value
from six import PY2

if PY2:
    from generator3._vendor.pyparsing_py2 import *
else:
    # noinspection PyUnresolvedReferences
    from generator3._vendor.pyparsing_py3 import *

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

tupleStr << (Suppress("(") + Optional(delimitedList(listItem)) +
             Optional(Literal(",")) + Suppress(")")).setResultsName("tuple")

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

ident = Word(alphas + "_", alphanums + "_-.").setName("ident")  # we accept things like "foo-or-bar"
decorated_ident = ident + Optional(Suppress(SP + Literal(":") + SP + ident))  # accept "foo: bar", ignore "bar"
spaced_ident = Combine(
    decorated_ident + ZeroOrMore(Literal(' ') + decorated_ident))  # we accept 'list or tuple' or 'C struct'

# allow quoted names, because __setattr__, etc docs use it
paramname = spaced_ident | \
            APOS + spaced_ident + APOS | \
            QUOTE + spaced_ident + QUOTE

parenthesized_tuple = (Literal("(") + Optional(delimitedList(listItem, combine=True)) +
                       Optional(Literal(",")) + Literal(")")).setResultsName("(tuple)")

initializer = (SP + Suppress("=") + SP + Combine(parenthesized_tuple | listItem | ident)).setName(
    "=init")  # accept foo=defaultfoo

param = Group(Empty().setParseAction(replaceWith(T_SIMPLE)) + Combine(Optional(oneOf("* **")) + paramname) + Optional(
    initializer))

ellipsis = Group(
    Empty().setParseAction(replaceWith(T_SIMPLE)) + \
    (Literal("..") +
     ZeroOrMore(Literal('.'))).setParseAction(replaceWith(TRIPLE_DOT))  # we want to accept both 'foo,..' and 'foo, ...'
)

paramSlot = Forward()

simpleParamSeq = ZeroOrMore(paramSlot + COMMA) + Optional(paramSlot + Optional(COMMA))
nestedParamSeq = Group(
    Suppress('(').setParseAction(replaceWith(T_NESTED)) + \
    simpleParamSeq + Optional(ellipsis + Optional(COMMA) + Optional(simpleParamSeq)) + \
    Suppress(')')
)  # we accept "(a1, ... an)"

paramSlot << (param | nestedParamSeq)

optionalPart = Forward()

paramSeq = simpleParamSeq + Optional(optionalPart)  # this is our approximate target

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
paramSeqAndRest = paramSeq + Suppress(')') + Optional(return_type) + Suppress(Optional(Regex(r"(?s).*")))


def transform_seq(results, toplevel=True):
    """Transforms a tree of ParseResults into a param spec string."""
    is_clr = sys.platform == "cli"
    ret = [] # add here token to join
    for token in results:
        token_type = token[0]
        if token_type is T_SIMPLE:
            token_name = token[1]
            if len(token) == 3: # name with value
                if toplevel:
                    ret.append(sanitize_ident(token_name, is_clr) + "=" + sanitize_value(token[2]))
                else:
                    # smth like "a, (b1=1, b2=2)", make it "a, p_b"
                    return ["p_" + results[0][1]] # NOTE: for each item of tuple, return the same name of its 1st item.
            elif token_name == TRIPLE_DOT:
                if toplevel and not has_item_starting_with(ret, "*"):
                    ret.append("*more")
                else:
                    # we're in a "foo, (bar1, bar2, ...)"; make it "foo, bar_tuple"
                    return extract_alpha_prefix(results[0][1]) + "_tuple"
            else: # just name
                ret.append(sanitize_ident(token_name, is_clr))
        elif token_type is T_NESTED:
            inner = transform_seq(token[1:], False)
            if len(inner) != 1:
                ret.append(inner)
            else:
                ret.append(inner[0]) # [foo] -> foo
        elif token_type is T_OPTIONAL:
            ret.extend(transform_optional_seq(token))
        elif token_type is T_RETURN:
            pass # this is handled elsewhere
        else:
            raise Exception("This cannot be a token type: " + repr(token_type))
    return ret


def transform_optional_seq(results):
    """
    Produces a string that describes the optional part of parameters.
    @param results must start from T_OPTIONAL.
    """
    assert results[0] is T_OPTIONAL, "transform_optional_seq expects a T_OPTIONAL node, sees " + \
                                     repr(results[0])
    is_clr = sys.platform == "cli"
    ret = []
    for token in results[1:]:
        token_type = token[0]
        if token_type is T_SIMPLE:
            token_name = token[1]
            if len(token) == 3: # name with value; little sense, but can happen in a deeply nested optional
                ret.append(sanitize_ident(token_name, is_clr) + "=" + sanitize_value(token[2]))
            elif token_name == '...':
                # we're in a "foo, [bar, ...]"; make it "foo, *bar"
                return ["*" + extract_alpha_prefix(
                    results[1][1])] # we must return a seq; [1] is first simple, [1][1] is its name
            else: # just name
                ret.append(sanitize_ident(token_name, is_clr) + "=None")
        elif token_type is T_OPTIONAL:
            ret.extend(transform_optional_seq(token))
            # maybe handle T_NESTED if such cases ever occur in real life
            # it can't be nested in a sane case, really
    return ret


def has_item_starting_with(p_seq, p_start):
    for item in p_seq:
        if isinstance(item, STR_TYPES) and item.startswith(p_start):
            return True
    return False


def sanitize_ident(x, is_clr=False):
    """Takes an identifier and returns it sanitized"""
    if x in ("class", "object", "def", "list", "tuple", "int", "float", "str", "unicode" "None"):
        return "p_" + x
    else:
        if is_clr:
            # it tends to have names like "int x", turn it to just x
            xs = x.split(" ")
            if len(xs) == 2:
                return sanitize_ident(xs[1])
        return x.replace("-", "_").replace(" ", "_").replace(".", "_") # for things like "list-or-tuple" or "list or tuple"


def extract_alpha_prefix(p_string, default_prefix="some"):
    """Returns 'foo' for things like 'foo1' or 'foo2'; if prefix cannot be found, the default is returned"""
    match = NUM_IDENT_PATTERN.match(p_string)
    prefix = match and match.groups()[match.lastindex - 1] or None
    return prefix or default_prefix


NUM_IDENT_PATTERN = re.compile("([A-Za-z_]+)[0-9]?[A-Za-z_]*") # 'foo_123' -> $1 = 'foo_'