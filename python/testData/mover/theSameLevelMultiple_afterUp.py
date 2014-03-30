try:
    a = 1
except ImportError as A:
    import <caret>tmp2; import tmp1
    print xrange
