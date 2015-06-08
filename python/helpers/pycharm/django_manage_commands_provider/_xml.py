# coding=utf-8
"""
This module exports information about manage commands and options from django to PyCharm.
Information is provided in XML (to prevent encoding troubles and simplify deserialization on java side).

Right after xml declaration, before root tag it contains following comment:
<!--jb pycharm data start-->

Use it to make sure you found correct XML

It does not have schema (yet!) but here is XML format it uses.

<commandInfo-array> -- root
<commandInfo args="args description" help="human readable text" name="command name"> -- info about command
<option help="option help" numberOfArgs="number of values (nargs)" type="option_type (see below)"> -- one entry for each option
<longNames>--each-for-one-long-opt-name</longNames>
<shortNames>-each-for-one-short-name</shortNames>
<choices>--each-for-one-available-value</choices>
</option>
</commandInfo>
</commandInfo-array>

"option_type" is only set if "numberOfArgs" > 0, and it can be: "int" (means integer),
"choices" (means opt can have one of the values, provided in choices) or "str" that means "string" (option may have any value)

Classes like DjangoCommandsInfo is used on Java side.

TODO: Since Django 1.8 we can fetch much more info from argparse like positional argument names, nargs etc. Use it!

"""
from xml.dom import minidom
from xml.dom.minidom import Element
from _jb_utils import VersionAgnosticUtils

__author__ = 'Ilya.Kazakevich'


class XmlDumper(object):
    """"
    Creates an API to generate XML provided in this package.
    How to use:
    * dumper.start_command(..)
    * dumper.add_command_option(..) # optional
    * dumper.close_command()
    * print(dumper.xml)

    """

    __command_info_tag = "commandInfo"  # Name of main tag

    def __init__(self):
        self.__document = minidom.Document()
        self.__root = self.__document.createElement("{0}-array".format(XmlDumper.__command_info_tag))
        self.__document.appendChild(self.__document.createComment("jb pycharm data start"))
        self.__document.appendChild(self.__root)
        self.__command_element = None

    def __create_text_array(self, parent, tag_name, values):
        """
        Creates array of text elements and adds them to parent

        :type parent Element
        :type tag_name str
        :type values list of str

        :param parent destination to add new elements
        :param tag_name name tag to create to hold text
        :param values list of values to add

        """
        for value in values:
            tag = self.__document.createElement(tag_name)
            text = self.__document.createTextNode(str(value))
            tag.appendChild(text)
            parent.appendChild(tag)

    def start_command(self, command_name, command_help_text):
        """
        Starts manage command

        :param command_name: command name
        :param command_help_text: command help


        """
        assert not bool(self.__command_element), "Already in command"
        self.__command_element = self.__document.createElement(XmlDumper.__command_info_tag)
        self.__command_element.setAttribute("name", command_name)
        self.__command_element.setAttribute("help", command_help_text)
        self.__root.appendChild(self.__command_element)

    def set_arguments(self, command_args_text):
        """
        Adds "arguments help" to command.

                TODO: Use real list of arguments instead of this text when people migrate to argparse (Dj. 1.8)

        :param command_args_text: command text for args
        :type command_args_text str
        """
        assert bool(self.__command_element), "Not in a a command"
        self.__command_element.setAttribute("args", VersionAgnosticUtils().to_unicode(command_args_text))

    def add_command_option(self, long_opt_names, short_opt_names, help_text, argument_info):
        """
        Adds command option

        :param argument_info: None if option does not accept any arguments or tuple of (num_of_args, type_info) \
                where num_of_args is int > 0 and type_info is str, representing type (only "int" and "string" are supported) \
                or list of available types in case of choices

        :param long_opt_names:  list of long opt names
        :param short_opt_names: list of short opt names
        :param help_text: help text

        :type long_opt_names iterable of str
        :type short_opt_names iterable of str
        :type help_text str
        :type argument_info tuple or None
        """
        assert isinstance(self.__command_element, Element), "Add option in command only"

        option = self.__document.createElement("option")

        opt_type_to_report = None
        num_of_args = 0

        if argument_info:
            (num_of_args, type_info) = argument_info
            if isinstance(type_info, list):
                self.__create_text_array(option, "choices", type_info)
                opt_type_to_report = "choices"
            else:
                opt_type_to_report = "int" if str(type_info) == "int" else "str"

        if long_opt_names:
            self.__create_text_array(option, "longNames", long_opt_names)
        if short_opt_names:
            self.__create_text_array(option, "shortNames", short_opt_names)

        if opt_type_to_report:
            option.setAttribute("type", opt_type_to_report)
        option.setAttribute("help", help_text)
        if num_of_args:
            option.setAttribute("numberOfArgs", str(num_of_args))
        self.__command_element.appendChild(option)

    def close_command(self):
        """
        Closes currently opened command
        """
        assert bool(self.__command_element), "No command to close"
        self.__command_element = None
        pass

    @property
    def xml(self):
        """

        :return: current commands as XML as described in package
        :rtype str
        """
        document = self.__document.toxml(encoding="utf-8")
        return VersionAgnosticUtils().to_unicode(document.decode("utf-8") if isinstance(document, bytes) else document)
