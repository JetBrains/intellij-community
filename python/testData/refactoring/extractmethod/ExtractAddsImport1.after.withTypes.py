from types import CodeType


def f(a):
    compiled = compile("x = 42", "<string>", "exec")
    body(compiled)


def body(compiled_new: CodeType):
    1
    compiled_new
