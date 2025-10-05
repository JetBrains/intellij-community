def foo() -> int:
    if foo():
        return -1
    else:
        return 1


return foo()


bar