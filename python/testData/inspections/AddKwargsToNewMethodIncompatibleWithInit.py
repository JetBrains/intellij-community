class C(object):
    def __init__<warning descr="Signature is not compatible to __new__">(self, x, y)</warning>:
        pass

    def __new__<warning descr="Signature is not compatible to __init__">(c<caret>ls)</warning>:
        pass
