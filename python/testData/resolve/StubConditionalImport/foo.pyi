import sys

if sys.version_info == (3, 12):
    import a
    bar = a.bar
else:
    import b
    bar = b.bar