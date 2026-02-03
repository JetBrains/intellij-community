from typing import TypedDict


Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)
Movie2 = TypedDict('Movie2', {'name': str, 'year': int})

movie = Movie()
movie2 = Movie2(<warning descr="Parameter 'name' unfilled"><warning descr="Parameter 'year' unfilled">)</warning></warning>
movie3 = Movie2(<warning descr="Unexpected argument">''</warning>, <warning descr="Unexpected argument">3</warning><warning descr="Parameter 'name' unfilled"><warning descr="Parameter 'year' unfilled">)</warning></warning>
