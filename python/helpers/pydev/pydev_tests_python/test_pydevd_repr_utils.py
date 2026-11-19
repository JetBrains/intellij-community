import os
import subprocess
import sys

import pytest

_HELPERS_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Both debugger backends keep a copy of the module.
_REPR_UTILS = {
    'pydevd': (os.path.join(_HELPERS_DIR, 'pydev'), '_pydevd_bundle.pydevd_repr_utils'),
    'debugpy': (os.path.join(_HELPERS_DIR, 'debugpy', '_vendored', 'pydevd'), '_pydevd_bundle.custom.pydevd_repr_utils'),
}

_CHECK_SCRIPT = '''
import importlib
import sys
import tracemalloc

sys.path.insert(0, sys.argv[1])
get_value_repr = importlib.import_module(sys.argv[2]).get_value_repr
value_type = {'bytes': bytes, 'bytearray': bytearray}[sys.argv[3]]

value = value_type(64 << 20)
tracemalloc.start()
result = get_value_repr(value)
peak = tracemalloc.get_traced_memory()[1]
tracemalloc.stop()

full_repr = repr(value_type(1000))
expected = full_repr[:126] + '...' + full_repr[-127:]
assert result == expected, (result, expected)
assert peak < 1 << 20, 'the repr took %s bytes at peak' % peak
'''


@pytest.mark.skipif(sys.version_info[0] < 3, reason='Python 2 has no PydevdRepr')
@pytest.mark.parametrize('backend', sorted(_REPR_UTILS))
@pytest.mark.parametrize('value_type', ['bytes', 'bytearray'])
def test_trimmed_repr_of_large_bytes_does_not_copy_the_value(backend, value_type):
    root, module = _REPR_UTILS[backend]
    subprocess.check_call([sys.executable, '-c', _CHECK_SCRIPT, root, module, value_type])
