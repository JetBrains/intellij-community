import os

if os.environ['GENERATOR3_VERSION'] == '0.1':
    raise RuntimeError

del os
