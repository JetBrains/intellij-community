import os
import sys
import unittest

_tests_dir = os.path.dirname(os.path.abspath(__file__))
_helpers_dir = os.path.dirname(_tests_dir)


def discover_and_run_tests():
    suite = unittest.TestLoader().discover(_tests_dir)
    runner = unittest.TextTestRunner()
    try:
        import teamcity.unittestpy
        if teamcity.is_running_under_teamcity():
            runner = teamcity.unittestpy.TeamcityTestRunner()
    except ImportError:
        pass
    runner.run(suite)


if __name__ == '__main__':
    sys.path.append(_helpers_dir)
    discover_and_run_tests()
