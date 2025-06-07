from typing import TypedDict


Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)
Movie2 = TypedDict('Movie2', {'name': str, 'year': int})
Movie3 = TypedDict(<warning descr="Parameter 'fields' unfilled"><warning descr="Parameter 'typename' unfilled">)</warning></warning>
Movie4 = TypedDict('Movie4'<warning descr="Parameter 'fields' unfilled">)</warning>
Movie5 = TypedDict(<warning descr="Unexpected argument">typename='Movie5'</warning>, <warning descr="Unexpected argument">fields={}</warning><warning descr="Parameter 'typename' unfilled"><warning descr="Parameter 'fields' unfilled">)</warning></warning>
Movie6 = TypedDict('Movie6', {}, <warning descr="Unexpected argument">False</warning>)
Movie7 = TypedDict('Movie7', {}, <warning descr="Unexpected argument">unknown_param=False</warning>)
