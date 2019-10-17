from typing import TypedDict


class X(TypedDict):
    x: int


class Y(TypedDict, total=False):
    y: str


class XYZ(X, Y):
    z: bool


xyz = XYZ(z=True<warning descr="Parameter 'x' unfilled">)</warning>

x = X(<warning descr="Parameter 'x' unfilled">)</warning>
x.clear()
x.setdefault(<warning descr="Parameter 'k' unfilled">)</warning>

x1: X = {'x': 42}
x1.clear()
x1.setdefault(<warning descr="Parameter 'k' unfilled">)</warning>


class Employee(TypedDict):
    name: str
    id: int


class Employee2(Employee, total=False):
    director: str


em = Employee2(name='str'<warning descr="Parameter 'id' unfilled">)</warning>
em2 = Employee2(<warning descr="Unexpected argument">"str"</warning>, id=2<warning descr="Parameter 'name' unfilled">)</warning>


Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)
Movie2 = TypedDict('Movie', {'name': str, 'year': int})
Movie3 = TypedDict(3<warning descr="Parameter 'fields' unfilled">)</warning>
movie = Movie()
movie2 = Movie2(<warning descr="Parameter 'name' unfilled"><warning descr="Parameter 'year' unfilled">)</warning></warning>
