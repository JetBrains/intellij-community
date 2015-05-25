# coding=utf-8
"""
Fetches arguments from optparse-based Django (< 1.8)
"""
__author__ = 'Ilya.Kazakevich'
from django_manage_commands_provider._parser import _utils


# noinspection PyUnusedLocal
# Parser here by contract
def process_command(dumper, command, parser):
    """
    Fetches arguments and options from command and parser and reports em to dumper.

    :param dumper dumper to output data to
    :param parser opt parser to use
    :param command django command

    :type dumper _xml.XmlDumper
    :type parser optparse.OptionParser
    :type command django.core.management.base.BaseCommand
    """
    dumper.set_arguments(str(command.args)) # args should be string, but in some buggy cases it is not
    # TODO: support subcommands
    for opt in command.option_list:
        num_of_args = int(opt.nargs) if opt.nargs else 0
        opt_type = None
        if num_of_args > 0:
            opt_type = _utils.get_opt_type(opt)

        # There is no official way to access this field, so I use protected one. At least it is public API.
        # noinspection PyProtectedMember
        dumper.add_command_option(
            long_opt_names=opt._long_opts,
            short_opt_names=opt._short_opts,
            help_text=opt.help,
            argument_info=(num_of_args, opt_type) if num_of_args else None)
