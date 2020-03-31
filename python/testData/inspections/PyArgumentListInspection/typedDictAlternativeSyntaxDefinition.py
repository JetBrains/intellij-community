from typing import TypedDict


Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)
Movie2 = TypedDict('Movie2', {'name': str, 'year': int})
Movie3 = TypedDict(<warning descr="Parameter 'name' unfilled"><warning descr="Parameter 'fields' unfilled">)</warning></warning>
Movie4 = TypedDict(total=False, fields={}, name='Movie4')
