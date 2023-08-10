from typing import Any, NamedTuple

operators: Any
escapes: Any
name_re: Any
dotted_name_re: Any
division_re: Any
regex_re: Any
line_re: Any
line_join_re: Any
uni_escape_re: Any

class Token(NamedTuple):
    type: Any
    value: Any
    lineno: Any

def get_rules(jsx, dotted, template_string): ...
def indicates_division(token): ...
def unquote_string(string): ...
def tokenize(source, jsx: bool = ..., dotted: bool = ..., template_string: bool = ...) -> None: ...
