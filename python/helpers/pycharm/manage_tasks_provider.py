# coding=utf-8
"""
This is an entry point of this helper.
It fetches data from Django manage commands delegating calles to _parser package report it via _xml module.
See _xml module and readme.txt for more info.

Module can be called directly, but be sure env var DJANGO_SETTINGS_MODULE is set to something like "mysite.settings"
"""

import django

from django_manage_commands_provider._parser import parser
from django_manage_commands_provider import _xml


__author__ = 'Ilya.Kazakevich'

# Some django versions require setup
if hasattr(django, 'setup'):
    django.setup()
dumper = _xml.XmlDumper()
parser.report_data(dumper)
print(dumper.xml)