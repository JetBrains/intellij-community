pytest_plugins = "pytester"


def test_foo(testdir):
    testdir.makefile(".py", test_bar="""
    def test_bar():pass
    """)
    res = testdir.runpytest()
    assert res.ret == 0
