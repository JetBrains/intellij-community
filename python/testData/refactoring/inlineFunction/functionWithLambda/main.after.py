def foo(lst):
    a = 1
    res = map(lambda x: x + a + 1, lst)
    return list(res)


a = 2

lst = [1, 2]
a1 = 1
res = map(lambda x: x + a1 + 1, lst)
x = list(res)