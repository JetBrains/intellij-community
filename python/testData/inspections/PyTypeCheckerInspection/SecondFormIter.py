def test_second_form():
    def f():
        return ''

    for chunk in iter(lambda: f(), ''):
        pass


def test_second_form_fail():
    for chunk in iter(<weak_warning descr="Expected type 'Union[Iterable, () -> object]' (matched generic type 'Union[Iterable[TypeVar('T')], () -> object]'), got 'int' instead">10</weak_warning>, ''):
        pass


def test_first_form():
    for x in iter([1, 2, 3]):
        pass
