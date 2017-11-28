def foo(a, p):
    pass


foo<warning descr="Unexpected argument(s)">(1, p=2, <error descr="Keyword argument repeated">p=3<caret>3</error>)</warning>
