import os
import re
import subprocess
import sys
import textwrap
import unittest

from _jb_unittest_runner import build_runner_script_args
from _jb_unittest_runner import build_unittest_args
from testing import HelpersTestCase
from testing import _helpers_pycharm_root
from testing import python2_only
from testing import python3_only


class RunnerProcessTestCase(HelpersTestCase):
    def _write(self, name, content):
        with open(self.resolve_in_temp_dir(name), "w") as f:
            f.write(textwrap.dedent(content))

    def _run_script(self, script, *args):
        return self._run_helper(args, {"JB_UNITTEST_RUNNER_SCRIPT": script})

    def _run_helper(self, args, extra_env=None):
        env = os.environ.copy()
        env.update(extra_env or {})
        env["PWD"] = self.temp_dir

        return subprocess.run(
            [sys.executable, os.path.join(_helpers_pycharm_root, "_jb_unittest_runner.py")] + list(args),
            cwd=self.temp_dir,
            env=env,
            capture_output=True,
            text=True,
        )

    @staticmethod
    def _node_ids(output):
        return {
            name: (node_id, parent_id)
            for name, node_id, parent_id in re.findall(
                r"##teamcity\[testStarted .*?name='([^']+)' nodeId='([0-9]+)' parentNodeId='([0-9]+)'", output)
        }

    @staticmethod
    def _test_events(output):
        return re.findall(r"##teamcity\[(testStarted|testFailed|testFinished) .*? name='([^']+)'", output)


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
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "test_some_func.SomeFuncTestCase",
            ],
        )

    @python3_only
    def test_discover(self):
        self.assertEqual(
            build_unittest_args(
                path=self.temp_dir,
                targets=None,
                additional_args=[],
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                self.temp_dir,
                "-t",
                "/project_dir",
            ],
        )

    @python2_only
    def test_python2(self):
        self.assertEqual(
            build_unittest_args(
                path=self.temp_dir,
                targets=None,
                additional_args=[],
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "discover",
                "-s",
                self.temp_dir,
                "-t",
                "/project_dir",
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
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                temp_file,
            ],
        )

    @python3_only
    def test_user_args(self):
        self.assertEqual(
            build_unittest_args(
                path=None,
                targets=["test_some_func.SomeFuncTestCase"],
                additional_args=["--locals", "-f"],
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "test_some_func.SomeFuncTestCase",
                "--locals",
                "-f",
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
                project_dir="/project_dir",
            ),
            [
                "python -m unittest",
                "test_some_func.SomeFuncTestCase.test_false_1",
                "test_some_func.SomeFuncTestCase.test_false_2",
            ],
        )


class JBUnittestRunnerScriptTest(RunnerProcessTestCase):
    def test_runner_script_targets(self):
        self.assertEqual(
            build_runner_script_args(
                script="runtests.py",
                path=None,
                targets=["asgi.tests.ASGITest.test_get_asgi_application"],
                additional_args=["--parallel=1"],
                working_dir=self.temp_dir,
            ),
            ["runtests.py", "asgi.tests.ASGITest.test_get_asgi_application", "--parallel=1"],
        )

    def test_runner_script_file_path(self):
        os.makedirs(self.resolve_in_temp_dir("asgi"))
        test_file = self.resolve_in_temp_dir(os.path.join("asgi", "tests.py"))
        open(test_file, "a").close()

        self.assertEqual(
            build_runner_script_args("runtests.py", test_file, None, [], working_dir=self.temp_dir),
            ["runtests.py", "asgi.tests"],
        )

    def test_runner_script_directory_path(self):
        test_dir = self.resolve_in_temp_dir(os.path.join("gis_tests", "geoapp"))
        os.makedirs(test_dir)

        self.assertEqual(
            build_runner_script_args("runtests.py", test_dir, None, [], working_dir=self.temp_dir),
            ["runtests.py", "gis_tests.geoapp"],
        )

    def test_runner_script_project_dir_runs_all_tests(self):
        self.assertEqual(
            build_runner_script_args("runtests.py", self.temp_dir, None, ["-v", "2"], working_dir=self.temp_dir),
            ["runtests.py", "-v", "2"],
        )

    def test_runner_script_package_init_file(self):
        os.makedirs(self.resolve_in_temp_dir("asgi"))
        init_file = self.resolve_in_temp_dir(os.path.join("asgi", "__init__.py"))
        open(init_file, "a").close()

        self.assertEqual(
            build_runner_script_args("runtests.py", init_file, None, [], working_dir=self.temp_dir),
            ["runtests.py", "asgi"],
        )

    @unittest.skipUnless(hasattr(os, "symlink") and os.name != "nt", "Needs symlinks")
    def test_runner_script_working_dir_is_symlink(self):
        real_dir = self.resolve_in_temp_dir("real")
        os.makedirs(os.path.join(real_dir, "asgi"))
        link_dir = self.resolve_in_temp_dir("link")
        os.symlink(real_dir, link_dir)

        self.assertEqual(
            build_runner_script_args("runtests.py", os.path.join(real_dir, "asgi"), None, [], working_dir=link_dir),
            ["runtests.py", "asgi"],
        )

    def test_runner_script_path_outside_project_dir(self):
        project_dir = self.resolve_in_temp_dir("project")
        os.makedirs(project_dir)

        self.assertEqual(
            build_runner_script_args("runtests.py", self.temp_dir, None, [], working_dir=project_dir),
            ["runtests.py", self.temp_dir],
        )

    @python3_only
    def test_runner_script_reports_tests(self):
        self._write("runtests.py", """\
            import os
            import sys
            import unittest

            # Global setup that "python -m unittest" cannot do
            os.environ["RUNTESTS_SETUP_DONE"] = "1"
            suite = unittest.defaultTestLoader.loadTestsFromNames(sys.argv[1:])
            result = unittest.TextTestRunner().run(suite)
            sys.exit(not result.wasSuccessful())
        """)
        self._write("test_sample.py", """\
            import os
            import unittest

            class SampleTest(unittest.TestCase):
                def test_setup_done(self):
                    self.assertEqual(os.environ.get("RUNTESTS_SETUP_DONE"), "1")

                def test_subtests(self):
                    for i in range(2):
                        with self.subTest(i=i):
                            self.assertEqual(i, 0)
        """)

        result = self._run_script("runtests.py", "--target", "test_sample.SampleTest")

        self.assertEqual(result.returncode, 1, msg=result.stdout + result.stderr)
        self.assertEqual(
            self._test_events(result.stdout),
            [
                ("testStarted", "test_setup_done"),
                ("testFinished", "test_setup_done"),
                ("testStarted", "test_subtests"),
                ("testStarted", "(i=0)"),
                ("testFinished", "(i=0)"),
                ("testStarted", "(i=1)"),
                ("testFailed", "(i=1)"),
                ("testFinished", "(i=1)"),
                ("testFailed", "test_subtests"),
                ("testFinished", "test_subtests"),
            ],
        )
        self.assertIn(
            "locationHint='python<{0}>://test_sample.SampleTest.test_setup_done'".format(self.temp_dir),
            result.stdout,
        )
        node_ids = self._node_ids(result.stdout)
        self.assertEqual(node_ids["(i=0)"][1], node_ids["test_subtests"][0])
        self.assertEqual(node_ids["(i=1)"][1], node_ids["test_subtests"][0])

    @python3_only
    def test_runner_script_keeps_reporting_with_own_result_class(self):
        # Django gives its own result class with "--debug-sql" and "--pdb"
        self._write("runtests.py", """\
            import sys
            import unittest

            class OwnResult(unittest.TextTestResult):
                def addSuccess(self, test):
                    super().addSuccess(test)
                    sys.stdout.write("own result: success\\n")

            suite = unittest.defaultTestLoader.loadTestsFromNames(sys.argv[1:])
            unittest.TextTestRunner(resultclass=OwnResult).run(suite)
        """)
        self._write("test_sample.py", """\
            import unittest

            class SampleTest(unittest.TestCase):
                def test_pass(self):
                    pass
        """)

        result = self._run_script("runtests.py", "--target", "test_sample.SampleTest")

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        self.assertEqual(self._test_events(result.stdout), [("testStarted", "test_pass"), ("testFinished", "test_pass")])
        self.assertIn("own result: success", result.stdout)

    @python3_only
    def test_runner_script_reports_duration_of_replayed_test(self):
        # A parallel runner replays the events of a test after the test, as Django does with "--parallel"
        self._write("runtests.py", """\
            import unittest

            class SampleTest(unittest.TestCase):
                def test_slow(self):
                    pass

            test = SampleTest("test_slow")
            result = unittest.TextTestRunner()._makeResult()
            result.startTest(test)
            result.addDuration(test, 2.5)
            result.addSuccess(test)
            result.stopTest(test)
        """)

        result = self._run_script("runtests.py")

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        durations = re.findall(r"##teamcity\[testFinished .*?duration='([0-9]+)'", result.stdout)
        self.assertEqual(len(durations), 1, msg=result.stdout)
        self.assertGreaterEqual(int(durations[0]), 2500)
        self.assertLess(int(durations[0]), 3500)

    @python3_only
    def test_runner_script_reports_interleaved_tests_in_one_tree(self):
        # The script runs the tests out of order, as Django does with "--parallel"
        self._write("runtests.py", """\
            import sys
            import unittest

            loader = unittest.defaultTestLoader
            suite = unittest.TestSuite([
                loader.loadTestsFromName("test_sample.FirstTest.test_one"),
                loader.loadTestsFromName("test_sample.SecondTest.test_one"),
                loader.loadTestsFromName("test_sample.FirstTest.test_two"),
            ])
            unittest.TextTestRunner().run(suite)
        """)
        self._write("test_sample.py", """\
            import unittest

            class FirstTest(unittest.TestCase):
                def test_one(self):
                    pass

                def test_two(self):
                    pass

            class SecondTest(unittest.TestCase):
                def test_one(self):
                    pass
        """)

        result = self._run_script("runtests.py")

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        suites = re.findall(r"##teamcity\[testSuiteStarted .*? name='([^']+)'", result.stdout)
        self.assertEqual(suites, ["test_sample", "FirstTest", "SecondTest"])


class JBUnittestRunnerMainTest(RunnerProcessTestCase):
    def setUp(self):
        super(JBUnittestRunnerMainTest, self).setUp()
        os.makedirs(self.resolve_in_temp_dir("testmodule"))
        self._write(os.path.join("testmodule", "test.py"), """\
            import unittest

            class SampleTest(unittest.TestCase):
                def test_pass(self):
                    pass
        """)

    @python3_only
    def test_user_discover_args(self):
        result = self._run_helper(["--", "discover", "testmodule", "*.py"])

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        self.assertEqual(
            self._test_events(result.stdout),
            [
                ("testStarted", "test_pass"),
                ("testFinished", "test_pass"),
            ],
        )

    @python3_only
    def test_quiet_by_default(self):
        result = self._run_helper(["--target", "testmodule.test.SampleTest"])

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        self.assertNotIn(" ... ok", result.stdout + result.stderr)

    @python3_only
    def test_jb_verbose(self):
        result = self._run_helper(["--target", "testmodule.test.SampleTest"], {"JB_VERBOSE": "1"})

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        self.assertIn(" ... ok", result.stdout + result.stderr)

    @python3_only
    def test_user_verbose_flag(self):
        result = self._run_helper(["--", "discover", "testmodule", "*.py", "--verbose"])

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        self.assertIn(" ... ok", result.stdout + result.stderr)

    @python3_only
    def test_user_quiet_flag_overrides_jb_verbose(self):
        result = self._run_helper(["--target", "testmodule.test.SampleTest", "--", "--quiet"], {"JB_VERBOSE": "1"})

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        self.assertNotIn(" ... ok", result.stdout + result.stderr)
