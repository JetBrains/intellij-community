class klass:
    def foo(self):
        pass

var = klass()
<warning descr="Statement seems to have no effect and can be replaced with function call to have effect">var.f<caret>oo</warning>
