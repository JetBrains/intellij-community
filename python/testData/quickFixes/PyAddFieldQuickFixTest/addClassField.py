__author__ = 'ktisha'

class A(object):

    def method(self):
        var = A.<warning descr="Unresolved attribute reference 'FIELD' for class 'A'"><caret>FIELD</warning>
        var = self.<warning descr="Unresolved attribute reference 'test' for class 'A'">test</warning>

