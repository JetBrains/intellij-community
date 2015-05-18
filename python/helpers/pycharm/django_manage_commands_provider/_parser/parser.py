# coding=utf-8
"""
Exports data from optparse or argparse based manage.py commands and reports it to _xml.XmlDumper.
This module encapsulates Django semi-public API knowledge, and not very stable because of it.
"""
from django.core.exceptions import ImproperlyConfigured
from django.core.management import ManagementUtility, get_commands, BaseCommand

from django_manage_commands_provider._parser import _optparse, _argparse
from utils import VersionAgnosticUtils


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
        except Exception:
            continue  # TODO: Log somehow. Probably print to output?

        assert isinstance(command, BaseCommand)

        use_argparse = False
        try:
            use_argparse = command.use_argparse
        except AttributeError:
            pass
        dumper.start_command(command_name=command_name,
                             command_help_text=VersionAgnosticUtils().to_unicode(command.usage("")).replace("%prog", command_name))
        module_to_use = _argparse if use_argparse else _optparse # Choose appropriate module: argparse, optparse
        module_to_use.process_command(dumper, command, command.create_parser("", command_name))
        dumper.close_command()

