def test_second_form():
    def f():
        return ''

    for chunk in iter(lambda: f(), ''):
        pass


def test_second_form_fail():
    for chunk in iter(<weak_warning descr="Expected type '() -> Any' (matched generic type '() -> TypeVar('_T')'), got 'int' instead">10</weak_warning>, ''):
        pass


def test_first_form():
    for x in iter([1, 2, 3]):
        pass
