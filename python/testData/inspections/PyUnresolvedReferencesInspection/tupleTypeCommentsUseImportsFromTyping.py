from typing import List, Set, Tuple, <warning descr="Unused import statement 'Optional'">Optional</warning>


d = {'a': [1, 2, 3]}
for l1, l2 in d.items():  # type: str, List[int]
    pass


class C:
    def __enter__(self):
        return {1}, {2}


with C() as (x1, x2):  # type: (Set[int], Set[int])
    pass


y1, y2 = (1, 2), 3  # type: Tuple[int, int], int
