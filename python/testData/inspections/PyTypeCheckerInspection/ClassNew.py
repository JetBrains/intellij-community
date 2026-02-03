class C(object):
    def __new__(cls, name):
        """
        :type name: int
        """
        return super(cls, C).__new__(cls)


C(<warning descr="Expected type 'int', got 'str' instead">'10'</warning>)
