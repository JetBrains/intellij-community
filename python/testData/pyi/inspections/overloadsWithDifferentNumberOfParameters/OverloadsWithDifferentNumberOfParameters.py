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

    g<warning descr="Unexpected type(s):(str, bool)Possible type(s):(int, bool)(int, str)">("a", False)</warning>
    g<warning descr="Unexpected type(s):(int, int)Possible type(s):(int, bool)(int, str)">(5, 6)</warning>
    g<warning descr="Unexpected type(s):(bool, int)Possible type(s):(int, bool)(int, str)">(False, 5)</warning>

    g(5, "a")
    g<warning descr="Unexpected type(s):(str, str)Possible type(s):(int, bool)(int, str)">("a", "b")</warning>
    g<warning descr="Unexpected type(s):(int, int)Possible type(s):(int, bool)(int, str)">(5, 6)</warning>
    g<warning descr="Unexpected type(s):(str, int)Possible type(s):(int, bool)(int, str)">("a", 5)</warning>


def test_different_number_of_parameters_one_is_default():
    h(5)
    h(<warning descr="Unexpected type(s):((x: Any) -> Any)Possible type(s):(int)(str)">lambda x: x</warning>)

    h("a")

    h("a", False)
    h(<warning descr="Expected type 'str', got 'int' instead">5</warning>, False)  # fail
    h("a", <warning descr="Expected type 'bool', got 'int' instead">5</warning>)  # fail
    h(<warning descr="Expected type 'str', got 'bool' instead">False</warning>, <warning descr="Expected type 'bool', got 'str' instead">"a"</warning>)  # fail