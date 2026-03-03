print('a' < 'b' < 'c' < 'd')
print(('a' < 'b') < <warning descr="Expected type 'int', got 'str' instead">'c'</warning>)
print((1, 1) < (1, 2) < <warning descr="Expected type 'Tuple[Literal[1, 2], ...]' (matched generic type 'Tuple[_T_co, ...]'), got 'Tuple[Literal[1], Literal[3]]' instead">(1, 3)</warning> < <warning descr="Expected type 'Tuple[Literal[1, 3], ...]' (matched generic type 'Tuple[_T_co, ...]'), got 'Tuple[Literal[1], Literal[4]]' instead">(1, 4)</warning>)
print(((1, 1) < (1, 2)) < <warning descr="Expected type 'int', got 'Tuple[int, int]' instead">(1, 3)</warning>)
print(1.0 < 4.5 < 9.3 < 10.0)
print((1.0 < 4.5) < 9.3)

from datetime import datetime
d1 = datetime.now() 
d2 = datetime.now() 
d3 = datetime.now() 

print(d1 < d2 < d3)
print((d1 < d2) < <warning descr="Expected type 'int', got 'datetime' instead">d3</warning>)