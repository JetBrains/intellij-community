def foo(**kwargs):
    x = kwargs.get('x_param', None)
    run(x_param)

def bar():
    foo(x_<caret>)