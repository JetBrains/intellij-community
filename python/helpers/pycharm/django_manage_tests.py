#!/usr/bin/env python
import sys
from django.core.management import execute_manager
from pycharm import django_settings_tests

if __name__ == "__main__":
    if sys.platform == 'win32':
        import getpass
        import warnings
        getpass.getpass = getpass.fallback_getpass
        warnings.simplefilter("ignore", category=getpass.GetPassWarning)
    execute_manager(django_settings_tests)
