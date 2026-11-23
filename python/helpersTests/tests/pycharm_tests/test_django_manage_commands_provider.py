import os
import signal
import subprocess
import sys
import textwrap
import unittest

from testing import HelpersTestCase
from testing import _helpers_pycharm_root


class DjangoManageCommandsProviderTest(HelpersTestCase):
    def test_slow_command_is_skipped(self):
        """PY-93724: a command that does not load in time is skipped, and the other commands are reported."""
        self._write_fake_django(slow_commands=["slowcmd"], commands=["slowcmd", "migrate"])

        result = self._run_provider()

        self.assertEqual(result.exit_code, 0, msg=result.stdout + result.stderr)
        self.assertIn('name="migrate"', result.stdout)
        self.assertNotIn('name="slowcmd"', result.stdout)
        self.assertIn(
            "Command 'slowcmd' is not in the command list: it did not load in 1 s.\n"
            "To skip it without the wait, add it to the comma-separated list "
            "'python.django.commandsToSkip' in Find Action | Registry.\n",
            result.stderr,
        )

    @unittest.skipIf(sys.platform == "win32", "The IDE interrupts the helper with SIGINT only on POSIX")
    def test_interrupt_on_timeout_prints_no_traceback(self):
        """PY-93724: the IDE interrupts the helper on its timeout, and the console shows only the warnings of the helper."""
        self._write_fake_django(slow_commands=["slowcmd1", "slowcmd2", "slowcmd3"], commands=["slowcmd1", "slowcmd2", "slowcmd3"])
        process = subprocess.Popen(
            [sys.executable, os.path.join(_helpers_pycharm_root, "_jb_manage_tasks_provider.py")],
            env=self._provider_env(),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            universal_newlines=True,
            # Python installs its SIGINT handler only when the parent does not ignore SIGINT.
            preexec_fn=lambda: signal.signal(signal.SIGINT, signal.SIG_DFL),
        )
        first_warning_line = process.stderr.readline()
        process.send_signal(signal.SIGINT)
        stdout, stderr = process.communicate()

        # Python 2 does not keep the order of the dict of commands.
        self.assertIn("is not in the command list: it did not load in 1 s.\n", first_warning_line)
        self.assertEqual(process.returncode, 1, msg=stdout + stderr)
        self.assertNotIn("Traceback", stderr)
        self.assertNotIn("KeyboardInterrupt", stderr)

    def _write_fake_django(self, slow_commands, commands):
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

            SLOW_COMMANDS = {slow_commands!r}
            COMMANDS = {commands!r}

            class _Command(object):
                help = "The help of the command"

                def create_parser(self, prog_name, subcommand):
                    return argparse.ArgumentParser(prog=subcommand)

            class ManagementUtility(object):
                def fetch_command(self, subcommand):
                    if subcommand in SLOW_COMMANDS:
                        time.sleep(30)
                    return _Command()

            def get_commands():
                return dict((name, "app") for name in COMMANDS)
        """.format(slow_commands=slow_commands, commands=commands))

    def _run_provider(self):
        return self.run_process(
            [sys.executable, os.path.join(_helpers_pycharm_root, "_jb_manage_tasks_provider.py")],
            env=self._provider_env(),
        )

    def _provider_env(self):
        env = os.environ.copy()
        env.update({
            "PYTHONPATH": os.pathsep.join([self.temp_dir, _helpers_pycharm_root]),
            "PYTHONUNBUFFERED": "1",
            "_PYCHARM_DJANGO_DEFAULT_TIMEOUT": "1",
        })
        return env

    def _write(self, relative_path, text):
        path = os.path.join(self.temp_dir, relative_path)
        directory = os.path.dirname(path)
        if not os.path.isdir(directory):
            os.makedirs(directory)
        with open(path, "w") as f:
            f.write(textwrap.dedent(text))
