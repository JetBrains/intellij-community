def test_namedtuple():
    from collections import namedtuple
    import sys

    if sys.version_info >= (3, 6):
        assert namedtuple("Point", "x y", verbose=True, rename=True, module="m1")(1, 2).x == 1
        assert namedtuple(u"Point", u"x y", verbose=False, rename=False, module="m2")(1, 2).x == 1
        assert namedtuple(u"Point", u"x y", verbose=False, rename=False, module=None)(1, 2).x == 1

        assert namedtuple("Point", ["x", "y"])(1, 2).x == 1
        assert namedtuple(u"Point", [u"x", u"y"])(1, 2).x == 1
    else:
        assert namedtuple("Point", "x y", verbose=True, rename=True)(1, 2).x == 1
        assert namedtuple(u"Point", u"x y", verbose=False, rename=False)(1, 2).x == 1

        assert namedtuple("Point", ["x", "y"], True, True)(1, 2).x == 1
        assert namedtuple(u"Point", [u"x", u"y"], False, False)(1, 2).x == 1

    Point = namedtuple('Point', 'x y')
    p = Point(1, 2)

    assert p == Point(1, 2)

    assert p._replace(y=3.14).y == 3.14
    assert p._asdict()['x'] == 1
    assert p._fields == ('x', 'y')
    assert p._source is not None

    assert p == (1, 2)
    assert (p.x, p.y) == (1, 2)
    assert p[0] + p[1] == 3
    assert p.index(1) == 0

    assert Point._make([1, 3.14]).y == 3.14


def test_deque():
    from collections import deque
    import sys

    d = deque([2])
    assert list(deque([1, 2, 3])) == [1, 2, 3]
    assert list(deque([1, 2, 3], 2)) == [2, 3]

    assert deque([1, 2, 3]).maxlen is None
    assert deque([1, 2, 3], 2).maxlen == 2

    d.append(3)
    d.appendleft(1)
    assert list(d) == [1, 2, 3]

    d.clear()
    assert len(d) == 0

    if sys.version_info >= (3, 5):
        copy = d.copy()
        assert copy == d
        assert copy is not d

    d.extend([1, 2, 3])
    d.extendleft([5, 3, 1])
    assert d.count(3) == 2
    assert list(d) == [1, 3, 5, 1, 2, 3]

    assert d.pop() == 3
    assert d.popleft() == 1
    d.remove(5)
    assert list(d) == [3, 1, 2]

    d.reverse()
    assert list(d) == [2, 1, 3]
    d.rotate(1)
    assert list(d) == [3, 2, 1]
    d.rotate(-2)
    assert list(d) == [1, 3, 2]

    assert len(d) == 3
    assert 3 in d
    assert d[1] == 3
    d[1] = 4
    assert list(d) == [1, 4, 2]

    assert list(reversed(d)) == [2, 4, 1]

    if sys.version_info >= (3, 5):
        d.insert(len(d), 4)
        assert list(d) == [1, 4, 2, 4]

        assert d.index(4) == 1
        assert d.index(4, 2) == 3
        assert d.index(4, 2, len(d)) == 3

        assert d + deque([5, 6]) == deque(list(d) + [5, 6])
        assert d * 2 == deque([1, 4, 2, 4, 1, 4, 2, 4])
        d *= 2
        assert list(d) == [1, 4, 2, 4, 1, 4, 2, 4]


def test_chain_map():
    from collections import ChainMap

    ChainMap()
    cm = ChainMap({"a": 1, "b": 2})

    assert cm.maps is not None

    assert cm.new_child().maps is not None
    assert cm.new_child({"c": 3}).maps is not None

    assert cm.parents.maps is not None

    cm["d"] = 4
    del cm["a"]
    assert cm["a"] is None
    assert cm["b"] == 2
    assert cm["d"] == 4
    assert list(iter(cm)) == ["b", "d", "c"]
    assert len(cm) == 3


def test_counter():
    from collections import Counter

    c = Counter()
    assert Counter("abc") == {"a": 1, "b": 1, "c": 1}
    assert Counter({"a": 1, "b": 2, "c": 3}) == {"a": 1, "b": 2, "c": 3}
    assert Counter(a=1, b=2, c=3) == {"a": 1, "b": 2, "c": 3}

    c["abc"] = 1
    c["def"] = 2
    assert c == {"abc": 1, "def": 2}

    c["def"] = 0
    assert c == {"abc": 1, "def": 0}
    del c["def"]

    assert c["ghi"] == 0

    c.update({"ghi": 2})
    assert list(c.elements()) == ["abc", "ghi", "ghi"]

    c.update(["a", "a", "b", "a", "a", "a", "b", "a"])
    assert c == {"abc": 1, "ghi": 2, "a": 6, "b": 2}

    c.update(a=-3, b=-1)
    assert c == {"abc": 1, "ghi": 2, "a": 3, "b": 1}

    assert c.most_common(2) == [("a", 3), ("ghi", 2)]
    assert c.most_common() == [("a", 3), ("ghi", 2), ("abc", 1), ("b", 1)]
    c.subtract({"abc": 1, "ghi": -2, "a": 3, "b": -1})
    assert c == {"abc": 0, "ghi": 4, "a": 0, "b": 2}

    c = Counter(a=3, b=1)
    d = Counter(a=1, b=2)
    assert c + d == Counter({'a': 4, 'b': 3})
    assert c - d == Counter({'a': 2})
    assert c & d == Counter({'a': 1, 'b': 1})
    assert c | d == Counter({'a': 3, 'b': 2})

    c = Counter(a=3, b=1)
    c += d
    assert c == Counter({'a': 4, 'b': 3})

    c = Counter(a=3, b=1)
    c -= d
    assert c == Counter({'a': 2})

    c = Counter(a=3, b=1)
    c &= d
    assert c == Counter({'a': 1, 'b': 1})

    c = Counter(a=3, b=1)
    c |= d
    assert c == Counter({'a': 3, 'b': 2})

    c = Counter(a=3, b=1)
    c.subtract(["a", "b"])
    assert c == {"a": 2, "b": 0}

    c = Counter(a=2, b=-4)
    assert +c == {"a": 2}
    assert -c == {"b": 4}


def test_ordered_dict():
    from collections import OrderedDict

    od = OrderedDict([("a", 1), ("b", 2), ("c", 3), ("d", 4)])
    assert od.popitem() == ("d", 4)
    assert od == OrderedDict([("a", 1), ("b", 2), ("c", 3)])
    assert od.popitem(last=False) == ("a", 1)
    assert od == OrderedDict([("b", 2), ("c", 3)])
    assert od.popitem(last=True) == ("c", 3)
    assert od == OrderedDict([("b", 2)])
    assert od == {"b": 2}

    od = OrderedDict([("a", 1), ("b", 2), ("c", 3)])
    od.move_to_end("a")
    assert list(od.keys()) == ["b", "c", "a"]
    od.move_to_end("c", last=True)
    assert list(od.keys()) == ["b", "a", "c"]
    od.move_to_end("a", last=False)
    assert list(od.keys()) == ["a", "b", "c"]


def test_defaultdict():
    from collections import defaultdict

    assert defaultdict() == {}
    assert defaultdict(k1=1, k2=2) == {"k1": 1, "k2": 2}

    assert defaultdict(lambda: 1) == {}
    assert defaultdict(lambda: 2, {"k1": 1, "k2": 2}) == {"k1": 1, "k2": 2}
    assert defaultdict(lambda: 3, [("k1", 1), ("k2", 2)]) == {"k1": 1, "k2": 2}

    assert defaultdict(None) == {}
    assert defaultdict(None, {"k1": 1, "k2": 2}) == {"k1": 1, "k2": 2}
    assert defaultdict(None, [("k1", 1), ("k2", 2)]) == {"k1": 1, "k2": 2}

    assert defaultdict(lambda: 4).__missing__("key") == 4


def test_abc():
    from collections import (Container, Hashable, Iterable, Iterator, Reversible, Generator, Sized, Callable,
                             Collection, Sequence, MutableSequence, ByteString, Set, MutableSet, Mapping,
                             MutableMapping, MappingView, ItemsView, KeysView, ValuesView, Awaitable, Coroutine,
                             AsyncIterable, AsyncIterator, AsyncGenerator)

    assert [Container, Hashable, Iterable, Iterator, Reversible, Generator, Sized, Callable,
                             Collection, Sequence, MutableSequence, ByteString, Set, MutableSet, Mapping,
                             MutableMapping, MappingView, ItemsView, KeysView, ValuesView, Awaitable, Coroutine,
                             AsyncIterable, AsyncIterator, AsyncGenerator]

    from collections.abc import (Container, Hashable, Iterable, Iterator, Reversible, Generator, Sized, Callable,
                             Collection, Sequence, MutableSequence, ByteString, Set, MutableSet, Mapping,
                             MutableMapping, MappingView, ItemsView, KeysView, ValuesView, Awaitable, Coroutine,
                             AsyncIterable, AsyncIterator, AsyncGenerator)

    assert [Container, Hashable, Iterable, Iterator, Reversible, Generator, Sized, Callable,
                             Collection, Sequence, MutableSequence, ByteString, Set, MutableSet, Mapping,
                             MutableMapping, MappingView, ItemsView, KeysView, ValuesView, Awaitable, Coroutine,
                             AsyncIterable, AsyncIterator, AsyncGenerator]