# coding=utf-8
"""
This XML is part of API implemented by XmlDumper, but data fetch engine is optparse-specific (at least in Django <1.8)
and implemented in _django_obtainer_optparse.py.

Module to be call package directly, just to get XML, but be sure env var DJANGO_SETTINGS_MODULE is set to something
like "mysite.settings"
"""
from distutils.version import LooseVersion
import django
import _django_obtainer_optparse
from _django_obtainer_core import XmlDumper

__author__ = 'Ilya.Kazakevich'

# TODO: Support Django 1.8 as well, it uses argparse, not optparse
assert LooseVersion(django.get_version()) < LooseVersion('1.8a'), "Only Django <1.8 is supported now"
dumper = XmlDumper()
_django_obtainer_optparse.report_data(dumper)
print(dumper.xml)