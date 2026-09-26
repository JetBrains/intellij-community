# coding=utf-8
# A package that only holds step modules. It registers no steps itself, so it must
# be left in sys.modules: dropping it while keeping its children desynchronizes the
# package (PY-91210).
from nested_pkg.counters import count_execution

count_execution('nested_pkg')
