print(<warning descr="<> is not supported in Python 3, use != instead">a <> 3</warning>)
<warning descr="Backquote is not supported in Python 3, use repr() instead">`foo()`</warning>
x = <warning descr="Method 'raw_input' has been removed, use 'input' instead">raw_input</warning>()
a = <warning descr="Integer literals do not support a trailing 'l' or 'L' in Python 3">123l</warning>
a = <warning descr="Python 3 requires '0o' prefix for octal literals">043</warning>
a = 0X43
a = 0b1
a = 0.0
s = <warning descr="String literals do not support a leading 'u' or 'U' in Python 3">u"text"</warning>
<warning descr="Python 3 does not support this syntax">raise a, b, c</warning>
<warning descr="Python 3 does not support this syntax">raise a, b</warning>

try:
  pass
<warning descr="Python 3 does not support this syntax">except a, name:
  pass</warning>

[x * 2 for x in <warning descr="List comprehensions do not support this syntax in Python 3">vec1, vec2</warning>]

<warning descr="Module __builtin__ renamed to builtins">import __builtin__</warning>
