def f(lst):
    print("Called")
    lst.reverse()
    print("Reversed")
    lst.append(42)
    print("Appended")
    return lst


L = [1, 2, 3]
counter = 2
while counter > 0:
    len(f(f((f(L)))))  # breakpoint
    counter -= 1
