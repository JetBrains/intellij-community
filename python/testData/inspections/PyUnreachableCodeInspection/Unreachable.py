def f():
    return
    <warning descr="This code is unreachable">a = 1</warning>


def f():
    if 0:
        return
    a = 1 # pass


def f():
    raise Exception()
    <warning descr="This code is unreachable">a = 1</warning>


def f():
    for x in []:
        break
        <warning descr="This code is unreachable">a = x</warning>


def f():
    for x in []:
        if x == 0:
            break
        a = 1 # pass


def f():
    for x in []:
        continue
        <warning descr="This code is unreachable">a = 1</warning>


def f():
    for x in []:
        raise Exception()
        <warning descr="This code is unreachable">if 1:
            pass</warning>


def f():
    if 1:
        return
        <warning descr="This code is unreachable">print "x"</warning>


def f():
    try:
        raise KeyboardInterrupt
    finally:
        print 'test'


class MyTestCase(unittest.TestCase):
    def test_something(self):
        with self.assertRaises():
            raise Foo
        foo() # pass


# PY-3532
def f():
    import sys
    f = lambda: sys.exit() #pass
    foo = 3
    return f, foo


# PY-3886
def f():
    from unittest import TestCase
    class C(TestCase):
        def test_1(self):
            self.fail()
            <warning descr="This code is unreachable">return -42</warning>


# PY-4149
def f():
    try:
        pass
    finally: #pass
        pass


# PY-4208
def f(g):
    try:
        raise ValueError
    finally:
        g()
    <warning descr="This code is unreachable">g()</warning>


# PY-5266
def f(g):
    x = 0
    try:
        x = g()
    except Exception:
        try:
            x = 2
            return
        except Exception:
            raise
    print(x) #pass


# PY-6159
def f(c):
  while c:
      break #pass
  else:
      x = 1


# PY-6062
def f(x):
    for _ in [1, 2]:
        for _ in [3, 4]:
            pass
        else:
            break
    else:
        return
    print(x) #pass


# PY-6062
def f(x):
    for _ in [1, 2]:
        while x:
            pass
        else:
            break
    else:
        return
    print(x) #pass
