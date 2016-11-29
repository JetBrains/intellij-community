xs = set([1, 2, 3])

'foo' + <warning descr="Expected type 'Union[str, unicode]', got 'int' instead">xs.pop()</warning>
xs.discard(<weak_warning descr="Expected type 'int' (matched generic type 'TypeVar('T')'), got 'str' instead">'foo'</weak_warning>)
xs.remove(<weak_warning descr="Expected type 'int' (matched generic type 'TypeVar('T')'), got 'str' instead">'bar'</weak_warning>)
xs.add(<weak_warning descr="Expected type 'int' (matched generic type 'TypeVar('T')'), got 'object' instead">object()</weak_warning>)

ys = ['green', 'eggs']
ys.extend(<weak_warning descr="Expected type 'Iterable[str]' (matched generic type 'Iterable[TypeVar('T')]'), got 'Set[int]' instead">xs</weak_warning>)
