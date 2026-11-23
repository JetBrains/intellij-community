import os
import sys
import textwrap

from testing import HelpersTestCase
from testing import _helpers_pycharm_root


class DjangoTestManageTest(HelpersTestCase):
    def test_tests_run_with_project_settings(self):
        self._write_project(settings_prefix="")

        result = self._run_tests(settings_module="mysite.settings")

        self.assertEqual(result.exit_code, 0, msg=result.stdout + result.stderr)
        self.assertIn("testFinished", result.stdout)
        self.assertNotIn("testFailed", result.stdout)

    def test_settings_import_error_is_shown(self):
        """PY-93720: an exception in the settings module stops the run, and the traceback leads to the settings module."""
        self._write_project(settings_prefix="import missing_module_py93720\n")

        result = self._run_tests(settings_module="mysite.settings")

        self.assertNotEqual(result.exit_code, 0, msg=result.stdout + result.stderr)
        self.assertIn("missing_module_py93720", result.stderr)
        self.assertIn(os.path.join("mysite", "settings.py"), result.stderr)
        self.assertNotIn("testStarted", result.stdout)

    def test_settings_module_of_ide_wins_over_manage_py(self):
        """PY-93720: a manage.py that sets DJANGO_SETTINGS_MODULE on import does not replace the settings module of the run configuration."""
        self._write_project(settings_prefix="", manage_prefix='os.environ["DJANGO_SETTINGS_MODULE"] = "mysite.settings"\n')
        self._write("mysite/settings_ci.py", """\
            from mysite.settings import *

            SETTINGS_NAME = "ci"
        """)
        self._write("polls/tests.py", """\
            from django.conf import settings
            from django.test import SimpleTestCase


            class SettingsTests(SimpleTestCase):
                def test_settings_name(self):
                    self.assertEqual(getattr(settings, "SETTINGS_NAME", None), "ci")
        """)

        result = self._run_tests(settings_module="mysite.settings_ci")

        self.assertEqual(result.exit_code, 0, msg=result.stdout + result.stderr)
        self.assertIn("testFinished", result.stdout)
        self.assertNotIn("testFailed", result.stdout)

    def test_unknown_settings_module_is_reported(self):
        """PY-93720: without DJANGO_SETTINGS_MODULE, the run stops with the error of Django and does not use empty settings."""
        self._write_project(settings_prefix="")

        result = self._run_tests(settings_module=None)

        self.assertNotEqual(result.exit_code, 0, msg=result.stdout + result.stderr)
        self.assertIn("DJANGO_SETTINGS_MODULE", result.stderr)
        self.assertNotIn("testStarted", result.stdout)

    def _write_project(self, settings_prefix, manage_prefix=""):
        self._write("manage.py", textwrap.dedent("""\
            import os
            import sys

            {manage_prefix}
            if __name__ == "__main__":
                os.environ.setdefault("DJANGO_SETTINGS_MODULE", "mysite.settings")
                from django.core.management import execute_from_command_line
                execute_from_command_line(sys.argv)
        """).format(manage_prefix=manage_prefix))
        self._write("mysite/__init__.py", "")
        self._write("mysite/settings.py", settings_prefix + textwrap.dedent("""\
            SECRET_KEY = "PY-93720"
            INSTALLED_APPS = ["django.contrib.contenttypes", "django.contrib.auth", "polls"]
            DATABASES = {"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}}
            DEFAULT_AUTO_FIELD = "django.db.models.AutoField"
        """))
        self._write("polls/__init__.py", "")
        self._write("polls/models.py", """\
            from django.db import models


            class Question(models.Model):
                text = models.CharField(max_length=200)
        """)
        self._write("polls/tests.py", """\
            from django.test import TestCase

            from polls.models import Question


            class QuestionTests(TestCase):
                def test_create(self):
                    Question.objects.create(text="What?")
                    self.assertEqual(Question.objects.count(), 1)
        """)

    def _run_tests(self, settings_module):
        return self.run_process(
            [sys.executable, os.path.join(_helpers_pycharm_root, "django_test_manage.py"), "test", "polls.tests",
             self._project_dir()],
            env={"DJANGO_SETTINGS_MODULE": settings_module, "PYCHARM_DJANGO_MANAGE_MODULE": None},
        )

    def _project_dir(self):
        return os.path.join(self.temp_dir, "project")

    def _write(self, relative_path, text):
        path = os.path.join(self._project_dir(), relative_path)
        directory = os.path.dirname(path)
        if not os.path.isdir(directory):
            os.makedirs(directory)
        with open(path, "w") as f:
            f.write(textwrap.dedent(text))
