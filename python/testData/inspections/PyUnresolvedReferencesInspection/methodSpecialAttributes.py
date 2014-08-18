class MyClass(object):
    def method(self):
        pass

    @staticmethod
    def static_method():
        pass


# Unbound method still treated as __method in Python 2
MyClass.method.__func__
MyClass.method.<warning descr="Cannot find reference '__defaults__' in 'function'">__defaults__</warning>

# Bound method with qualifier
inst = MyClass()
inst.method.__func__
inst.method.<warning descr="Cannot find reference '__defaults__' in 'function'">__defaults__</warning>

# Reassigned bound method without qualifier
m = inst.method

# Static method
# This reference should be marked as unresolved, but such warnings are suppressed for methods with decorators
inst.static_method.__func__
inst.static_method.__defaults__
