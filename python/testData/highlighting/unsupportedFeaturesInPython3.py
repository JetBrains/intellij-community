print(<error descr="Python version 3.4 does not support <>, use != instead">a <> 3</error>)
<error descr="Python version 3.4 does not support backquotes, use repr() instead">`foo()`</error>
a = <error descr="Python version 3.4 does not support a trailing 'l'">123l</error>
a = <error descr="Python version 3.4 does not support this syntax. It requires '0o' prefix for octal literals">043</error>
a = 0X43
a = 0X43
a = 0x43
a = 0O43
a = 0o43
a = 0B1
a = 0b1
a = 0.0
s = u"text"
<error descr="Python version 3.4 does not support this syntax">raise a, b, c</error>
<error descr="Python version 3.4 does not support this syntax">raise a, b</error>

try:
  pass
<error descr="Python version 3.4 does not support this syntax">except a, name:
  pass</error>

[x * 2 for x in <error descr="Python version 3.4 does not support this syntax in list comprehensions">vec1, vec2</error>]

<error descr="Python version 3.4 does not have module __builtin__">import __builtin__</error>

<error descr="No exception to reraise">raise</error>

try:
    pass
except:
    raise

def exception_handler():
    if undefined:
        raise
    log_somehow()
    