#!/usr/bin/env python
from pycharm.fix_getpass import fixGetpass
import os
from django.core import management
import sys

try:
  from runpy import run_module
except ImportError:
  from pycharm.runpy_compat import run_module


def run():
  manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
  if not manage_file:
      manage_file = 'manage'

  execute_manager_original = management.execute_manager

  def execute_manager(settings_mod):
      management.setup_environ(settings_mod)

  management.execute_manager = execute_manager

  fixGetpass()

  run_module(manage_file, None, '__main__', True)

