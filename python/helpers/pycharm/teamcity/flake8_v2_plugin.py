try:
    import pycodestyle as pep8
except ImportError:
    import pep8

import re

from teamcity.messages import TeamcityServiceMessages
from teamcity import __version__, is_running_under_teamcity


enable_teamcity = is_running_under_teamcity()


class TeamcityReport(pep8.StandardReport):
    name = 'teamcity'
    version = __version__
    options_added = False

    @classmethod
    def add_options(cls, parser):
        if not cls.options_added:
            parser.add_option('--teamcity', default=False,
                              action='callback', callback=cls.set_option_callback,
                              help="Enable teamcity messages")
            cls.options_added = True

    @classmethod
    def set_option_callback(cls, option, opt, value, parser):
        global enable_teamcity
        enable_teamcity = True

    @classmethod
    def parse_options(cls, options):
        if not enable_teamcity:
            return

        options.reporter = TeamcityReport
        options.report = TeamcityReport(options)
        options.jobs = None  # needs to be disabled, flake8 overrides the report if enabled

    def get_file_results(self):
        self._deferred_print.sort()

        messages = TeamcityServiceMessages()

        normalized_filename = self.filename.replace("\\", "/")

        for line_number, offset, code, text, doc in self._deferred_print:
            position = '%(path)s:%(row)d:%(col)d' % {
                'path': normalized_filename,
                'row': self.line_offset + line_number,
                'col': offset + 1,
            }

            error_message = '%s %s' % (code, text)
            test_name = 'pep8: %s: %s' % (normalized_filename, error_message)

            messages.testStarted(test_name)

            if line_number > len(self.lines):
                line = ''
            else:
                line = self.lines[line_number - 1]

            details = [
                position,
                line.rstrip(),
                re.sub(r'\S', ' ', line[:offset]) + '^',
            ]

            if doc:
                details.append(doc.strip())

            details = '\n'.join(details)

            messages.testFailed(test_name, error_message, details)
            messages.testFinished(test_name)
        return self.file_errors
