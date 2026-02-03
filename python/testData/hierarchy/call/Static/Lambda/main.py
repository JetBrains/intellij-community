from file_1 import *


def target_func(x=func1, y=func2(), z=lambda: func3, w=lambda: func4()):
    p1 = lambda: func5()
    p2 = lambda: func6
    p1(), p2()
    def inner(ix=func7, iy=func8(), iz=lambda: func9, iw=lambda: func10()):
        func11()
        ip = lambda: func12()
        ip()
    func13()
    inner(func14, func15(), lambda: func16, lambda: func17())

    return func18


target_<caret>func()