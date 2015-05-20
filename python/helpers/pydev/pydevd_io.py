import pydevd_constants #@UnusedImport -- defines False and True if not there.

IS_PY3K = pydevd_constants.IS_PY3K

class IORedirector:
    '''This class works to redirect the write function to many streams
    '''

    def __init__(self, *args):
        self._redirectTo = args

    def write(self, s):
        for r in self._redirectTo:
            try:
                r.write(s)
            except:
                pass

    def isatty(self):
        return False

    def flush(self):
        for r in self._redirectTo:
            r.flush()

    def __getattr__(self, name):
        for r in self._redirectTo:
            if hasattr(r, name):
                return r.__getattribute__(name)
        raise AttributeError(name)

class IOBuf:
    '''This class works as a replacement for stdio and stderr.
    It is a buffer and when its contents are requested, it will erase what
    
    it has so far so that the next return will not return the same contents again.
    '''
    def __init__(self):
        self.buflist = []
        import os
        self.encoding = os.environ.get('PYTHONIOENCODING', 'utf-8')

    def getvalue(self):
        b = self.buflist
        self.buflist = [] #clear it
        return ''.join(b)
    
    def write(self, s):
        if not IS_PY3K:
            if isinstance(s, unicode):
                s = s.encode(self.encoding)
        self.buflist.append(s)

    def isatty(self):
        return False

    def flush(self):
        pass

    def empty(self):
        return len(self.buflist) == 0

class _RedirectionsHolder:
    _stack_stdout = []
    _stack_stderr = []


def StartRedirect(keep_original_redirection=False, std='stdout'):
    '''
    @param std: 'stdout', 'stderr', or 'both'
    '''
    import sys
    buf = IOBuf()

    if std == 'both':
        config_stds = ['stdout', 'stderr']
    else:
        config_stds = [std]

    for std in config_stds:
        original = getattr(sys, std)
        stack = getattr(_RedirectionsHolder, '_stack_%s' % std)
        stack.append(original)

        if keep_original_redirection:
            setattr(sys, std, IORedirector(buf, getattr(sys, std)))
        else:
            setattr(sys, std, buf)
    return buf


def EndRedirect(std='stdout'):
    import sys
    if std == 'both':
        config_stds = ['stdout', 'stderr']
    else:
        config_stds = [std]
    for std in config_stds:
        stack = getattr(_RedirectionsHolder, '_stack_%s' % std)
        setattr(sys, std, stack.pop())

