#!/usr/bin/env python
from pycharm.fix_getpass import fixGetpass
from django.core.management import execute_manager
from pycharm import django_settings_tests

if __name__ == "__main__":
    fixGetpass()
    execute_manager(django_settings_tests)
