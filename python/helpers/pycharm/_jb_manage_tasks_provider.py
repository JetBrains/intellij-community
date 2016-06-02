# coding=utf-8
"""
This is an entry point of this helper.
It fetches data from Django manage commands delegating calles to _parser package report it via _xml module.
See _xml module and readme.txt for more info.

One may also add list of commands, separated with comma as argument. This is a list of commands to skip.
Could be useful if you know command may lead to freeze

Module can be called directly, but be sure env var DJANGO_SETTINGS_MODULE is set to something like "mysite.settings"
"""


import django

from django_manage_commands_provider._parser import parser
from django_manage_commands_provider import _xml
import sys


__author__ = 'Ilya.Kazakevich'

# Some django versions require setup
if hasattr(django, 'setup'):
    django.setup()
dumper = _xml.XmlDumper()
commands_to_skip = str(sys.argv[1]).split(",") if len(sys.argv) > 1 else []
parser.report_data(dumper, commands_to_skip)
print(dumper.xml)