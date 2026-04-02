import os
import subprocess
import sys
import textwrap
import unittest

from testing import HelpersTestCase
from testing import _helpers_pycharm_root
from testing import python3_only


@python3_only
class TeamcityPluginTest(HelpersTestCase):
    @staticmethod
    def run_pytest(test_file, extra_args=None):
        args = [sys.executable, "-m", "pytest", "-p", "teamcity.pytest_plugin", "--teamcity"]
        if extra_args:
            args.extend(extra_args)
        args.append(test_file)

        env = os.environ.copy()
        env["PYTHONPATH"] = _helpers_pycharm_root

        return subprocess.run(args, capture_output=True, text=True, env=env)

    # PY-84850
    def test_teamcity_plugin_with_xdist(self):
        test_file = os.path.join(self.temp_dir, "test_example.py")
        with open(test_file, "w") as f:
            f.write(textwrap.dedent("""\
                def test_example_1():
                    assert 1 + 1 == 2

                def test_example_2():
                    assert 2 * 2 == 4

                def test_example_3():
                    assert 3 - 1 == 2
            """))

        result = self.run_pytest(test_file, extra_args=["-n", "2"])

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        self.assertIn("##teamcity[testStarted", result.stdout)
        self.assertIn("##teamcity[testFinished", result.stdout)

    # PY-84850
    def test_teamcity_plugin_without_xdist(self):
        test_file = os.path.join(self.temp_dir, "test_basic.py")
        with open(test_file, "w") as f:
            f.write(textwrap.dedent("""\
                def test_basic():
                    assert True
            """))

        result = self.run_pytest(test_file)

        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
        self.assertIn("##teamcity[testStarted", result.stdout)
        self.assertIn("##teamcity[testFinished", result.stdout)
