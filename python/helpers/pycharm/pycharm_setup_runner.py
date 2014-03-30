__author__ = 'ktisha'

import sys
from pycharm_run_utils import PYTHON_VERSION_MAJOR, PYTHON_VERSION_MINOR
#noinspection PyUnresolvedReferences
import pycharm_commands  # we need pycharm_commands module to be loaded

if __name__ == "__main__":
    parameters = []

    test_suite = sys.argv.pop(-1)
    while test_suite.startswith("-"):
      parameters.append(test_suite)
      test_suite = sys.argv.pop(-1)

    sys.argv = [test_suite, "--command-packages", "pycharm_commands", "pycharm_test"]
    sys.argv.extend(parameters)
    __file__ = test_suite

    if PYTHON_VERSION_MINOR == 2 and PYTHON_VERSION_MAJOR == 4:
        #noinspection PyCompatibility
        execfile(test_suite)
    else:
        #noinspection PyCompatibility
        with open(test_suite, "r") as fh:
            exec (fh.read(), globals(), locals())
