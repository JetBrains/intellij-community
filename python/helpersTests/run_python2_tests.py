import os
import sys

import pytest


current_dir = os.path.dirname(os.path.abspath(__file__))
python_plugin_dir = os.path.abspath(os.path.join(current_dir, '..'))

helpers_dir = os.path.join(python_plugin_dir, "helpers")
pycharm_dir = os.path.join(helpers_dir, "pycharm")
py3only_dir = os.path.join(helpers_dir, "py3only")

sys.path.insert(0, helpers_dir)
sys.path.insert(0, pycharm_dir)
sys.path.insert(0, py3only_dir)

exit_code = pytest.main(["--junit-xml=.tox/reports/junit-py27.xml", "tests"])
sys.exit(exit_code)
