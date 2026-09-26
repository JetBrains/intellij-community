def test_dump_threads():
    import pydevd
    try:
        from StringIO import StringIO
    except:
        from io import StringIO
    stream = StringIO()
    pydevd.dump_threads(stream=stream)
    contents = stream.getvalue()
    assert 'Thread MainThread  (daemon: False, pydevd thread: False)' in contents
    assert 'test_dump_threads' in contents