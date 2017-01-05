try:
    do_smth()
<warning descr="Python versions < 2.6 do not support this syntax.">except ImportError as e:
    do()</warning>

try:
    do_smth()
<warning descr="Python version 3.0, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 do not support this syntax.">except ImportError, ImportWarning:
    do()</warning>