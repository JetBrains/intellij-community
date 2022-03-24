from typing import Any

class JMESPathError(ValueError): ...

class ParseError(JMESPathError):
    lex_position: Any
    token_value: Any
    token_type: Any
    msg: Any
    expression: Any
    def __init__(self, lex_position, token_value, token_type, msg=...) -> None: ...

class IncompleteExpressionError(ParseError):
    expression: Any
    lex_position: Any
    token_type: Any
    token_value: Any
    def set_expression(self, expression) -> None: ...

class LexerError(ParseError):
    lexer_position: Any
    lexer_value: Any
    message: Any
    expression: Any
    def __init__(self, lexer_position, lexer_value, message, expression: Any | None = ...) -> None: ...

class ArityError(ParseError):
    expected_arity: Any
    actual_arity: Any
    function_name: Any
    expression: Any
    def __init__(self, expected, actual, name) -> None: ...

class VariadictArityError(ArityError): ...

class JMESPathTypeError(JMESPathError):
    function_name: Any
    current_value: Any
    actual_type: Any
    expected_types: Any
    def __init__(self, function_name, current_value, actual_type, expected_types) -> None: ...

class EmptyExpressionError(JMESPathError):
    def __init__(self) -> None: ...

class UnknownFunctionError(JMESPathError): ...
