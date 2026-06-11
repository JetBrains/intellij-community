def test_second_form():
    def f():
        return ''

    for chunk in iter(lambda: f(), ''):
        pass


def test_second_form_fail():
    for chunk in iter<warning descr="No overload of 'iter' matches the arguments. Argument types: (int, str). Expected one of: (__function: () -> Optional[_T], __sentinel: None), (__function: () -> _T, __sentinel: Any)">(10, '')</warning>:
        pass


def test_first_form():
    for x in iter([1, 2, 3]):
        pass
