def f():
    bytes, str = <warning descr="Local variable 'str' might be referenced before assignment">str</warning>, unicode #fail

class C(object):
    bytes, str = str, unicode #pass

bytes, str = str, unicode #pass
