from m1 import f, g, h

def test_different_number_of_parameters():
    f(5)
    f(<warning descr="Expected type 'int', got 'Literal[\"a\"]' instead">"a"</warning>)
    f(<warning descr="Expected type 'int', got 'Literal[\"a\"]' instead">"a"</warning>)

    f(5, "a")
    f(<warning descr="Expected type 'int', got 'Literal[\"a\"]' instead">"a"</warning>, "b")
    f(5, <warning descr="Expected type 'str', got 'Literal[6]' instead">6</warning>)
    f(<warning descr="Expected type 'int', got 'Literal[\"a\"]' instead">"a"</warning>, <warning descr="Expected type 'str', got 'Literal[5]' instead">5</warning>)
    f(<warning descr="Expected type 'int', got 'Literal[\"a\"]' instead">"a"</warning>, "b")
    f(5, <warning descr="Expected type 'str', got 'Literal[6]' instead">6</warning>)
    f(<warning descr="Expected type 'int', got 'Literal[\"a\"]' instead">"a"</warning>, <warning descr="Expected type 'str', got 'Literal[5]' instead">5</warning>)


def test_same_number_of_parameters_but_one_is_default():
    g(5)
    g(<warning descr="Expected type 'int', got 'Literal[\"a\"]' instead">"a"</warning>)
    g(<warning descr="Expected type 'int', got 'Literal[\"a\"]' instead">"a"</warning>)

    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[\"a\"], Literal[False]). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", False)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[5], Literal[6]). Expected one of: (i: int, b: bool), (i: int, s: str)">(5, 6)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[False], Literal[5]). Expected one of: (i: int, b: bool), (i: int, s: str)">(False, 5)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[\"a\"], Literal[False]). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", False)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[5], Literal[6]). Expected one of: (i: int, b: bool), (i: int, s: str)">(5, 6)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[False], Literal[5]). Expected one of: (i: int, b: bool), (i: int, s: str)">(False, 5)</warning>

    g(5, "a")
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[\"a\"], Literal[\"b\"]). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", "b")</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[5], Literal[6]). Expected one of: (i: int, b: bool), (i: int, s: str)">(5, 6)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[\"a\"], Literal[5]). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", 5)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[\"a\"], Literal[\"b\"]). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", "b")</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[5], Literal[6]). Expected one of: (i: int, b: bool), (i: int, s: str)">(5, 6)</warning>
    g<warning descr="No overload of 'g' matches the arguments. Argument types: (Literal[\"a\"], Literal[5]). Expected one of: (i: int, b: bool), (i: int, s: str)">("a", 5)</warning>


def test_different_number_of_parameters_one_is_default():
    h(5)
    h(<warning descr="No overload of 'h' matches the arguments. Argument types: ((x: Unknown) -> Unknown). Expected one of: (i: int), (i: str)">lambda x: x</warning>)

    h("a")

    h("a", False)
    h(<warning descr="Expected type 'str', got 'Literal[5]' instead">5</warning>, False)  # fail
    h("a", <warning descr="Expected type 'bool', got 'Literal[5]' instead">5</warning>)  # fail
    h(<warning descr="Expected type 'str', got 'Literal[False]' instead">False</warning>, <warning descr="Expected type 'bool', got 'Literal[\"a\"]' instead">"a"</warning>)  # fail
    h(<warning descr="Expected type 'str', got 'Literal[5]' instead">5</warning>, False)  # fail
    h("a", <warning descr="Expected type 'bool', got 'Literal[5]' instead">5</warning>)  # fail
    h(<warning descr="Expected type 'str', got 'Literal[False]' instead">False</warning>, <warning descr="Expected type 'bool', got 'Literal[\"a\"]' instead">"a"</warning>)  # fail
