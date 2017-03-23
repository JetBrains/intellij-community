def test_map():
    assert map(str, [1, 2, 3]) + ['foo'] == ['1', '2', '3', 'foo']
    assert map(lambda x: x.upper(), 'foo').pop() == 'O'


def test_filter():
    assert filter(lambda x: x % 2 == 0, [1, 2, 3]) + [4, 5, 6] == [2, 4, 5, 6]
    assert filter(lambda x: x != 'f', 'foo') + 'bar' == 'oobar'


def test_open(tmpdir):
    path = tmpdir.join('foo')
    path.write('test')
    with open(str(path), 'r') as fd:
        assert '\n'.join(fd.xreadlines()) == 'test'


def test_zip():
    assert(list(zip([1])) == [(1,)])
    assert(list(zip([1], [2])) == [(1, 2,)])
    assert(list(zip([1], [2], [3])) == [(1, 2, 3)])
    assert(list(zip([1], [2], [3], [4])) == [(1, 2, 3, 4)])
    assert(list(zip([1], [2], [3], [4], [5])) == [(1, 2, 3, 4, 5)])
    assert(list(zip([1], [2], [3], [4], [5], [6])) == [(1, 2, 3, 4, 5, 6)])
    assert(list(zip([1], [2], [3], [4], [5], [6], [7])) == [(1, 2, 3, 4, 5, 6, 7)])
    assert(list(zip([1], [2], [3], [4], [5], [6], [7], [8])) == [(1, 2, 3, 4, 5, 6, 7, 8)])
    assert(list(zip([1], [2], [3], [4], [5], [6], [7], [8], [9])) == [(1, 2, 3, 4, 5, 6, 7, 8, 9)])
    assert(list(zip([1], [2], [3], [4], [5], [6], [7], [8], [10])) == [(1, 2, 3, 4, 5, 6, 7, 8, 10)])
