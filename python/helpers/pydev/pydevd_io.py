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
        return False #not really a file

    def flush(self):
        pass

    def __getattr__(self, name):
        for r in self._redirectTo:
            if hasattr(r, name):
                t = type(r.__getattribute__(name)).__name__
                if t == 'builtin_function_or_method' or t == 'method':
                    def foo(*args):
                        return r.__getattribute__(name)(*args)
                    return foo
        raise AttributeError(name)

    
class IOBuf:
    '''This class works as a replacement for stdio and stderr.
    It is a buffer and when its contents are requested, it will erase what
    
    it has so far so that the next return will not return the same contents again.
    '''
    def __init__(self):
        self.buflist = []
    
    def getvalue(self):
        b = self.buflist
        self.buflist = [] #clear it
        return ''.join(b)
    
    def write(self, s):
        self.buflist.append(s)

    def isatty(self):
        return False #not really a file

    def flush(self):
        pass

        
