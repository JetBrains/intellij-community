import sys

IS_PY3K = False

try:
    if sys.version_info[0] >= 3:
        IS_PY3K = True
except AttributeError:
    pass  #Not all versions have sys.version_info


if IS_PY3K:
    pass
else:
    pass

