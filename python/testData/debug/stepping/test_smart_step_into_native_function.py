def f(lst):
    lst.reverse()
    lst.append(42)
    return lst


L = [1, 2, 3]
counter = 2
while counter > 0:
    len(f(f((f(L)))))  # breakpoint
    counter -= 1
