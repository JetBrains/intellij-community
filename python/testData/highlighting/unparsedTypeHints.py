from typing import Union


def a(b: 'Union[int<error descr="']' expected">'</error>) -> 'Union[str<error descr="']' expected">'</error>:
    pass


def c(d):
    # type: (Union[int<error descr="']' expected">)</error> -> Union[str<EOLError descr="']' expected"></EOLError>
    pass


e = None  # type: Union[str<EOLError descr="']' expected"></EOLError>


def f(g: Union[int<error descr="']' expected">)</error> -> Union[str:<EOLError descr="':' or ']' expected"></EOLError>
    pass<EOLError descr="End of statement expected"></EOLError>


h: Union[str<EOLError descr="']' expected"></EOLError>