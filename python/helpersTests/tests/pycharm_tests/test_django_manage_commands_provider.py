import os
import sys
import textwrap

from testing import HelpersTestCase
from testing import _helpers_pycharm_root


class DjangoManageCommandsProviderTest(HelpersTestCase):
    def test_slow_command_is_skipped(self):
        """PY-93724: a command that does not load in time is skipped, and the other commands are reported."""
        self._write_fake_django(slow_command="slowcmd", commands=["slowcmd", "migrate"])

        result = self._run_provider()

        self.assertEqual(result.exit_code, 0, msg=result.stdout + result.stderr)
        self.assertIn('name="migrate"', result.stdout)
        self.assertNotIn('name="slowcmd"', result.stdout)
        self.assertIn("Command 'slowcmd' took too long", result.stderr)

    def _write_fake_django(self, slow_command, commands):
        self._write("django/__init__.py", """\
            def setup():
                pass

            def get_version():
                return "5.0"
        """)
        self._write("django/core/__init__.py", "")
        self._write("django/core/management/__init__.py", """\
            import argparse
            import time

            SLOW_COMMAND = {slow_command!r}
            COMMANDS = {commands!r}

            class _Command(object):
                help = "The help of the command"

                def create_parser(self, prog_name, subcommand):
                    return argparse.ArgumentParser(prog=subcommand)

            class ManagementUtility(object):
                def fetch_command(self, subcommand):
                    if subcommand == SLOW_COMMAND:
                        time.sleep(30)
                    return _Command()

            def get_commands():
                return dict((name, "app") for name in COMMANDS)
        """.format(slow_command=slow_command, commands=commands))

    def _run_provider(self):
        return self.run_process(
            [sys.executable, os.path.join(_helpers_pycharm_root, "_jb_manage_tasks_provider.py")],
            env={
                "PYTHONPATH": os.pathsep.join([self.temp_dir, _helpers_pycharm_root]),
                "_PYCHARM_DJANGO_DEFAULT_TIMEOUT": "1",
            },
        )

    def _write(self, relative_path, text):
        path = os.path.join(self.temp_dir, relative_path)
        directory = os.path.dirname(path)
        if not os.path.isdir(directory):
            os.makedirs(directory)
        with open(path, "w") as f:
            f.write(textwrap.dedent(text))
