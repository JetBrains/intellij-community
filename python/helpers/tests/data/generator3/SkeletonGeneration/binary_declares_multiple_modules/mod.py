import _foo
import _bar
import sys

sys.modules['mod.foo'] = _foo
del sys.modules['mod.foo'].__file__
sys.modules['mod.bar'] = _bar
del sys.modules['mod.bar'].__file__

del sys
del _bar
del _foo
