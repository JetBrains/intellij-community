def decorator(*args, **kwargs):
    print(args, kwargs)

    def wrapper(f):
        return f

    return wrapper


@decorator([my_ref for my_ref in range(10)])
def test_decorator(r):
    foo = 1

    @decorator(foo, <error descr="Unresolved reference 'bar'">bar</error>)
    def inner():
        pass

    for my_ref in range(r):
        assert my_ref >= 0

    return my_ref


print(test_decorator(10))
print(<error descr="Unresolved reference 'my_ref'">my_ref</error>)
