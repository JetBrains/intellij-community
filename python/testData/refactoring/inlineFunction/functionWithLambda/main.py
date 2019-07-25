def foo(lst):
    a = 1
    res = map(lambda x: x + a + 1, lst)
    return list(res)


a = 2

x = fo<caret>o([1, 2])