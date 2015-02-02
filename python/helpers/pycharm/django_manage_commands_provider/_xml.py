# coding=utf-8
"""
This module exports information about manage commands and options from django to PyCharm.
Information is provided in XML (to prevent encoding troubles and simplify deserialization on java side).
It does not have schema (yet!) but here is XML format it uses.

<commandInfo-array> -- root
<commandInfo args="args description" help="human readable text" name="command name"> -- info about command
<option help="option help" numberOfArgs="number of values (nargs)" type="option type: Option.TYPES"> -- one entry for each option
<longNames>--each-for-one-long-opt-name</longNames>
<shortNames>-each-for-one-short-name</shortNames>
</option>
</commandInfo>
</commandInfo-array>

Classes like DjangoCommandsInfo is used on Java side.

"""
from xml.dom import minidom
from xml.dom.minidom import Element

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
            text = self.__document.createTextNode(value)
            tag.appendChild(text)
            parent.appendChild(tag)

    def start_command(self, command_name, command_help_text, command_args_text):
        """
        Starts manage command

        :param command_name: command name
        :param command_help_text: command help
        :param command_args_text: command text for args


        """
        assert not bool(self.__command_element), "Already in command"
        self.__command_element = self.__document.createElement(XmlDumper.__command_info_tag)
        self.__command_element.setAttribute("name", command_name)
        self.__command_element.setAttribute("help", command_help_text)
        self.__command_element.setAttribute("args", command_args_text)
        self.__root.appendChild(self.__command_element)

    def add_command_option(self, opt_type, choices, long_opt_names, short_opt_names, help_text, num_of_args):
        """
        Adds command option

        :param opt_type: "string", "int", "long", "float", "complex", "choice"
        :param choices: list of choices for "choice" type
        :param long_opt_names:  list of long opt names
        :param short_opt_names: list of short opt names
        :param help_text: help text
        :param num_of_args: number of arguments

        :type opt_type str
        :type choices list of string
        :type long_opt_names list of str
        :type short_opt_names list of str
        :type help_text str
        :type num_of_args int
        """
        assert isinstance(self.__command_element, Element), "Add option in command only"
        option = self.__document.createElement("option")
        option.setAttribute("type", opt_type)

        if choices:
            self.__create_text_array(option, "choices", choices)
        if long_opt_names:
            self.__create_text_array(option, "longNames", long_opt_names)
        if short_opt_names:
            self.__create_text_array(option, "shortNames", short_opt_names)

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
        return self.__document.toprettyxml()
