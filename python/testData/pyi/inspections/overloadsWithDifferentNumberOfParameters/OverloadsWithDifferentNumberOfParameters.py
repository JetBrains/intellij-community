from m1 import f, g, h


def test_different_number_of_parameters():
    f(5)
    f(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>)

    f(5, "a")
    f(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>, "b")
    f(5, <warning descr="Expected type 'str', got 'int' instead">6</warning>)
    f(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>, <warning descr="Expected type 'str', got 'int' instead">5</warning>)


def test_same_number_of_parameters_but_one_is_default():
    g(5)
    g(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>)

    g<warning descr="No overload of 'g' matches the arguments. Argument types: (str, bool). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", False)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (int, int). Expected one of: (i: int, b: bool), (i: int, s: str)">(5, 6)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (bool, int). Expected one of: (i: int, b: bool), (i: int, s: str)">(False, 5)</warning>

    g(5, "a")
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (str, str). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", "b")</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (int, int). Expected one of: (i: int, b: bool), (i: int, s: str)">(5, 6)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (str, int). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", 5)</warning>


def test_different_number_of_parameters_one_is_default():
    h(5)
    h(<warning descr="No overload of 'h' matches the arguments. Argument types: ((x: Any) -> Any). Expected one of: (i: int), (i: str)">lambda x: x</warning>)

    h("a")

    h("a", False)
    h(<warning descr="Expected type 'str', got 'int' instead">5</warning>, False)  # fail
    h("a", <warning descr="Expected type 'bool', got 'int' instead">5</warning>)  # fail
    h(<warning descr="Expected type 'str', got 'bool' instead">False</warning>, <warning descr="Expected type 'bool', got 'str' instead">"a"</warning>)  # fail