import sys
import types

_sub = types.ModuleType('mod2.sub')
_sub.VALUE = 42
sys.modules['mod2.sub'] = _sub
