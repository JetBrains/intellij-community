def test_fsencode_path_like():
    import os
    import sys

    assert os.fsencode("a.py") == b"a.py"
    assert os.fsencode(b"a.py") == b"a.py"

    if sys.version_info >= (3, 6):
        class A:
            def __fspath__(self):
                return "a.py"

        class B:
            def __fspath__(self):
                return b"a.py"

        assert os.fsencode(A()) == b"a.py"
        assert os.fsencode(B()) == b"a.py"


def test_fsdecode_path_like():
    import os
    import sys

    assert os.fsdecode("a.py") == "a.py"
    assert os.fsdecode(b"a.py") == "a.py"

    if sys.version_info >= (3, 6):
        class A:
            def __fspath__(self):
                return "a.py"

        class B:
            def __fspath__(self):
                return b"a.py"

        assert os.fsdecode(A()) == "a.py"
        assert os.fsdecode(B()) == "a.py"


def test_fspath():
    import sys

    if sys.version_info >= (3, 6):
        import os

        class A:
            def __fspath__(self):
                return "a.py"

        class B:
            def __fspath__(self):
                return b"a.py"

        assert os.fspath("a.py") == "a.py"
        assert os.fspath(b"a.py") == b"a.py"
        assert os.fspath(A()) == "a.py"
        assert os.fspath(B()) == b"a.py"