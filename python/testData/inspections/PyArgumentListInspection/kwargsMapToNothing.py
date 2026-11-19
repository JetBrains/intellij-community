def f5(a, b, c):
    pass

arg = [1, 2, 3]
kwarg = {'c':3}
f5(*arg, **kwarg) # ok
f5(1,2, **kwarg) # ok
f5(1, 2, 3, **kwarg) # not reported: the type of kwarg allows an empty dict
f5(1, 2, 3, *arg) # not reported: the type of arg allows an empty list
f5(1, 2, 3, <warning descr="Unexpected argument">**{'c': 3}</warning>) # fail
f5(1, 2, 3, *(<warning descr="Unexpected argument">4</warning>,)) # fail
