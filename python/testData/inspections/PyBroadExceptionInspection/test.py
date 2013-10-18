class ExceptionKlass(Exception):
    pass

try:
    function_throws_exception()
except <weak_warning descr="Too broad exception clause">Exception</weak_warning>:
    pass

try:
    function_throws_exception()
except <weak_warning descr="Too broad exception clause">BaseException</weak_warning>:
    pass

try:
    function_throws_exception()
except ExceptionKlass:
    pass

try:
    function_throws_exception()
<weak_warning descr="Too broad exception clause">except</weak_warning>:
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