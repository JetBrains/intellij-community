# coding=utf-8
"""
Exports data from optparse or argparse based manage.py commands and reports it to _xml.XmlDumper.
This module encapsulates Django semi-public API knowledge, and not very stable because of it.
Optional env var ``_PYCHARM_DJANGO_DEFAULT_TIMEOUT`` sets timeout (in seconds) to wait for each command to be
fetched.
"""
import sys
import threading
from _jb_utils import VersionAgnosticUtils
from django.core.management import ManagementUtility, get_commands
from django_manage_commands_provider._parser import _optparse, _argparse
import os

__author__ = 'Ilya.Kazakevich'


class _Fetcher(threading.Thread):
    def __init__(self, utility, command_name):
        super(_Fetcher, self).__init__()
        self.result = None
        self.__utility = utility
        self.__command_name = command_name
        self.command_lead_to_exception = False

    def run(self):
        try:
            self.result = self.__utility.fetch_command(self.__command_name)
        except Exception as e:
            sys.stderr.write("Error fetching command '{0}': {1}\n".format(self.__command_name, e))
            self.command_lead_to_exception = True


def report_data(dumper, commands_to_skip):
    """
    Fetches data from management commands and reports it to dumper.

    :type dumper _xml.XmlDumper
    :type commands_to_skip list
    :param commands_to_skip list of commands to skip
    :param dumper: destination to report
    """
    utility = ManagementUtility()
    for command_name in get_commands().keys():

        if command_name in commands_to_skip:
            sys.stderr.write("Skipping command '{0}' due to config\n".format(command_name))
            continue

        fetcher = _Fetcher(utility, command_name)
        fetcher.daemon = True
        fetcher.start()
        fetcher.join(int(os.getenv("_PYCHARM_DJANGO_DEFAULT_TIMEOUT", "2")))
        command = fetcher.result
        if not command:
            if fetcher.command_lead_to_exception:
                sys.stderr.write("Command '{0}' skipped\n".format(command_name))
                continue
            else:
                sys.stderr.write(
                    "Command '{0}' took too long and may freeze everything. Consider adding it to 'skip commands' list\n".format(
                        command_name))
                sys.exit(1)

        use_argparse = False
        # There is no optparse in 1.10
        if _is_django_10():
            use_argparse = True
        else:
            try:
                use_argparse = command.use_argparse
            except AttributeError:
                pass

        try:
            parser = command.create_parser("", command_name)
        except Exception as e:
            sys.stderr.write("Error parsing command {0}: {1}\n".format(command_name, e))
            continue

        try:  # and there is no "usage()" since 1.10
            usage = command.usage("")
        except AttributeError:
            usage = command.help

        dumper.start_command(command_name=command_name,
                             command_help_text=VersionAgnosticUtils().to_unicode(usage).replace("%prog",
                                                                                                command_name))
        module_to_use = _argparse if use_argparse else _optparse  # Choose appropriate module: argparse, optparse
        module_to_use.process_command(dumper, command, parser)
        dumper.close_command()


def _is_django_10():
    """

    :return: is Django >= 1.10
    """
    try:
        from distutils.version import StrictVersion
        import django
        return StrictVersion(django.get_version()) >= StrictVersion("1.10")
    except (ImportError, AttributeError):
        return False
