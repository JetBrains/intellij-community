#!/usr/bin/env python
import sys
from django.core.management import execute_manager
from pycharm import django_settings

if __name__ == "__main__":
    if sys.platform == 'win32':
        import getpass
        getpass.getpass = getpass.fallback_getpass
    execute_manager(django_settings)
