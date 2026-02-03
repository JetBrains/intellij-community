import sys

setattr(sys.modules[__name__], 'illegal name\n', type('illegal name\n', (object,), {}))
del sys
