'''
    This module provides utilities to get the absolute filenames so that we can be sure that:
        - The case of a file will match the actual file in the filesystem (otherwise breakpoints won't be hit).
        - Providing means for the user to make path conversions when doing a remote debugging session in
          one machine and debugging in another.
    
    To do that, the PATHS_FROM_ECLIPSE_TO_PYTHON constant must be filled with the appropriate paths.
    
    @note: 
        in this context, the server is where your python process is running 
        and the client is where eclipse is running.
    
    E.g.: 
        If the server (your python process) has the structure
            /user/projects/my_project/src/package/module1.py  
        
        and the client has: 
            c:\my_project\src\package\module1.py  
            
        the PATHS_FROM_ECLIPSE_TO_PYTHON would have to be:
            PATHS_FROM_ECLIPSE_TO_PYTHON = [(r'c:\my_project\src', r'/user/projects/my_project/src')]
    
    @note: DEBUG_CLIENT_SERVER_TRANSLATION can be set to True to debug the result of those translations
    
    @note: the case of the paths is important! Note that this can be tricky to get right when one machine
    uses a case-independent filesystem and the other uses a case-dependent filesystem (if the system being
    debugged is case-independent, 'normcase()' should be used on the paths defined in PATHS_FROM_ECLIPSE_TO_PYTHON). 
    
    @note: all the paths with breakpoints must be translated (otherwise they won't be found in the server)
    
    @note: to enable remote debugging in the target machine (pydev extensions in the eclipse installation)
        import pydevd;pydevd.settrace(host, stdoutToServer, stderrToServer, port, suspend)
        
        see parameter docs on pydevd.py
        
    @note: for doing a remote debugging session, all the pydevd_ files must be on the server accessible 
        through the PYTHONPATH (and the PATHS_FROM_ECLIPSE_TO_PYTHON only needs to be set on the target 
        machine for the paths that'll actually have breakpoints).
'''




from pydevd_constants import * #@UnusedWildImport
import os.path
import sys
import traceback

normcase = os.path.normcase
basename = os.path.basename
exists = os.path.exists
join = os.path.join

try:
    rPath = os.path.realpath #@UndefinedVariable
except:
    # jython does not support os.path.realpath
    # realpath is a no-op on systems without islink support
    rPath = os.path.abspath 
  
#defined as a list of tuples where the 1st element of the tuple is the path in the client machine
#and the 2nd element is the path in the server machine.
#see module docstring for more details.
PATHS_FROM_ECLIPSE_TO_PYTHON = []


#example:
#PATHS_FROM_ECLIPSE_TO_PYTHON = [
#(normcase(r'd:\temp\temp_workspace_2\test_python\src\yyy\yyy'),
# normcase(r'd:\temp\temp_workspace_2\test_python\src\hhh\xxx'))]

DEBUG_CLIENT_SERVER_TRANSLATION = False

#caches filled as requested during the debug session
NORM_FILENAME_CONTAINER = {}
NORM_FILENAME_AND_BASE_CONTAINER = {}
NORM_FILENAME_TO_SERVER_CONTAINER = {}
NORM_FILENAME_TO_CLIENT_CONTAINER = {}

def _NormFile(filename):
    try:
        return NORM_FILENAME_CONTAINER[filename]
    except KeyError:
        r = normcase(rPath(filename))
        #cache it for fast access later
        NORM_FILENAME_CONTAINER[filename] = r
        return r

    
#Now, let's do a quick test to see if we're working with a version of python that has no problems
#related to the names generated...
try:
    try:
        code = rPath.func_code
    except AttributeError:
        code = rPath.__code__
    if not exists(_NormFile(code.co_filename)):
        sys.stderr.write('-------------------------------------------------------------------------------\n')
        sys.stderr.write('pydev debugger: CRITICAL WARNING: This version of python seems to be incorrectly compiled (internal generated filenames are not absolute)\n')
        sys.stderr.write('pydev debugger: The debugger may still function, but it will work slower and may miss breakpoints.\n')
        sys.stderr.write('pydev debugger: Related bug: http://bugs.python.org/issue1666807\n')
        sys.stderr.write('-------------------------------------------------------------------------------\n')
        
        NORM_SEARCH_CACHE = {}
        
        initial_norm_file = _NormFile
        def _NormFile(filename): #Let's redefine _NormFile to work with paths that may be incorrect
            try:
                return NORM_SEARCH_CACHE[filename]
            except KeyError:
                ret = initial_norm_file(filename)
                if not exists(ret):
                    #We must actually go on and check if we can find it as if it was a relative path for some of the paths in the pythonpath
                    for path in sys.path:
                        ret = initial_norm_file(join(path, filename))
                        if exists(ret):
                            break
                    else:
                        sys.stderr.write('pydev debugger: Unable to find real location for: %s\n' % (filename,))
                        ret = filename
                        
                NORM_SEARCH_CACHE[filename] = ret
                return ret
except:
    #Don't fail if there's something not correct here -- but at least print it to the user so that we can correct that
    traceback.print_exc()


if PATHS_FROM_ECLIPSE_TO_PYTHON:
    #Work on the client and server slashes.
    eclipse_sep = None
    python_sep = None
    for eclipse_prefix, server_prefix in PATHS_FROM_ECLIPSE_TO_PYTHON:
        if eclipse_sep is not None and python_sep is not None:
            break
        
        if eclipse_sep is None:
            for c in eclipse_prefix:
                if c in ('/', '\\'):
                    eclipse_sep = c
                    break
                
        if python_sep is None:
            for c in server_prefix:
                if c in ('/', '\\'):
                    python_sep = c
                    break
        
    #If they're the same or one of them cannot be determined, just make it all None.
    if eclipse_sep == python_sep or eclipse_sep is None or python_sep is None:
        eclipse_sep = python_sep = None
            
                
    #only setup translation functions if absolutely needed! 
    def NormFileToServer(filename):
        #Eclipse will send the passed filename to be translated to the python process
        #So, this would be 'NormFileFromEclipseToPython' 
        try:
            return NORM_FILENAME_TO_SERVER_CONTAINER[filename]
        except KeyError:
            #used to translate a path from the client to the debug server
            translated = normcase(filename)
            for eclipse_prefix, server_prefix in PATHS_FROM_ECLIPSE_TO_PYTHON:
                if translated.startswith(eclipse_prefix):
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write('pydev debugger: replacing to server: %s\n' % (translated,))
                    translated = translated.replace(eclipse_prefix, server_prefix)
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write('pydev debugger: sent to server: %s\n' % (translated,))
                    break
            else:
                if DEBUG_CLIENT_SERVER_TRANSLATION:
                    sys.stderr.write('pydev debugger: to server: unable to find matching prefix for: %s in %s\n' % \
                        (translated, [x[0] for x in PATHS_FROM_ECLIPSE_TO_PYTHON]))
                    
            #Note that when going to the server, we do the replace first and only later do the norm file.
            if eclipse_sep is not None:
                translated = translated.replace(eclipse_sep, python_sep)
            translated = _NormFile(translated)
                
            NORM_FILENAME_TO_SERVER_CONTAINER[filename] = translated
            return translated
        
    
    def NormFileToClient(filename): 
        #The result of this method will be passed to eclipse
        #So, this would be 'NormFileFromPythonToEclipse'
        try:
            return NORM_FILENAME_TO_CLIENT_CONTAINER[filename]
        except KeyError:
            #used to translate a path from the debug server to the client
            translated = _NormFile(filename)
            for eclipse_prefix, pyhon_prefix in PATHS_FROM_ECLIPSE_TO_PYTHON:
                if translated.startswith(pyhon_prefix):
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write('pydev debugger: replacing to client: %s\n' % (translated,))
                    translated = translated.replace(pyhon_prefix, eclipse_prefix)
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write('pydev debugger: sent to client: %s\n' % (translated,))
                    break
            else:
                if DEBUG_CLIENT_SERVER_TRANSLATION:
                    sys.stderr.write('pydev debugger: to client: unable to find matching prefix for: %s in %s\n' % \
                        (translated, [x[1] for x in PATHS_FROM_ECLIPSE_TO_PYTHON]))
                        
            if eclipse_sep is not None:
                translated = translated.replace(python_sep, eclipse_sep)
            
            #The resulting path is not in the python process, so, we cannot do a _NormFile here,
            #only at the beginning of this method.
            NORM_FILENAME_TO_CLIENT_CONTAINER[filename] = translated
            return translated
        
else:
    #no translation step needed (just inline the calls)
    NormFileToClient = _NormFile
    NormFileToServer = _NormFile
    

def GetFilenameAndBase(frame):
    #This one is just internal (so, does not need any kind of client-server translation)
    f = frame.f_code.co_filename
    try:
        return NORM_FILENAME_AND_BASE_CONTAINER[f]
    except KeyError:
        filename = _NormFile(f)
        base = basename(filename)
        NORM_FILENAME_AND_BASE_CONTAINER[f] = filename, base
        return filename, base
