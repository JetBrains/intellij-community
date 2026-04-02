import pathlib
import sys
import unittest

from _jb_unittest_runner import build_unittest_args
from testing import HelpersTestCase
from testing import python2_only
from testing import python3_only


class JBUnittestRunnerTest(HelpersTestCase):
    @python3_only
    def test_path_doesnt_exist(self):
        pattern = r"No such file or directory: 'test_foo.py'"
        with self.assertRaisesRegex(OSError, pattern):
            build_unittest_args("test_foo.py", [], [])

    @python3_only
    def test_targets(self):
        self.assertEqual(
            build_unittest_args(
                path=None,
                targets=["test_some_func.SomeFuncTestCase"],
                additional_args=[],
                verbose=False,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "test_some_func.SomeFuncTestCase",
                "--quiet",
            ],
        )

    @python3_only
    def test_quiet_discover(self):
        tmp_path = pathlib.Path(self.temp_dir)
        self.assertEqual(
            build_unittest_args(
                path=str(tmp_path),
                targets=None,
                additional_args=[],
                verbose=False,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                str(tmp_path),
                "-t",
                "/project_dir",
                "--quiet",
            ],
        )

    @python3_only
    def test_verbose_discover(self):
        tmp_path = pathlib.Path(self.temp_dir)
        self.assertEqual(
            build_unittest_args(
                path=str(tmp_path),
                targets=None,
                additional_args=[],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                str(tmp_path),
                "-t",
                "/project_dir",
                "--verbose",
            ],
        )

    @python2_only
    def test_python2(self):
        tmp_path = pathlib.Path(self.temp_dir)
        self.assertEqual(
            build_unittest_args(
                path=str(tmp_path),
                targets=None,
                additional_args=[],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                str(tmp_path),
                "-t",
                "/project_dir",
                "--verbose",
            ],
        )

    @python2_only
    def test_python2_and_path_is_file(self):
        tmp_path = pathlib.Path(self.temp_dir)
        tmp_file = tmp_path / "test_sample.py"
        tmp_file.touch()

        self.assertEqual(
            build_unittest_args(
                path=str(tmp_file),
                targets=None,
                additional_args=[],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                str(tmp_path),
                "-p",
                tmp_file.name,
                "-t",
                "/project_dir",
                "--verbose",
            ],
        )

    @python3_only
    def test_python3_and_path_is_file(self):
        tmp_path = pathlib.Path(self.temp_dir)
        tmp_file = tmp_path / "test_sample.py"
        tmp_file.touch()

        self.assertEqual(
            build_unittest_args(
                path=str(tmp_file),
                targets=None,
                additional_args=[],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                str(tmp_file),
                "--verbose",
            ],
        )

    @python3_only
    def test_user_args(self):
        self.assertEqual(
            build_unittest_args(
                path=None,
                targets=["test_some_func.SomeFuncTestCase"],
                additional_args=["--locals", "-f"],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "test_some_func.SomeFuncTestCase",
                "--verbose",
                "--locals",
                "-f",
            ],
        )

    @python3_only
    def test_user_overrides_verbosity(self):
        self.assertEqual(
            build_unittest_args(
                path=None,
                targets=["test_some_func.SomeFuncTestCase"],
                additional_args=["--quiet"],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "test_some_func.SomeFuncTestCase",
                "--verbose",
                "--quiet",
            ],
        )

    @python3_only
    def test_rerun_failed_tests(self):
        self.assertEqual(
            build_unittest_args(
                path=None,
                targets=[
                    "test_some_func.SomeFuncTestCase.test_false_1",
                    "test_some_func.SomeFuncTestCase.test_false_2",
                ],
                additional_args=[],
                verbose=False,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "test_some_func.SomeFuncTestCase.test_false_1",
                "test_some_func.SomeFuncTestCase.test_false_2",
                "--quiet",
            ],
        )
