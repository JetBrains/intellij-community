class B:
    def __init__(self, auno=True): pass

class C(B):
    def __init__(self, **kwargs): pass

c = C(au<caret>)
