#!/usr/bin/env python
from pycharm.fix_getpass import fixGetpass
from pycharm import django_settings
import os
from django.core.management import execute_manager

manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
if not manage_file:
    manage_file = 'manage'

if __name__ == "__main__":
    fixGetpass()
    execute_manager(django_settings)
