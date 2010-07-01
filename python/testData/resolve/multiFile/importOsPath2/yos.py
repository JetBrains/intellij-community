import sys

if True:
    import ypath as path
else:
    import zpath as path

sys.modules['yos.path'] = path
