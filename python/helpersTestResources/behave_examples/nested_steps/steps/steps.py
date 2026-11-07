# coding=utf-8
# behave executes every *.py of this directory directly. The steps themselves live
# in a nested package reached by a plain import, which is the layout from PY-86174.
use_step_matcher("re")

from nested_pkg import more_steps  # noqa: F401
from nested_pkg.counters import count_execution

count_execution('steps')
