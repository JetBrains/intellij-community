def test_namedtuple():
    from typing import NamedTuple

    nt1 = NamedTuple("nt1", [("x", str), ("y", int)])
    nt1_instance = nt1(x="str", y=5)
    assert nt1_instance.x == "str"
    assert nt1_instance.y == 5

    nt2 = NamedTuple("nt2", x=str, y=int)
    nt2_instance = nt2(x="str", y=5)
    assert nt2_instance.x == "str"
    assert nt2_instance.y == 5