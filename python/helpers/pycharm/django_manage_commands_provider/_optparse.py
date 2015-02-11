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
                             command_help_text=str(command.usage("").replace("%prog", command_name)),
                             # TODO: support subcommands
                             command_args_text=str(command.args))
        for opt in command.option_list:
            num_of_args = int(opt.nargs) if opt.nargs else 0
            opt_type = None
            if num_of_args > 0:
                # If option accepts arg, we need to determine its type. It could be int, choices, or something other
                # See https://docs.python.org/2/library/optparse.html#standard-option-types
                if opt.type in ["int", "long"]:
                    opt_type = "int"
                elif opt.choices:
                    assert isinstance(opt.choices, list), "Choices should be list"
                    opt_type = opt.choices

            # There is no official way to access this field, so I use protected one. At least it is public API.
            # noinspection PyProtectedMember
            dumper.add_command_option(
                long_opt_names=opt._long_opts,
                short_opt_names=opt._short_opts,
                help_text=opt.help,
                argument_info=(num_of_args, opt_type) if num_of_args else None)
        dumper.close_command()