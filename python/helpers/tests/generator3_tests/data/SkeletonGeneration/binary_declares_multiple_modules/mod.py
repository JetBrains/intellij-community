import _foo
import _bar
import sys

sys.modules['foo'] = _foo
del sys.modules['foo'].__file__
sys.modules['bar'] = _bar
del sys.modules['bar'].__file__

del sys
del _bar
del _foo
