# coding=utf-8
"""
Exports data from optparse-based manage.py commands and reports it to _xml.XmlDumper.
This module encapsulates Django semi-public API knowledge, and not very stable because of it.
"""
from optparse import Option
import django
from django.apps import registry
from django.conf import settings
from django.core.management import ManagementUtility, get_commands, BaseCommand

__author__ = 'Ilya.Kazakevich'


def report_data(dumper):
    """
    Fetches data from management commands and reports it to dumper.

    :type dumper _xml.XmlDumper
    :param dumper: destination to report
    """
    utility = ManagementUtility()
    for command_name in get_commands().keys():
        command = utility.fetch_command(command_name)
        assert isinstance(command, BaseCommand)
        dumper.start_command(command_name=command_name,
                             command_help_text=str(command.usage("").replace("%prog", command_name)), # TODO: support subcommands
                             command_args_text=str(command.args))
        for opt in command.option_list:
            opt_type = opt.type if opt.type in Option.TYPES else ""  # Empty for unknown
            # There is no official way to access this field, so I use protected one. At least it is public API.
            # noinspection PyProtectedMember
            dumper.add_command_option(
                opt_type=opt_type,
                choices=opt.choices,
                long_opt_names=opt._long_opts,
                short_opt_names=opt._short_opts,
                help_text=opt.help,
                num_of_args=opt.nargs)
        dumper.close_command()