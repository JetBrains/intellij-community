__author__ = 'ktisha'

import sys
from pycharm_run_utils import PYTHON_VERSION_MAJOR, PYTHON_VERSION_MINOR
#noinspection PyUnresolvedReferences
import pycharm_commands  # we need pycharm_commands module to be loaded

if __name__ == "__main__":
    test_suite = sys.argv.pop(-1)

    __file__ = test_suite
    sys.argv.append("--command-packages")
    sys.argv.append("pycharm_commands")
    sys.argv.append("pycharm_test")


    if PYTHON_VERSION_MINOR == 2 and PYTHON_VERSION_MAJOR == 4:
        #noinspection PyCompatibility
        execfile(test_suite)
    else:
        #noinspection PyCompatibility
        with open(test_suite, "r") as fh:
            exec (fh.read(), globals(), locals())
