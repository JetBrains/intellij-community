<warning descr="Python version 2.4 doesn't support this syntax. You could use a finally block to ensure that code is always executed, or one or more except blocks to catch specific exceptions.">try:
    do_smth()
except ImportError:
    do()
except KeyError:
    do_1()
finally:
    quit()</warning>