# -*- coding: utf-8 -*-

from __future__ import absolute_import

from .exc import ThriftLexerError


literals = ':;,=*{}()<>[]'


thrift_reserved_keywords = (
    'BEGIN',
    'END',
    '__CLASS__',
    '__DIR__',
    '__FILE__',
    '__FUNCTION__',
    '__LINE__',
    '__METHOD__',
    '__NAMESPACE__',
    'abstract',
    'alias',
    'and',
    'args',
    'as',
    'assert',
    'begin',
    'break',
    'case',
    'catch',
    'class',
    'clone',
    'continue',
    'declare',
    'def',
    'default',
    'del',
    'delete',
    'do',
    'dynamic',
    'elif',
    'else',
    'elseif',
    'elsif',
    'end',
    'enddeclare',
    'endfor',
    'endforeach',
    'endif',
    'endswitch',
    'endwhile',
    'ensure',
    'except',
    'exec',
    'finally',
    'float',
    'for',
    'foreach',
    'from',
    'function',
    'global',
    'goto',
    'if',
    'implements',
    'import',
    'in',
    'inline',
    'instanceof',
    'interface',
    'is',
    'lambda',
    'module',
    'native',
    'new',
    'next',
    'nil',
    'not',
    'or',
    'pass',
    'public',
    'print',
    'private',
    'protected',
    'public',
    'raise',
    'redo',
    'rescue',
    'retry',
    'register',
    'return',
    'self',
    'sizeof',
    'static',
    'super',
    'switch',
    'synchronized',
    'then',
    'this',
    'throw',
    'transient',
    'try',
    'undef',
    'union',
    'unless',
    'unsigned',
    'until',
    'use',
    'var',
    'virtual',
    'volatile',
    'when',
    'while',
    'with',
    'xor',
    'yield'
)


keywords = (
    'namespace',
    'include',
    'cpp_include',
    'void',
    'bool',
    'byte',
    'i8',
    'i16',
    'i32',
    'i64',
    'double',
    'string',
    'binary',
    'map',
    'list',
    'set',
    'oneway',
    'typedef',
    'struct',
    'union',
    'exception',
    'extends',
    'throws',
    'service',
    'enum',
    'const',
    'required',
    'optional',
)


tokens = (
    'BOOLCONSTANT',
    'INTCONSTANT',
    'DUBCONSTANT',
    'LITERAL',
    'IDENTIFIER',
) + tuple(map(lambda kw: kw.upper(), keywords))


t_ignore = ' \t\r'   # whitespace


def t_error(t):
    raise ThriftLexerError('Illegal character %r at line %d' %
                           (t.value[0], t.lineno))


def t_newline(t):
    r'\n+'
    t.lexer.lineno += len(t.value)


def t_ignore_SILLYCOMM(t):
    r'\/\*\**\*\/'
    t.lexer.lineno += t.value.count('\n')


def t_ignore_MULTICOMM(t):
    r'\/\*[^*]\/*([^*/]|[^*]\/|\*[^/])*\**\*\/'
    t.lexer.lineno += t.value.count('\n')


def t_ignore_DOCTEXT(t):
    r'\/\*\*([^*/]|[^*]\/|\*[^/])*\**\*\/'
    t.lexer.lineno += t.value.count('\n')


def t_ignore_UNIXCOMMENT(t):
    r'\#[^\n]*'


def t_ignore_COMMENT(t):
    r'\/\/[^\n]*'


def t_BOOLCONSTANT(t):
    r'\btrue\b|\bfalse\b'
    t.value = t.value == 'true'
    return t


def t_DUBCONSTANT(t):
    r'[+-]?((\d+(?=\.|[Ee])(\.\d*)?)|(\.\d+))([Ee][+-]?\d+)?'
    t.value = float(t.value)
    return t


def t_HEXCONSTANT(t):
    r'0x[0-9A-Fa-f]+'
    t.value = int(t.value, 16)
    t.type = 'INTCONSTANT'
    return t


def t_INTCONSTANT(t):
    r'[+-]?[0-9]+'
    t.value = int(t.value)
    return t


def t_LITERAL(t):
    r'(\"([^\\\n]|(\\.))*?\")|\'([^\\\n]|(\\.))*?\''
    s = t.value[1:-1]
    maps = {
        't': '\t',
        'r': '\r',
        'n': '\n',
        '\\': '\\',
        '\'': '\'',
        '"': '\"'
    }
    i = 0
    length = len(s)
    val = ''
    while i < length:
        if s[i] == '\\':
            i += 1
            if s[i] in maps:
                val += maps[s[i]]
            else:
                msg = 'Unexcepted escaping characher: %s' % s[i]
                raise ThriftLexerError(msg)
        else:
            val += s[i]

        i += 1

    t.value = val
    return t


def t_IDENTIFIER(t):
    r'[a-zA-Z_](\.[a-zA-Z_0-9]|[a-zA-Z_0-9])*'

    if t.value in keywords:
        t.type = t.value.upper()
        return t
    if t.value in thrift_reserved_keywords:
        raise ThriftLexerError('Cannot use reserved language keyword: %r'
                               ' at line %d' % (t.value, t.lineno))
    return t
