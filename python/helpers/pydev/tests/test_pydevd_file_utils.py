from pydevd_file_utils import get_fullname


def test_get_fullname_exists():
    assert get_fullname('pydevd_file_utils').endswith('pydevd_file_utils.py')


def test_get_fullname_unknown():
    assert get_fullname("hello_world") is None
