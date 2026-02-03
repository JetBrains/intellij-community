__author__ = 'ktisha'

def g(f):
    if f.__doc__: # Should be OK
        lines = f.__doc__.splitlines() # Should be OK
        for line in lines:
            line = line.strip()
            if line:
                return line.rstrip('.')
    return f.__name__ # Should be OK