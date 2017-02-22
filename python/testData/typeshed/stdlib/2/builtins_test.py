def test_map():
    assert map(str, [1, 2, 3]) + ['foo'] == ['1', '2', '3', 'foo']
    assert map(lambda x: x.upper(), 'foo').pop() == 'O'


def test_filter():
    assert filter(lambda x: x % 2 == 0, [1, 2, 3]) + [4, 5, 6] == [2, 4, 5, 6]
    assert filter(lambda x: x != 'f', 'foo') + 'bar' == 'oobar'
