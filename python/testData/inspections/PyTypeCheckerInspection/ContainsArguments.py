class C(object):
    def __contains__(self, item):
        """
        :type item: int
        """
        return False


def test():
    c = C()
    i = 10
    s = 'string'
    c in i
    i in c
    <warning descr="Expected type 'int', got 'str' instead">s</warning> in c
