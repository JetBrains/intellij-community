class A():
    def __init__(self):
        invoke1(self)
        invoke2(self)

    def method1(self):
        pass

    def method2(self):
        pass

def invoke1(p):
    p.method1()


def invoke2(p):
    p.method2()


def invokeA():
    a = A()
    a.method1()
    a.method2()

    def new_class_func():
        class C():
            def bar(self):
                invokeA(A())
        return C()

a = A()
A.__init_<caret>_(a)