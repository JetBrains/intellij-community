from typing import Any

from jmespath.exceptions import EmptyExpressionError as EmptyExpressionError, LexerError as LexerError

class Lexer:
    START_IDENTIFIER: Any
    VALID_IDENTIFIER: Any
    VALID_NUMBER: Any
    WHITESPACE: Any
    SIMPLE_TOKENS: Any
    def tokenize(self, expression) -> None: ...
