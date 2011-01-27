class ExceptionKlass(Exception):
    pass

try:
    function_throws_exception()
except <warning descr="Too broad exception clause">Exception</warning>:
    pass

try:
    function_throws_exception()
except ExceptionKlass:
    pass

try:
    function_throws_exception()
<warning descr="Too broad exception clause">except</warning>:
    pass

class Exception:
    pass

try:
    function_throws_exception()
except Exception:
    pass

try:
  doSomething()
except:
  someCleanup()
  raise

result = []

## PY-2698
try:
  function_throws_exception()
except Exception, e:
  result.append(e)