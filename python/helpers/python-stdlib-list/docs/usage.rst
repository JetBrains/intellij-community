Usage (Or: How To Get The List of Libraries)
============================================

The primary function that you'll care about in this package is ``stdlib_list.stdlib_list``.

In particular:

::

    In [1]: from stdlib_list import stdlib_list

    In [2]: libs = stdlib_list("3.4")

    In [3]: libs[:6]
    Out[3]: ['__future__', '__main__', '_dummy_thread', '_thread', 'abc', 'aifc']


.. automodule:: stdlib_list
    :members: stdlib_list
