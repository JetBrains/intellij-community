from file_1 import *

def target_func():
    def inner(*args):
        return func1()

    return inner(func1(func2(func3(func4, func5()), func6(), (((func7)))()), func8), func9, func10())


target_<caret>func()