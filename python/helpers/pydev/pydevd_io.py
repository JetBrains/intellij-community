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
        
