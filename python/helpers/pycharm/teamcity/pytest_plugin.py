# coding=utf-8
"""
Aaron Buchanan
Nov. 2012

Plug-in for py.test for reporting to TeamCity server
Report results to TeamCity during test execution for immediate reporting
    when using TeamCity.

This should be installed as a py.test plugin and will be automatically enabled by running
tests under TeamCity build.
"""

import os
import sys
import re
import traceback
from datetime import timedelta

from teamcity.messages import TeamcityServiceMessages
from teamcity.common import convert_error_to_string, dump_test_stderr, dump_test_stdout
from teamcity import is_running_under_teamcity
from teamcity import diff_tools

diff_tools.patch_unittest_diff()


def fetch_diff_error_from_message(err_message):
    line_with_diff = None
    diff_error_message = None
    lines = err_message.split("\n")
    if err_message.startswith("AssertionError: assert"):
        # Everything in one line
        line_with_diff = lines[0][len("AssertionError: assert "):]
    elif len(err_message.split("\n")) > 1:
        err_line = lines[1]
        line_with_diff = err_line[len("assert "):]
        diff_error_message = lines[0]

    if line_with_diff and line_with_diff.count("==") == 1:
        parts = [x.strip() for x in line_with_diff.split("==")]
        parts = [s[1:-1] if s.startswith("'") or s.startswith('"') else s for s in parts]
        # Pytest cuts too long lines, no need to check is_too_big
        return diff_tools.EqualsAssertionError(parts[0], parts[1], diff_error_message)
    else:
        return None


def pytest_addoption(parser):
    group = parser.getgroup("terminal reporting", "reporting", after="general")

    group._addoption('--teamcity', action="count",
                     dest="teamcity", default=0, help="force output of JetBrains TeamCity service messages")
    group._addoption('--no-teamcity', action="count",
                     dest="no_teamcity", default=0, help="disable output of JetBrains TeamCity service messages")


def pytest_configure(config):
    if config.option.no_teamcity >= 1:
        enabled = False
    elif config.option.teamcity >= 1:
        enabled = True
    else:
        enabled = is_running_under_teamcity()

    if enabled:
        output_capture_enabled = getattr(config.option, 'capture', 'fd') != 'no'
        coverage_controller = _get_coverage_controller(config)

        config._teamcityReporting = EchoTeamCityMessages(output_capture_enabled, coverage_controller)
        config.pluginmanager.register(config._teamcityReporting)


def pytest_unconfigure(config):
    teamcity_reporting = getattr(config, '_teamcityReporting', None)
    if teamcity_reporting:
        del config._teamcityReporting
        config.pluginmanager.unregister(teamcity_reporting)


def _get_coverage_controller(config):
    cov_plugin = config.pluginmanager.getplugin('_cov')
    if not cov_plugin:
        return None

    return cov_plugin.cov_controller


class EchoTeamCityMessages(object):
    def __init__(self, output_capture_enabled, coverage_controller):
        self.coverage_controller = coverage_controller
        self.output_capture_enabled = output_capture_enabled

        self.teamcity = TeamcityServiceMessages()
        self.test_start_reported_mark = set()

        self.max_reported_output_size = 1 * 1024 * 1024
        self.reported_output_chunk_size = 50000

    def get_id_from_location(self, location):
        if type(location) is not tuple or len(location) != 3 or not hasattr(location[2], "startswith"):
            return None

        def convert_file_to_id(filename):
            filename = re.sub(r"\.pyc?$", "", filename)
            return filename.replace(os.sep, ".").replace("/", ".")

        def add_prefix_to_filename_id(filename_id, prefix):
            dot_location = filename_id.rfind('.')
            if dot_location <= 0 or dot_location >= len(filename_id) - 1:
                return None

            return filename_id[:dot_location + 1] + prefix + filename_id[dot_location + 1:]

        pylint_prefix = '[pylint] '
        if location[2].startswith(pylint_prefix):
            id_from_file = convert_file_to_id(location[2][len(pylint_prefix):])
            return id_from_file + ".Pylint"

        if location[2] == "PEP8-check":
            id_from_file = convert_file_to_id(location[0])
            return id_from_file + ".PEP8"

        return None

    def format_test_id(self, nodeid, location):
        id_from_location = self.get_id_from_location(location)

        if id_from_location is not None:
            return id_from_location

        test_id = nodeid

        if test_id:
            if test_id.find("::") < 0:
                test_id += "::top_level"
        else:
            test_id = "top_level"

        first_bracket = test_id.find("[")
        if first_bracket > 0:
            # [] -> (), make it look like nose parameterized tests
            params = "(" + test_id[first_bracket + 1:]
            if params.endswith("]"):
                params = params[:-1] + ")"
            test_id = test_id[:first_bracket]
            if test_id.endswith("::"):
                test_id = test_id[:-2]
        else:
            params = ""

        test_id = test_id.replace("::()::", "::")
        test_id = re.sub(r"\.pyc?::", r"::", test_id)
        test_id = test_id.replace(".", "_").replace(os.sep, ".").replace("/", ".").replace('::', '.')

        if params:
            params = params.replace(".", "_")
            test_id += params

        return test_id

    def format_location(self, location):
        if type(location) is tuple and len(location) == 3:
            return "%s:%s (%s)" % (str(location[0]), str(location[1]), str(location[2]))
        return str(location)

    def pytest_collection_modifyitems(self, session, config, items):
        self.teamcity.testCount(len(items))

    def pytest_runtest_logstart(self, nodeid, location):
        self.ensure_test_start_reported(self.format_test_id(nodeid, location))

    def ensure_test_start_reported(self, test_id):
        if test_id not in self.test_start_reported_mark:
            if self.output_capture_enabled:
                capture_standard_output = "false"
            else:
                capture_standard_output = "true"
            self.teamcity.testStarted(test_id, flowId=test_id, captureStandardOutput=capture_standard_output)
            self.test_start_reported_mark.add(test_id)

    def report_has_output(self, report):
        for (secname, data) in report.sections:
            if report.when in secname and ('stdout' in secname or 'stderr' in secname):
                return True
        return False

    def report_test_output(self, report, test_id):
        for (secname, data) in report.sections:
            # https://github.com/JetBrains/teamcity-messages/issues/112
            # CollectReport doesn't have 'when' property
            if hasattr(report, "when") and report.when not in secname:
                continue
            if not data:
                continue

            if 'stdout' in secname:
                dump_test_stdout(self.teamcity, test_id, test_id, data)
            elif 'stderr' in secname:
                dump_test_stderr(self.teamcity, test_id, test_id, data)

    def report_test_finished(self, test_id, duration=None):
        self.teamcity.testFinished(test_id, testDuration=duration, flowId=test_id)
        self.test_start_reported_mark.remove(test_id)

    def report_test_failure(self, test_id, report, message=None, report_output=True):
        if hasattr(report, 'duration'):
            duration = timedelta(seconds=report.duration)
        else:
            duration = None

        if message is None:
            message = self.format_location(report.location)

        self.ensure_test_start_reported(test_id)
        if report_output:
            self.report_test_output(report, test_id)

        diff_error = None
        try:
            err_message = str(report.longrepr.reprcrash.message)
            diff_name = diff_tools.EqualsAssertionError.__name__
            # There is a string like "foo.bar.DiffError: [serialized_data]"
            if diff_name in err_message:
                serialized_data = err_message[err_message.index(diff_name) + len(diff_name) + 1:]
                diff_error = diff_tools.deserialize_error(serialized_data)

            # AssertionError is patched in py.test, we can try to fetch diff from it
            # In general case message starts with "AssertionError: ", but can also starts with "assert" for top-level
            # function. To support both cases we unify them
            if err_message.startswith("assert"):
                err_message = "AssertionError: " + err_message
            if err_message.startswith("AssertionError:"):
                diff_error = fetch_diff_error_from_message(err_message)
        except:
            pass

        if diff_error:
            # Cut everything after postfix: it is internal view of DiffError
            strace = str(report.longrepr)
            data_postfix = "_ _ _ _ _"
            if data_postfix in strace:
                strace = strace[0:strace.index(data_postfix)]
            self.teamcity.testFailed(test_id, diff_error.msg if diff_error.msg else message, strace,
                                     flowId=test_id,
                                     comparison_failure=diff_error
                                     )
        else:
            self.teamcity.testFailed(test_id, message, str(report.longrepr), flowId=test_id)
        self.report_test_finished(test_id, duration)

    def report_test_skip(self, test_id, report):
        if type(report.longrepr) is tuple and len(report.longrepr) == 3:
            reason = report.longrepr[2]
        else:
            reason = str(report.longrepr)

        if hasattr(report, 'duration'):
            duration = timedelta(seconds=report.duration)
        else:
            duration = None

        self.ensure_test_start_reported(test_id)
        self.report_test_output(report, test_id)
        self.teamcity.testIgnored(test_id, reason, flowId=test_id)
        self.report_test_finished(test_id, duration)

    def pytest_runtest_logreport(self, report):
        """
        :type report: _pytest.runner.TestReport
        """
        test_id = self.format_test_id(report.nodeid, report.location)

        duration = timedelta(seconds=report.duration)

        if report.passed:
            # Do not report passed setup/teardown if no output
            if report.when == 'call':
                self.ensure_test_start_reported(test_id)
                self.report_test_output(report, test_id)
                self.report_test_finished(test_id, duration)
            else:
                if self.report_has_output(report):
                    block_name = "test " + report.when
                    self.teamcity.blockOpened(block_name, flowId=test_id)
                    self.report_test_output(report, test_id)
                    self.teamcity.blockClosed(block_name, flowId=test_id)
        elif report.failed:
            if report.when == 'call':
                self.report_test_failure(test_id, report)
            elif report.when == 'setup':
                if self.report_has_output(report):
                    self.teamcity.blockOpened("test setup", flowId=test_id)
                    self.report_test_output(report, test_id)
                    self.teamcity.blockClosed("test setup", flowId=test_id)

                self.report_test_failure(test_id, report, message="test setup failed", report_output=False)
            elif report.when == 'teardown':
                # Report failed teardown as a separate test as original test is already finished
                self.report_test_failure(test_id + "_teardown", report)
        elif report.skipped:
            self.report_test_skip(test_id, report)

    def pytest_collectreport(self, report):
        test_id = self.format_test_id(report.nodeid, report.location) + "_collect"

        if report.failed:
            self.report_test_failure(test_id, report)
        elif report.skipped:
            self.report_test_skip(test_id, report)

    def pytest_terminal_summary(self):
        if self.coverage_controller is not None:
            try:
                self._report_coverage()
            except:
                tb = traceback.format_exc()
                self.teamcity.customMessage("Coverage statistics reporting failed", "ERROR", errorDetails=tb)

    def _report_coverage(self):
        from coverage.misc import NotPython
        from coverage.report import Reporter
        from coverage.results import Numbers

        class _CoverageReporter(Reporter):
            def __init__(self, coverage, config, messages):
                super(_CoverageReporter, self).__init__(coverage, config)

                self.branches = coverage.data.has_arcs()
                self.messages = messages

            def report(self, morfs, outfile=None):
                if hasattr(self, 'find_code_units'):
                    self.find_code_units(morfs)
                else:
                    self.find_file_reporters(morfs)

                total = Numbers()

                if hasattr(self, 'code_units'):
                    units = self.code_units
                else:
                    units = self.file_reporters

                for cu in units:
                    try:
                        analysis = self.coverage._analyze(cu)
                        nums = analysis.numbers
                        total += nums
                    except KeyboardInterrupt:
                        raise
                    except:
                        if self.config.ignore_errors:
                            continue

                        err = sys.exc_info()
                        typ, msg = err[:2]
                        if typ is NotPython and not cu.should_be_python():
                            continue

                        test_id = cu.name
                        details = convert_error_to_string(err)

                        self.messages.testStarted(test_id, flowId=test_id)
                        self.messages.testFailed(test_id, message="Coverage analysis failed", details=details, flowId=test_id)
                        self.messages.testFinished(test_id, flowId=test_id)

                if total.n_files > 0:
                    covered = total.n_executed
                    total_statements = total.n_statements

                    if self.branches:
                        covered += total.n_executed_branches
                        total_statements += total.n_branches

                    self.messages.buildStatisticLinesCovered(covered)
                    self.messages.buildStatisticTotalLines(total_statements)
                    self.messages.buildStatisticLinesUncovered(total_statements - covered)
        reporter = _CoverageReporter(
            self.coverage_controller.cov,
            self.coverage_controller.cov.config,
            self.teamcity,
        )
        reporter.report(None)
