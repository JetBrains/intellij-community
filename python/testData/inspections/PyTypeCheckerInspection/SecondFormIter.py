def test_second_form():
    def f():
        return ''

    for chunk in iter(lambda: f(), ''):
        pass


def test_second_form_fail():
    for chunk in iter<warning descr="Unexpected type(s):(int, str)Possible type(s):(() -> Optional[_T], None)(() -> _T, Any)">(10, '')</warning>:
        pass


def test_first_form():
    for x in iter([1, 2, 3]):
        pass
