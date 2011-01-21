#!/usr/bin/env python
from pycharm.fix_getpass import fixGetpass
from pycharm import django_test_settings
from django.core.management import execute_manager

if __name__ == "__main__":
    fixGetpass()
    execute_manager(django_settings)
