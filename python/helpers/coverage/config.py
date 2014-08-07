"""Config file for coverage.py"""

import os, re, sys
from coverage.backward import string_class, iitems

# In py3, # ConfigParser was renamed to the more-standard configparser
try:
    import configparser                             # pylint: disable=F0401
except ImportError:
    import ConfigParser as configparser


class HandyConfigParser(configparser.RawConfigParser):
    """Our specialization of ConfigParser."""

    def read(self, filename):
        """Read a filename as UTF-8 configuration data."""
        kwargs = {}
        if sys.version_info >= (3, 2):
            kwargs['encoding'] = "utf-8"
        return configparser.RawConfigParser.read(self, filename, **kwargs)

    def get(self, *args, **kwargs):
        v = configparser.RawConfigParser.get(self, *args, **kwargs)
        def dollar_replace(m):
            """Called for each $replacement."""
            # Only one of the groups will have matched, just get its text.
            word = [w for w in m.groups() if w is not None][0]
            if word == "$":
                return "$"
            else:
                return os.environ.get(word, '')

        dollar_pattern = r"""(?x)   # Use extended regex syntax
            \$(?:                   # A dollar sign, then
            (?P<v1>\w+) |           #   a plain word,
            {(?P<v2>\w+)} |         #   or a {-wrapped word,
            (?P<char>[$])           #   or a dollar sign.
            )
            """
        v = re.sub(dollar_pattern, dollar_replace, v)
        return v

    def getlist(self, section, option):
        """Read a list of strings.

        The value of `section` and `option` is treated as a comma- and newline-
        separated list of strings.  Each value is stripped of whitespace.

        Returns the list of strings.

        """
        value_list = self.get(section, option)
        values = []
        for value_line in value_list.split('\n'):
            for value in value_line.split(','):
                value = value.strip()
                if value:
                    values.append(value)
        return values

    def getlinelist(self, section, option):
        """Read a list of full-line strings.

        The value of `section` and `option` is treated as a newline-separated
        list of strings.  Each value is stripped of whitespace.

        Returns the list of strings.

        """
        value_list = self.get(section, option)
        return list(filter(None, value_list.split('\n')))


# The default line exclusion regexes
DEFAULT_EXCLUDE = [
    '(?i)# *pragma[: ]*no *cover',
    ]

# The default partial branch regexes, to be modified by the user.
DEFAULT_PARTIAL = [
    '(?i)# *pragma[: ]*no *branch',
    ]

# The default partial branch regexes, based on Python semantics.
# These are any Python branching constructs that can't actually execute all
# their branches.
DEFAULT_PARTIAL_ALWAYS = [
    'while (True|1|False|0):',
    'if (True|1|False|0):',
    ]


class CoverageConfig(object):
    """Coverage.py configuration.

    The attributes of this class are the various settings that control the
    operation of coverage.py.

    """
    def __init__(self):
        """Initialize the configuration attributes to their defaults."""
        # Metadata about the config.
        self.attempted_config_files = []
        self.config_files = []

        # Defaults for [run]
        self.branch = False
        self.cover_pylib = False
        self.data_file = ".coverage"
        self.parallel = False
        self.timid = False
        self.source = None
        self.debug = []

        # Defaults for [report]
        self.exclude_list = DEFAULT_EXCLUDE[:]
        self.ignore_errors = False
        self.include = None
        self.omit = None
        self.partial_list = DEFAULT_PARTIAL[:]
        self.partial_always_list = DEFAULT_PARTIAL_ALWAYS[:]
        self.precision = 0
        self.show_missing = False

        # Defaults for [html]
        self.html_dir = "htmlcov"
        self.extra_css = None
        self.html_title = "Coverage report"

        # Defaults for [xml]
        self.xml_output = "coverage.xml"

        # Defaults for [paths]
        self.paths = {}

    def from_environment(self, env_var):
        """Read configuration from the `env_var` environment variable."""
        # Timidity: for nose users, read an environment variable.  This is a
        # cheap hack, since the rest of the command line arguments aren't
        # recognized, but it solves some users' problems.
        env = os.environ.get(env_var, '')
        if env:
            self.timid = ('--timid' in env)

    MUST_BE_LIST = ["omit", "include", "debug"]

    def from_args(self, **kwargs):
        """Read config values from `kwargs`."""
        for k, v in iitems(kwargs):
            if v is not None:
                if k in self.MUST_BE_LIST and isinstance(v, string_class):
                    v = [v]
                setattr(self, k, v)

    def from_file(self, filename):
        """Read configuration from a .rc file.

        `filename` is a file name to read.

        """
        self.attempted_config_files.append(filename)

        cp = HandyConfigParser()
        files_read = cp.read(filename)
        if files_read is not None:  # return value changed in 2.4
            self.config_files.extend(files_read)

        for option_spec in self.CONFIG_FILE_OPTIONS:
            self.set_attr_from_config_option(cp, *option_spec)

        # [paths] is special
        if cp.has_section('paths'):
            for option in cp.options('paths'):
                self.paths[option] = cp.getlist('paths', option)

    CONFIG_FILE_OPTIONS = [
        # [run]
        ('branch', 'run:branch', 'boolean'),
        ('cover_pylib', 'run:cover_pylib', 'boolean'),
        ('data_file', 'run:data_file'),
        ('debug', 'run:debug', 'list'),
        ('include', 'run:include', 'list'),
        ('omit', 'run:omit', 'list'),
        ('parallel', 'run:parallel', 'boolean'),
        ('source', 'run:source', 'list'),
        ('timid', 'run:timid', 'boolean'),

        # [report]
        ('exclude_list', 'report:exclude_lines', 'linelist'),
        ('ignore_errors', 'report:ignore_errors', 'boolean'),
        ('include', 'report:include', 'list'),
        ('omit', 'report:omit', 'list'),
        ('partial_list', 'report:partial_branches', 'linelist'),
        ('partial_always_list', 'report:partial_branches_always', 'linelist'),
        ('precision', 'report:precision', 'int'),
        ('show_missing', 'report:show_missing', 'boolean'),

        # [html]
        ('html_dir', 'html:directory'),
        ('extra_css', 'html:extra_css'),
        ('html_title', 'html:title'),

        # [xml]
        ('xml_output', 'xml:output'),
        ]

    def set_attr_from_config_option(self, cp, attr, where, type_=''):
        """Set an attribute on self if it exists in the ConfigParser."""
        section, option = where.split(":")
        if cp.has_option(section, option):
            method = getattr(cp, 'get'+type_)
            setattr(self, attr, method(section, option))
