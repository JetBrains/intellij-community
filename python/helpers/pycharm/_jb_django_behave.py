# coding=utf-8
"""
To support django-behave
"""
import os
import sys


def run_as_django_behave(formatter_name, feature_names, scenario_n_options):
    """
    :param formatter_name: for "-f" argument
    :param feature_names: feature names or folders behave arguments
    :param scenario_n_options: list of ["-n", "scenario_name"]


    :return: True if launched as django-behave. Otherwise false and need to be launched as plain behave
    """
    if "DJANGO_SETTINGS_MODULE" not in os.environ:
        return False
    try:
        import django
        from django.core.management import ManagementUtility

        from behave_django import __version__  # To make sure version exists
        django.setup()
        from django.apps import apps

        if apps.is_installed("behave_django"):
            base = sys.argv[0]
            sys.argv = [base] + ["behave", "-f{0}".format(formatter_name)] + feature_names + scenario_n_options
            print("manage.py " + " ".join(sys.argv[1:]))
            ManagementUtility().execute()
            return True
    except ImportError:
        return False
