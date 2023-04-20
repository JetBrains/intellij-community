# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""TOML configuration support for coverage.py"""

import io
import os
import re

from coverage import env
from coverage.backward import configparser, path_types
from coverage.misc import CoverageException, substitute_variables

# TOML support is an install-time extra option.
try:
    import toml
except ImportError:         # pragma: not covered
    toml = None


class TomlDecodeError(Exception):
    """An exception class that exists even when toml isn't installed."""
    pass


class TomlConfigParser:
    """TOML file reading with the interface of HandyConfigParser."""

    # This class has the same interface as config.HandyConfigParser, no
    # need for docstrings.
    # pylint: disable=missing-function-docstring

    def __init__(self, our_file):
        self.our_file = our_file
        self.data = None

    def read(self, filenames):
        # RawConfigParser takes a filename or list of filenames, but we only
        # ever call this with a single filename.
        assert isinstance(filenames, path_types)
        filename = filenames
        if env.PYVERSION >= (3, 6):
            filename = os.fspath(filename)

        try:
            with io.open(filename, encoding='utf-8') as fp:
                toml_text = fp.read()
        except IOError:
            return []
        if toml:
            toml_text = substitute_variables(toml_text, os.environ)
            try:
                self.data = toml.loads(toml_text)
            except toml.TomlDecodeError as err:
                raise TomlDecodeError(*err.args)
            return [filename]
        else:
            has_toml = re.search(r"^\[tool\.coverage\.", toml_text, flags=re.MULTILINE)
            if self.our_file or has_toml:
                # Looks like they meant to read TOML, but we can't read it.
                msg = "Can't read {!r} without TOML support. Install with [toml] extra"
                raise CoverageException(msg.format(filename))
            return []

    def _get_section(self, section):
        """Get a section from the data.

        Arguments:
            section (str): A section name, which can be dotted.

        Returns:
            name (str): the actual name of the section that was found, if any,
                or None.
            data (str): the dict of data in the section, or None if not found.

        """
        prefixes = ["tool.coverage."]
        if self.our_file:
            prefixes.append("")
        for prefix in prefixes:
            real_section = prefix + section
            parts = real_section.split(".")
            try:
                data = self.data[parts[0]]
                for part in parts[1:]:
                    data = data[part]
            except KeyError:
                continue
            break
        else:
            return None, None
        return real_section, data

    def _get(self, section, option):
        """Like .get, but returns the real section name and the value."""
        name, data = self._get_section(section)
        if data is None:
            raise configparser.NoSectionError(section)
        try:
            return name, data[option]
        except KeyError:
            raise configparser.NoOptionError(option, name)

    def has_option(self, section, option):
        _, data = self._get_section(section)
        if data is None:
            return False
        return option in data

    def has_section(self, section):
        name, _ = self._get_section(section)
        return name

    def options(self, section):
        _, data = self._get_section(section)
        if data is None:
            raise configparser.NoSectionError(section)
        return list(data.keys())

    def get_section(self, section):
        _, data = self._get_section(section)
        return data

    def get(self, section, option):
        _, value = self._get(section, option)
        return value

    def _check_type(self, section, option, value, type_, type_desc):
        if not isinstance(value, type_):
            raise ValueError(
                'Option {!r} in section {!r} is not {}: {!r}'
                    .format(option, section, type_desc, value)
            )

    def getboolean(self, section, option):
        name, value = self._get(section, option)
        self._check_type(name, option, value, bool, "a boolean")
        return value

    def getlist(self, section, option):
        name, values = self._get(section, option)
        self._check_type(name, option, values, list, "a list")
        return values

    def getregexlist(self, section, option):
        name, values = self._get(section, option)
        self._check_type(name, option, values, list, "a list")
        for value in values:
            value = value.strip()
            try:
                re.compile(value)
            except re.error as e:
                raise CoverageException(
                    "Invalid [%s].%s value %r: %s" % (name, option, value, e)
                )
        return values

    def getint(self, section, option):
        name, value = self._get(section, option)
        self._check_type(name, option, value, int, "an integer")
        return value

    def getfloat(self, section, option):
        name, value = self._get(section, option)
        if isinstance(value, int):
            value = float(value)
        self._check_type(name, option, value, float, "a float")
        return value
