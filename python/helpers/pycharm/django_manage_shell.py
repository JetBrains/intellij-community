#!/usr/bin/env python
from fix_getpass import fixGetpass
import os
from django.core import management
import sys

try:
  from runpy import run_module
except ImportError:
  from runpy_compat import run_module


def run(working_dir):
  sys.path.insert(0, working_dir)
  manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
  if not manage_file:
    manage_file = 'manage'

  def execute_manager(settings_mod, argv = None):
      management.setup_environ(settings_mod)

  management.execute_manager = execute_manager

  def execute_from_command_line(argv=None):
    pass

  management.execute_from_command_line = execute_from_command_line

  fixGetpass()

  try:
      #import settings to prevent circular dependencies later on import django.db
      from django.conf import settings
      apps=settings.INSTALLED_APPS

      # From django.core.management.shell

      # XXX: (Temporary) workaround for ticket #1796: force early loading of all
      # models from installed apps.
      from django.db.models.loading import get_models
      get_models()

  except:
      pass

  run_module(manage_file, None, '__main__', True)

