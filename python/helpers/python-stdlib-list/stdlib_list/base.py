from __future__ import print_function, absolute_import

import os
import sys

base_dir = os.path.dirname(os.path.realpath(__file__))

list_dir = os.path.join(base_dir, "lists")

long_versions = ["2.6.9", "2.7.9", "3.2.6", "3.3.6", "3.4.3", "3.5", "3.6", "3.7"]

short_versions = [".".join(x.split(".")[:2]) for x in long_versions]


def get_canonical_version(version):

    if version in long_versions:
        version = ".".join(version.split(".")[:2])
    elif version not in short_versions:
        raise ValueError("No such version: {}".format(version))

    return version


def stdlib_list(version=None):
    """
    Given a ``version``, return a ``list`` of names of the Python Standard
    Libraries for that version. These names are obtained from the Sphinx inventory
    file (used in :py:mod:`sphinx.ext.intersphinx`).

    :param str|None version: The version (as a string) whose list of libraries you want
    (one of ``"2.6"``, ``"2.7"``, ``"3.2"``, ``"3.3"``, ``"3.4"``, or ``"3.5"``).
    If not specified, the current version of Python will be used.

    :return: A list of standard libraries from the specified version of Python
    :rtype: list
    """

    version = get_canonical_version(version) if version is not None else '.'.join(
        str(x) for x in sys.version_info[:2])

    module_list_file = os.path.join(list_dir, "{}.txt".format(version))

    with open(module_list_file) as f:
        result = [y for y in [x.strip() for x in f.readlines()] if y]

    return result
