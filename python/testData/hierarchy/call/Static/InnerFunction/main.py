def bar():
    pass

def target_func():
    def foo():
        return bar()
    foo()

target_<caret>func()