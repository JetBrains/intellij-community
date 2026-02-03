def repl(s):
    if not isinstance(s, basestring):
        return s
    return s.replace(s.replace('a', 'b'), s)
