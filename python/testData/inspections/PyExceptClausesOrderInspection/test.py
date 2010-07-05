class F(Exception):
    def foo(self):
        raise self

try:
    F().foo()
except BaseException:
    print("BaseException")
except <warning descr="'BaseException', superclass of exception class 'Exception', has already been caught">Exception</warning>:
    print("Exception")

try:
  pass
except Exception as e:
  pass
except <warning descr="Exception class 'Exception' has already been caught">Exception</warning> as ex:
  pass