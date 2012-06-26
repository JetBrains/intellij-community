#@PydevCodeAnalysisIgnore
'''
@author Fabio Zadrozny 
'''
IS_PYTHON3K = 0
try:
    import __builtin__
except ImportError:
    import builtins as __builtin__ # Python 3.0
    IS_PYTHON3K = 1

try:
    True 
    False 
except NameError: 
    #If it's not defined, let's define it now.
    setattr(__builtin__, 'True', 1) #Python 3.0 does not accept __builtin__.True = 1 in its syntax
    setattr(__builtin__, 'False', 0)

import pydevd_constants

try:
    from java.lang import Thread
    IS_JYTHON = True
    SERVER_NAME = 'jycompletionserver'
    import jyimportsTipper #as importsTipper #changed to be backward compatible with 1.5
    importsTipper = jyimportsTipper

except ImportError:
    #it is python
    IS_JYTHON = False
    SERVER_NAME = 'pycompletionserver'
    if pydevd_constants.USE_LIB_COPY:
        from _pydev_threading import Thread
    else:
        from threading import Thread
    import importsTipper


if pydevd_constants.USE_LIB_COPY:
    import _pydev_socket as socket
else:
    import socket

import sys
if sys.platform == "darwin":
    #See: https://sourceforge.net/projects/pydev/forums/forum/293649/topic/3454227
    try:
        import _CF #Don't fail if it doesn't work.
    except:
        pass


#initial sys.path
_sys_path = []
for p in sys.path:
    #changed to be compatible with 1.5
    _sys_path.append(p)

#initial sys.modules
_sys_modules = {}
for name, mod in sys.modules.items():
    _sys_modules[name] = mod


import traceback

if pydevd_constants.USE_LIB_COPY:
    import _pydev_time as time
else:
    import time

try:
    import StringIO
except:
    import io as StringIO #Python 3.0

try:
    from urllib import quote_plus, unquote_plus
except ImportError:
    from urllib.parse import quote_plus, unquote_plus #Python 3.0

INFO1 = 1
INFO2 = 2
WARN = 4
ERROR = 8

DEBUG = INFO1 | ERROR

def dbg(s, prior):
    if prior & DEBUG != 0:
        sys.stdout.write('%s\n' % (s,))
#        f = open('c:/temp/test.txt', 'a')
#        print_ >> f, s
#        f.close()
   
import pydev_localhost
HOST = pydev_localhost.get_localhost() # Symbolic name meaning the local host

MSG_KILL_SERVER = '@@KILL_SERVER_END@@'
MSG_COMPLETIONS = '@@COMPLETIONS'
MSG_END = 'END@@'
MSG_INVALID_REQUEST = '@@INVALID_REQUEST'
MSG_JYTHON_INVALID_REQUEST = '@@JYTHON_INVALID_REQUEST'
MSG_CHANGE_DIR = '@@CHANGE_DIR:'
MSG_OK = '@@MSG_OK_END@@'
MSG_BIKE = '@@BIKE'
MSG_PROCESSING = '@@PROCESSING_END@@'
MSG_PROCESSING_PROGRESS = '@@PROCESSING:%sEND@@'
MSG_IMPORTS = '@@IMPORTS:'
MSG_PYTHONPATH = '@@PYTHONPATH_END@@'
MSG_CHANGE_PYTHONPATH = '@@CHANGE_PYTHONPATH:'
MSG_SEARCH = '@@SEARCH'

BUFFER_SIZE = 1024



currDirModule = None

def CompleteFromDir(dir):
    '''
    This is necessary so that we get the imports from the same dir where the file
    we are completing is located.
    '''
    global currDirModule
    if currDirModule is not None:
        del sys.path[currDirModule]

    sys.path.insert(0, dir)


def ChangePythonPath(pythonpath):
    '''Changes the pythonpath (clears all the previous pythonpath)
    
    @param pythonpath: string with paths separated by |
    '''
    
    split = pythonpath.split('|')
    sys.path = []
    for path in split:
        path = path.strip()
        if len(path) > 0:
            sys.path.append(path)
    
class KeepAliveThread(Thread):
    def __init__(self, socket):
        Thread.__init__(self)
        self.socket = socket
        self.processMsgFunc = None
        self.lastMsg = None
    
    def run(self):
        time.sleep(0.1)
        
        def send(s, msg):
            if IS_PYTHON3K:
                s.send(bytearray(msg, 'utf-8'))
            else:
                s.send(msg)
            
        while self.lastMsg == None:
            
            if self.processMsgFunc != None:
                s = MSG_PROCESSING_PROGRESS % quote_plus(self.processMsgFunc())
                sent = send(self.socket, s)
            else:
                sent = send(self.socket, MSG_PROCESSING)
            if sent == 0:
                sys.exit(0) #connection broken
            time.sleep(0.1)

        sent = send(self.socket, self.lastMsg)
        if sent == 0:
            sys.exit(0) #connection broken
        
class Processor:

    def __init__(self):
      # nothing to do
      return
        
    def removeInvalidChars(self, msg):
        try:
            msg = str(msg)
        except UnicodeDecodeError:
            pass
        
        if msg:
            try:
                return quote_plus(msg)
            except:
                sys.stdout.write('error making quote plus in %s\n' % (msg,))
                raise
        return ' '
    
    def formatCompletionMessage(self, defFile, completionsList):
        '''
        Format the completions suggestions in the following format:
        @@COMPLETIONS(modFile(token,description),(token,description),(token,description))END@@
        '''
        compMsg = []
        compMsg.append('%s' % defFile)
        for tup in completionsList:
            compMsg.append(',')
                
            compMsg.append('(')
            compMsg.append(str(self.removeInvalidChars(tup[0]))) #token
            compMsg.append(',')
            compMsg.append(self.removeInvalidChars(tup[1])) #description

            if(len(tup) > 2):
                compMsg.append(',')
                compMsg.append(self.removeInvalidChars(tup[2])) #args - only if function.
                
            if(len(tup) > 3):
                compMsg.append(',')
                compMsg.append(self.removeInvalidChars(tup[3])) #TYPE
                
            compMsg.append(')')
        
        return '%s(%s)%s' % (MSG_COMPLETIONS, ''.join(compMsg), MSG_END)
    

class T(Thread):

    def __init__(self, thisP, serverP):
        Thread.__init__(self)
        self.thisPort = thisP
        self.serverPort = serverP
        self.socket = None #socket to send messages.
        self.processor = Processor()


    def connectToServer(self):
        self.socket = s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            s.connect((HOST, self.serverPort))
        except:
            sys.stderr.write('Error on connectToServer with parameters: host: %s port: %s\n' % (HOST, self.serverPort))
            raise

    def getCompletionsMessage(self, defFile, completionsList):
        '''
        get message with completions.
        '''
        return self.processor.formatCompletionMessage(defFile, completionsList)
    
    def getTokenAndData(self, data):
        '''
        When we receive this, we have 'token):data'
        '''
        token = ''
        for c in data:
            if c != ')':
                token = token + c
            else:
                break;
        
        return token, data.lstrip(token + '):')

    
    def run(self):
        # Echo server program
        try:
            import _pydev_log
            log = _pydev_log.Log()
            
            dbg(SERVER_NAME + ' creating socket' , INFO1)
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.bind((HOST, self.thisPort))
            except:
                sys.stderr.write('Error connecting with parameters: host: %s port: %s\n' % (HOST, self.serverPort))
                raise
            s.listen(1) #socket to receive messages.
            
    
            #we stay here until we are connected.
            #we only accept 1 client. 
            #the exit message for the server is @@KILL_SERVER_END@@
            dbg(SERVER_NAME + ' waiting for connection' , INFO1)
            conn, addr = s.accept()
            time.sleep(0.5) #wait a little before connecting to JAVA server
    
            dbg(SERVER_NAME + ' waiting to java client' , INFO1)
            #after being connected, create a socket as a client.
            self.connectToServer()
            
            dbg(SERVER_NAME + ' Connected by ' + str(addr), INFO1)
            
            
            while 1:
                data = ''
                returnMsg = ''
                keepAliveThread = KeepAliveThread(self.socket)
                
                while data.find(MSG_END) == -1:
                    received = conn.recv(BUFFER_SIZE)
                    if len(received) == 0:
                        sys.exit(0) #ok, connection ended
                    if IS_PYTHON3K:
                        data = data + received.decode('utf-8')
                    else:
                        data = data + received
    
                try:
                    try:
                        if data.find(MSG_KILL_SERVER) != -1:
                            dbg(SERVER_NAME + ' kill message received', INFO1)
                            #break if we received kill message.
                            self.ended = True
                            sys.exit(0)
            
                        dbg(SERVER_NAME + ' starting keep alive thread', INFO2)
                        keepAliveThread.start()
                        
                        if data.find(MSG_PYTHONPATH) != -1:
                            comps = []
                            for p in _sys_path:
                                comps.append((p, ' '))
                            returnMsg = self.getCompletionsMessage(None, comps)
    
                        else:
                            data = data[:data.rfind(MSG_END)]
                        
                            if data.startswith(MSG_IMPORTS):
                                data = data.replace(MSG_IMPORTS, '')
                                data = unquote_plus(data)
                                defFile, comps = importsTipper.GenerateTip(data, log)
                                returnMsg = self.getCompletionsMessage(defFile, comps)
        
                            elif data.startswith(MSG_CHANGE_PYTHONPATH):
                                data = data.replace(MSG_CHANGE_PYTHONPATH, '')
                                data = unquote_plus(data)
                                ChangePythonPath(data)
                                returnMsg = MSG_OK
        
                            elif data.startswith(MSG_SEARCH):
                                data = data.replace(MSG_SEARCH, '')
                                data = unquote_plus(data)
                                (f, line, col), foundAs = importsTipper.Search(data)
                                returnMsg = self.getCompletionsMessage(f, [(line, col, foundAs)])
                                
                            elif data.startswith(MSG_CHANGE_DIR):
                                data = data.replace(MSG_CHANGE_DIR, '')
                                data = unquote_plus(data)
                                CompleteFromDir(data)
                                returnMsg = MSG_OK
                                
                            elif data.startswith(MSG_BIKE): 
                                returnMsg = MSG_INVALID_REQUEST #No longer supported.
                                
                            else:
                                returnMsg = MSG_INVALID_REQUEST
                    except SystemExit:
                        returnMsg = self.getCompletionsMessage(None, [('Exit:', 'SystemExit', '')])
                        keepAliveThread.lastMsg = returnMsg
                        raise
                    except:
                        dbg(SERVER_NAME + ' exception occurred', ERROR)
                        s = StringIO.StringIO()
                        traceback.print_exc(file=s)
    
                        err = s.getvalue()
                        dbg(SERVER_NAME + ' received error: ' + str(err), ERROR)
                        returnMsg = self.getCompletionsMessage(None, [('ERROR:', '%s\nLog:%s' % (err, log.GetContents()), '')])
                            
                    
                finally:
                    log.Clear()
                    keepAliveThread.lastMsg = returnMsg
                
            conn.close()
            self.ended = True
            sys.exit(0) #connection broken
            
            
        except SystemExit:
            raise
            #No need to log SystemExit error
        except:
            s = StringIO.StringIO()
            exc_info = sys.exc_info()

            traceback.print_exception(exc_info[0], exc_info[1], exc_info[2], limit=None, file=s)
            err = s.getvalue()
            dbg(SERVER_NAME + ' received error: ' + str(err), ERROR)
            raise

if __name__ == '__main__':

    thisPort = int(sys.argv[1])  #this is from where we want to receive messages.
    serverPort = int(sys.argv[2])#this is where we want to write messages.
    
    t = T(thisPort, serverPort)
    dbg(SERVER_NAME + ' will start', INFO1)
    t.start()
    time.sleep(5)
    t.join()
