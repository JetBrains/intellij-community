try:
    do_smth()
<warning descr="Python version 2.4, 2.5 do not support this syntax.">except ImportError as e:
    do()</warning>

try:
    do_smth()
<warning descr="Python version 3.0, 3.1, 3.2, 3.3, 3.4, 3.5 do not support this syntax.">except ImportError, ImportWarning:
    do()</warning>