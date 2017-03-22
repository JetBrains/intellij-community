def test_pure_path():
    import sys

    if sys.version_info >= (3, 6):
        import pathlib

        class A:
            def __fspath__(self):
                return "a.txt"

        assert pathlib.PurePath(A()).name == "a.txt"

        path = pathlib.PurePath(A(), A())
        assert path.name == "a.txt"
        assert path.parent.name == "a.txt"


def test_path():
    import sys

    if sys.version_info >= (3, 6):
        import pathlib

        class A:
            def __fspath__(self):
                return "a.txt"

        assert pathlib.Path(A()).name == "a.txt"

        path = pathlib.Path(A(), A())
        assert path.name == "a.txt"
        assert path.parent.name == "a.txt"
