import re
from io import BytesIO

from flake8.formatting import base

from teamcity.messages import TeamcityServiceMessages
from teamcity import __version__, is_running_under_teamcity


class TeamcityReport(base.BaseFormatter):
    name = 'teamcity-messages'
    version = __version__

    @staticmethod
    def _add_option(parser, name, *args, **kwargs):
        if all(option.long_option_name != name for option in parser.options):
            parser.add_option(name, *args, **kwargs)

    @classmethod
    def add_options(cls, parser):
        cls._add_option(parser,
                        '--teamcity',
                        default=is_running_under_teamcity(),
                        help="Force output of JetBrains TeamCity service messages")
        cls._add_option(parser,
                        '--no-teamcity',
                        default=False,
                        help="Disable output of JetBrains TeamCity service messages (even under TeamCity build)")

    @classmethod
    def parse_options(cls, options):
        if not options.no_teamcity:
            if options.teamcity or is_running_under_teamcity():
                options.format = 'teamcity-messages'

    def format(self, error):
        normalized_filename = error.filename.replace("\\", "/")
        position = '%s:%d:%d' % (
            normalized_filename, error.line_number, error.column_number)
        error_message = '%s %s' % (error.code, error.text)
        test_name = 'pep8: %s: %s' % (normalized_filename, error_message)

        line = error.physical_line
        offset = error.column_number
        details = [
            position,
            line.rstrip(),
            re.sub(r'\S', ' ', line[:offset]) + '^',
        ]
        details = '\n'.join(details)

        bytesio = BytesIO()
        messages = TeamcityServiceMessages(output=bytesio)

        messages.testStarted(test_name)
        messages.testFailed(test_name, error_message, details)
        messages.testFinished(test_name)

        return bytesio.getvalue().decode('UTF-8')
