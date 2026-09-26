# -*- coding: utf-8 -*-

from __future__ import absolute_import


class ThriftParserError(Exception):
    pass


class ThriftLexerError(ThriftParserError):
    pass


class ThriftGrammerError(ThriftParserError):
    pass
