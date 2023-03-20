from typing_extensions import TypedDict, Annotated, Required, NotRequired


class A(TypedDict, total=False):
    x: Required[int]
    y: Annotated[Required[int], 'Some constraint']


AlternativeSyntax = TypedDict("AlternativeSyntax", {'x': NotRequired[int], 'y': Required[Annotated[int, 'Some constraint']]})
