class ExceptionKlass(Exception):
    pass

try:
    function_throws_exception()
<warning descr="Too broad exception clause">except Exception:
    pass</warning>

try:
    function_throws_exception()
except ExceptionKlass:
    pass

try:
    function_throws_exception()
<warning descr="Too broad exception clause">except:
    pass</warning>

class Exception:
    pass

try:
    function_throws_exception()
except Exception:
    pass