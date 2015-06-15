# coding=utf-8
"""
Exports data from optparse or argparse based manage.py commands and reports it to _xml.XmlDumper.
This module encapsulates Django semi-public API knowledge, and not very stable because of it.
"""
from django.core.exceptions import ImproperlyConfigured
from django.core.management import ManagementUtility, get_commands, BaseCommand

from django_manage_commands_provider._parser import _optparse, _argparse
from _jb_utils import VersionAgnosticUtils
import sys


__author__ = 'Ilya.Kazakevich'


def report_data(dumper):
    """
    Fetches data from management commands and reports it to dumper.

    :type dumper _xml.XmlDumper
    :param dumper: destination to report
    """
    utility = ManagementUtility()
    for command_name in get_commands().keys():
        try:
            command = utility.fetch_command(command_name)
        except Exception as e:
            sys.stderr.write("Error fetching command {0}: {1}\n".format(command_name, e))
            continue

        assert isinstance(command, BaseCommand)

        use_argparse = False
        try:
            use_argparse = command.use_argparse
        except AttributeError:
            pass

        try:
            parser = command.create_parser("", command_name)
        except Exception as e:
            sys.stderr.write("Error parsing command {0}: {1}\n".format(command_name, e))
            continue

        dumper.start_command(command_name=command_name,
                             command_help_text=VersionAgnosticUtils().to_unicode(command.usage("")).replace("%prog", command_name))
        module_to_use = _argparse if use_argparse else _optparse # Choose appropriate module: argparse, optparse
        module_to_use.process_command(dumper, command, parser)
        dumper.close_command()

