# coding=utf-8
"""
This is an entry point of this helper.
It fetches data from Django manage commands via _optparse module and report is via _xml module.
See _xml module and readme.txt for more info.

Module can be called directly, but be sure env var DJANGO_SETTINGS_MODULE is set to something like "mysite.settings"
"""

from distutils.version import LooseVersion
import django
import _optparse
import _xml

__author__ = 'Ilya.Kazakevich'

# TODO: Support Django 1.8 as well, it uses argparse, not optparse
version = LooseVersion(django.get_version())
assert version < LooseVersion('1.8a'), "Only Django <1.8 is supported now"
# Some django versions require setup
if hasattr(django, 'setup'):
    django.setup()
dumper = _xml.XmlDumper()
_optparse.report_data(dumper)
print(dumper.xml)