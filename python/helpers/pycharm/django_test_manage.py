#!/usr/bin/env python

import os, sys

from django.core.management import ManagementUtility

from pycharm_run_utils import import_system_module

inspect = import_system_module("inspect")

#import settings to prevent circular dependencies later on import django.db
try:
    from django.conf import settings
    apps = settings.INSTALLED_APPS
except:
    pass

import django_test_runner
project_directory = sys.argv.pop()

from django.core import management
from django.core.management.commands.test import Command

try:
  # setup environment
  # this stuff was done earlier by setup_environ() which was removed in 1.4
  sys.path.append(os.path.join(project_directory, os.pardir))
  project_name = os.path.basename(project_directory)
  __import__(project_name)
except ImportError:
  # project has custom structure (project directory is not importable)
  pass
finally:
  sys.path.pop()

manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
if not manage_file:
  manage_file = 'manage'

try:
  __import__(manage_file)
except ImportError:
  print ("There is no such manage file " + str(manage_file) + "\n")

settings_file = os.getenv('DJANGO_SETTINGS_MODULE')
if not settings_file:
  settings_file = 'settings'


class PycharmTestCommand(Command):
  def get_runner(self):
    TEST_RUNNER = 'django_test_runner.run_tests'
    test_path = TEST_RUNNER.split('.')
    # Allow for Python 2.5 relative paths
    if len(test_path) > 1:
      test_module_name = '.'.join(test_path[:-1])
    else:
      test_module_name = '.'
    test_module = __import__(test_module_name, {}, {}, test_path[-1])
    test_runner = getattr(test_module, test_path[-1])
    return test_runner

  def handle(self, *test_labels, **options):
    # handle south migration in tests
    management.get_commands()
    if hasattr(settings, "SOUTH_TESTS_MIGRATE") and not settings.SOUTH_TESTS_MIGRATE:
      # point at the core syncdb command when creating tests
      # tests should always be up to date with the most recent model structure
      management._commands['syncdb'] = 'django.core'
    elif 'south' in settings.INSTALLED_APPS:
      try:
        from south.management.commands import MigrateAndSyncCommand
        management._commands['syncdb'] = MigrateAndSyncCommand()
        from south.hacks import hacks
        if hasattr(hacks, "patch_flush_during_test_db_creation"):
          hacks.patch_flush_during_test_db_creation()
      except ImportError:
        management._commands['syncdb'] = 'django.core'

    verbosity = int(options.get('verbosity', 1))
    interactive = options.get('interactive', True)
    failfast = options.get('failfast', False)
    TestRunner = self.get_runner()

    if not inspect.ismethod(TestRunner):
      failures = TestRunner(test_labels, verbosity=verbosity, interactive=interactive, failfast=failfast)
    else:
      test_runner = TestRunner(verbosity=verbosity, interactive=interactive, failfast=failfast)
      failures = test_runner.run_tests(test_labels)

    if failures:
      sys.exit(bool(failures))

class PycharmTestManagementUtility(ManagementUtility):
  def __init__(self, argv=None):
    ManagementUtility.__init__(self, argv)

  def execute(self):
    PycharmTestCommand().run_from_argv(self.argv)

if __name__ == "__main__":

  try:
    custom_settings = __import__(settings_file)
    splitted_settings = settings_file.split('.')
    if len(splitted_settings) != 1:
      settings_name = '.'.join(splitted_settings[:-1])
      settings_module = __import__(settings_name, globals(), locals(), [splitted_settings[-1]])
      custom_settings = getattr(settings_module, splitted_settings[-1])

  except ImportError:
    print ("There is no such settings file " + str(settings_file) + "\n")

  try:
    subcommand = sys.argv[1]
  except IndexError:
    subcommand = 'help' # Display help if no arguments were given.

  if subcommand == 'test':
    utility = PycharmTestManagementUtility(sys.argv)
  else:
    utility = ManagementUtility()

  utility.execute()