import sys

var = (sys.platform == 'win32')

class A():
    def __init__(self):
        self.<caret>a = None

    if var:
        def func(self):
            self.a = ""
    else:
        def func(self):
            self.a = ()