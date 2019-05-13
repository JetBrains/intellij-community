class MyClass(object):
    def method(self):
        pass

    @staticmethod
    def static_method():
        pass


# Unbound methods are still treated as __method in Python 2
MyClass.method.__func__
MyClass.method.<warning descr="Cannot find reference '__defaults__' in 'function'">__defaults__</warning>

# Bound method with qualifier
inst = MyClass()
inst.method.__func__
inst.method.<warning descr="Cannot find reference '__defaults__' in 'function'">__defaults__</warning>

# Static method
inst.static_method.<warning descr="Cannot find reference '__func__' in 'function'">__func__</warning>
inst.static_method.__defaults__
MyClass.static_method.<warning descr="Cannot find reference '__func__' in 'function'">__func__</warning>
MyClass.static_method.__defaults__
