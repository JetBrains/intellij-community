import sys

import pytest

from pycharm._jb_unittest_runner import build_unittest_args


def test_path_doesnt_exist():
    pattern = r"No such file or directory: 'test_foo.py'"
    with pytest.raises(OSError, match=pattern):
        build_unittest_args("test_foo.py", [], [])



def test_targets():
    assert build_unittest_args(
        path=None,
        targets=["test_some_func.SomeFuncTestCase"],
        additional_args=[],
        verbose=False,
        project_dir="/project_dir",
    ) == [
        "python -m unittest",
        "test_some_func.SomeFuncTestCase",
        "--quiet",
    ]


def test_quiet_discover(tmp_path):
    assert build_unittest_args(
        path=str(tmp_path),
        targets=None,
        additional_args=[],
        verbose=False,
        project_dir="/project_dir",
    ) == [
        "python -m unittest",
        "discover",
        "-s",
        str(tmp_path),
        "-t",
        "/project_dir",
        "--quiet",
    ]


def test_verbose_discover(tmp_path):
    assert build_unittest_args(
        path=str(tmp_path),
        targets=None,
        additional_args=[],
        verbose=True,
        project_dir="/project_dir",
    ) == [
        "python -m unittest",
        "discover",
        "-s",
        str(tmp_path),
        "-t",
        "/project_dir",
        "--verbose",
    ]


@pytest.mark.skipif(sys.version_info >= (3, 0), reason="Python 2 is required")
def test_python2(tmp_path):
    assert build_unittest_args(
        path=str(tmp_path),
        targets=None,
        additional_args=[],
        verbose=True,
        project_dir="/project_dir",
        python_version=(2, 7),
    ) == [
        "python -m unittest",
        "discover",
        "-s",
        str(tmp_path),
        "-t",
        "/project_dir",
        "--verbose",
    ]


@pytest.mark.skipif(sys.version_info >= (3, 0), reason="Python 2 is required")
def test_python2_and_path_is_file(tmp_path):
    tmp_file = tmp_path / "test_sample.py"
    tmp_file.touch()

    assert build_unittest_args(
        path=str(tmp_file),
        targets=None,
        additional_args=[],
        verbose=True,
        project_dir="/project_dir",
        python_version=(2, 7),
    ) == [
        "python -m unittest",
        "discover",
        "-s",
        str(tmp_path),
        "-p",
        tmp_file.name,
        "-t",
        "/project_dir",
        "--verbose",
    ]


@pytest.mark.skipif(sys.version_info < (3, 0), reason="Python 3 is required")
def test_python3_and_path_is_file(tmp_path):
    tmp_file = tmp_path / "test_sample.py"
    tmp_file.touch()

    assert build_unittest_args(
        path=str(tmp_file),
        targets=None,
        additional_args=[],
        verbose=True,
        project_dir="/project_dir",
    ) == [
        "python -m unittest",
        str(tmp_file),
        "--verbose",
    ]


def test_user_args():
    assert build_unittest_args(
        path=None,
        targets=["test_some_func.SomeFuncTestCase"],
        additional_args=["--locals", "-f"],
        verbose=True,
        project_dir="/project_dir",
    ) == [
        "python -m unittest",
        "test_some_func.SomeFuncTestCase",
        "--verbose",
        "--locals",
        "-f",
    ]


def test_user_overrides_verbosity():
    assert build_unittest_args(
        path=None,
        targets=["test_some_func.SomeFuncTestCase"],
        additional_args=["--quiet"],
        verbose=True,
        project_dir="/project_dir",
    ) == [
        "python -m unittest",
        "test_some_func.SomeFuncTestCase",
        "--verbose",
        "--quiet",
    ]


def test_rerun_failed_tests():
    assert build_unittest_args(
        path=None,
        targets=[
            "test_some_func.SomeFuncTestCase.test_false_1",
            "test_some_func.SomeFuncTestCase.test_false_2",
        ],
        additional_args=[],
        verbose=False,
        project_dir="/project_dir",
    ) == [
        "python -m unittest",
        "test_some_func.SomeFuncTestCase.test_false_1",
        "test_some_func.SomeFuncTestCase.test_false_2",
        "--quiet",
    ]
