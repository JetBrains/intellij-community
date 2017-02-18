import csv

class A(object):
    pass

csv.register_dialect(<warning descr="Expected type 'Union[str, unicode]', got 'A' instead">A()</warning>, <warning descr="Expected type 'Dialect', got 'A' instead">A()</warning>, delimiter=':', quoting=csv.QUOTE_NONE)