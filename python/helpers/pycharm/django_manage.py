#!/usr/bin/env python
import sys
import os

from pycharm_run_utils import adjust_django_sys_path
from fix_getpass import fixGetpass

try:
  from runpy import run_module
except ImportError:
  from runpy_compat import run_module

adjust_django_sys_path()
base_path = sys.argv.pop()

manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
if not manage_file:
  manage_file = 'manage'


if __name__ == "__main__":
  fixGetpass()
  run_module(manage_file, None, '__main__', True)

