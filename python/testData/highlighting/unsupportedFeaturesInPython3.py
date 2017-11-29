print(<error descr="Python version 3.0 does not support <>, use != instead.">a <> 3</error>)
<error descr="Python version 3.0 does not support backquotes, use repr() instead">`foo()`</error>
a = <error descr="Python version 3.0 does not support a trailing 'l' or 'L'.">123l</error>
a = <error descr="Python version 3.0 does not support this syntax. It requires '0o' prefix for octal literals">043</error>
a = 0X43
a = 0b1
a = 0.0
s = <error descr="Python version 3.0 does not support a 'U' prefix">u</error>"text"
<error descr="Python version 3.0 does not support this syntax.">raise a, b, c</error>
<error descr="Python version 3.0 does not support this syntax.">raise a, b</error>

try:
  pass
<error descr="Python version 3.0 does not support this syntax.">except a, name:
  pass</error>

[x * 2 for x in <error descr="Python version 3.0 does not support this syntax in list comprehensions.">vec1, vec2</error>]

<error descr="Python version 3.0 does not have module __builtin__">import __builtin__</error>

<error descr="No exception to reraise">raise</error>

try:
    pass
except:
    raise
    