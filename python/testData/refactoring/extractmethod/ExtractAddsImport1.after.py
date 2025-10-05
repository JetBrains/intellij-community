def f(a):
    compiled = compile("x = 42", "<string>", "exec")
    body(compiled)


def body(compiled_new):
    1
    compiled_new
