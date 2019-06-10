import os
import sys
import unittest

_tests_dir = os.path.dirname(os.path.abspath(__file__))
_helpers_dir = os.path.dirname(_tests_dir)


def run_specified_tests():
    runner = get_test_runner()
    unittest.main(module=None, testRunner=runner, argv=sys.argv)


def discover_and_run_all_tests():
    runner = get_test_runner()
    suite = unittest.TestLoader().discover(_tests_dir)
    runner.run(suite)


def get_test_runner():
    try:
        import teamcity
        if teamcity.is_running_under_teamcity():
            from teamcity.unittestpy import TeamcityTestRunner, TeamcityTestResult

            class PythonVersionAwareTestResultClass(TeamcityTestResult):
                @staticmethod
                def get_test_id(test):
                    major, minor = sys.version_info[:2]
                    interpreter_id = 'py{}{}'.format(major, minor)
                    return '{}.{}'.format(interpreter_id, TeamcityTestResult.get_test_id(test))

            class PythonVersionAwareTeamcityTestRunner(TeamcityTestRunner):
                resultclass = PythonVersionAwareTestResultClass

            return PythonVersionAwareTeamcityTestRunner(buffer=True)
    except ImportError:
        pass
    return unittest.TextTestRunner()


if __name__ == '__main__':
    sys.path.append(_helpers_dir)

    if len(sys.argv) > 1:
        run_specified_tests()
    else:
        discover_and_run_all_tests()
