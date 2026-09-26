import os
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
        self.assertEqual(
            build_unittest_args(
                path=self.temp_dir,
                targets=None,
                additional_args=[],
                verbose=False,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                self.temp_dir,
                "-t",
                "/project_dir",
                "--quiet",
            ],
        )

    @python3_only
    def test_verbose_discover(self):
        self.assertEqual(
            build_unittest_args(
                path=self.temp_dir,
                targets=None,
                additional_args=[],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                self.temp_dir,
                "-t",
                "/project_dir",
                "--verbose",
            ],
        )

    @python2_only
    def test_python2(self):
        self.assertEqual(
            build_unittest_args(
                path=self.temp_dir,
                targets=None,
                additional_args=[],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                self.temp_dir,
                "-t",
                "/project_dir",
                "--verbose",
            ],
        )

    @python2_only
    def test_python2_and_path_is_file(self):
        temp_file = self.resolve_in_temp_dir("test_sample.py")
        open(temp_file, "a").close()

        self.assertEqual(
            build_unittest_args(
                path=temp_file,
                targets=None,
                additional_args=[],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                self.temp_dir,
                "-p",
                os.path.basename(temp_file),
                "-t",
                "/project_dir",
                "--verbose",
            ],
        )

    @python3_only
    def test_python3_and_path_is_file(self):
        temp_file = self.resolve_in_temp_dir("test_sample.py")
        open(temp_file, "a").close()
        
        self.assertEqual(
            build_unittest_args(
                path=temp_file,
                targets=None,
                additional_args=[],
                verbose=True,
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                temp_file,
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
