print(<warning descr="Python versions 3.0, 3.1 do not support <>, use != instead.">a <> b</warning>)

if <warning descr="Python versions 3.0, 3.1 do not support <>, use != instead.">a <> 2</warning>:
  var = a
########################

print <warning descr="Python versions >= 3.0 do not support this syntax. The print statement has been replaced with a print() function">"One value"</warning>

########################

#PY-2719
class BaseC<warning descr="Python version 2.4 does not support this syntax.">()</warning>:
    pass

########################

<warning descr="Python version 2.4 doesn't support this syntax.">with A() as a, <warning descr="Python versions 2.5, 2.6, 3.0 do not support multiple context managers">B() as b</warning>:
    suite

</warning>########################
a = <warning descr="Python versions 3.0, 3.1 do not support backquotes, use repr() instead">`imp.acquire_lock()`</warning>

########################
var = [x for x in <warning descr="Python versions 3.0, 3.1 do not support this syntax in list comprehensions.">1, 2, 3</warning>]

########################
class A:
  def cmp(self):
    pass

a = A()
a.cmp()

########################

<warning descr="Python versions 3.1 have no method cmp">cmp()</warning>
<warning descr="Python versions 3.0, 3.1 have no method reduce">reduce()</warning>
<warning descr="Python versions 2.4 have no method all">all()</warning>

bytearray()
next()
<warning descr="Python versions 2.4, 2.5, 3.0, 3.1 have no method buffer">buffer()</warning>

########################
try:
  a
except :
  <warning descr="Python versions 3.0, 3.1 do not support this syntax.">raise ImportError, ImportWarning</warning>


try:
  a
<warning descr="Python versions 3.0, 3.1 do not this syntax.">except ImportError, ImportWarning:
  b



</warning>########################

var = <warning descr="Python versions 2.4, 2.5, 2.6, 3.0 do not support dictionary comprehensions">{i : chr(65+i) for i in range(4)}</warning>

########################

import <warning descr="Python versions 3.0, 3.1 do not have module Bastion">Bastion</warning>
var = Bastion.BastionClass()

########################
def foo():    # PY-2796
  <warning descr="Python version 2.4 doesn't support this syntax. In Python <= 2.4, yield was a statement; it didn't return any value.">a = (yield 1)</warning>

########################

<warning descr="Python versions 3.0 3.1  do not support this syntax. Raise with no arguments can only be used in an except block">raise</warning>

########################
# PY-2792
<warning descr="Python version 2.4 doesn't support this syntax.">x = True if condition else False</warning>

########################
# PY-2792

def unified_tef():
    <warning descr="Python version 2.4 doesn't support this syntax. You could use a finally block to ensure that code is always executed, or one or more except blocks to catch specific exceptions.">try:
        pass
    except ImportError:
        pass
    except KeyError:
        pass
    else:
        pass
    finally:
        pass

</warning>########################
# PY-2797

<warning descr="Python version 2.4 doesn't support this syntax.">with open("x.txt") as f:
    data = f.read()
</warning>########################

<warning descr="Python version 2.4 doesn't support this syntax.">from .module import name1, name2</warning>