# coding=utf-8
"""
Fetches arguments from argparse-based Django (1.8+)
"""
from argparse import Action, _StoreTrueAction, _StoreFalseAction

from django_manage_commands_provider._parser import _utils


__author__ = 'Ilya.Kazakevich'


# noinspection PyUnusedLocal
# Command here by contract
def process_command(dumper, command, parser):
    """
    Fetches arguments and options from command and parser and reports em to dumper.

    :param dumper dumper to output data to
    :param parser arg parser to use
    :param command django command

    :type dumper _xml.XmlDumper
    :type parser argparse.ArgumentParser
    :type command django.core.management.base.BaseCommand
    """

    argument_names = []
    # No public API to fetch actions from argparse.
    # noinspection PyProtectedMember
    for action in parser._actions:
        assert isinstance(action, Action)
        if action.option_strings:
            # Long opts start with --
            long_opt_names = set(filter(lambda opt_name: str(opt_name).startswith("--"), action.option_strings))
            # All other opts are short
            short_opt_names = set(action.option_strings) - long_opt_names
            argument_info = None
            # The only difference between bool option and argument-based option is the one has store=True
            bool_option = isinstance(action, _StoreTrueAction) or isinstance(action, _StoreFalseAction)
            if not bool_option:
                # TODO: Support nargs. It can be +, ?, * and number. Not only 1.
                argument_info = (1, _utils.get_opt_type(action))

            dumper.add_command_option(long_opt_names, short_opt_names, str(action.help), argument_info)
        else:
            # TODO: Fetch optionality/mandatority from argument info because it has nargs field
            argument_names.append("[" + str(action.metavar if action.metavar else action.dest) + "]")

    dumper.set_arguments(" ".join(argument_names))