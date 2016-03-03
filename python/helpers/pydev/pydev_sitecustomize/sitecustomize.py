'''
    This module will:
    - change the input() and raw_input() commands to change \r\n or \r into \n
    - execute the user site customize -- if available
    - change raw_input() and input() to also remove any trailing \r
    
    Up to PyDev 3.4 it also was setting the default encoding, but it was removed because of differences when
    running from a shell (i.e.: now we just set the PYTHONIOENCODING related to that -- which is properly 
    treated on Py 2.7 onwards).
'''
DEBUG = 0 #0 or 1 because of jython

import sys
encoding = None

IS_PYTHON_3K = 0

try:
    if sys.version_info[0] == 3:
        IS_PYTHON_3K = 1
        
except:
    #That's OK, not all versions of python have sys.version_info
    if DEBUG:
        import traceback;traceback.print_exc() #@Reimport
        
#-----------------------------------------------------------------------------------------------------------------------
#Line buffering
if IS_PYTHON_3K:
    #Python 3 has a bug (http://bugs.python.org/issue4705) in which -u doesn't properly make output/input unbuffered
    #so, we need to enable that ourselves here.
    try:
        sys.stdout._line_buffering = True
    except:
        pass
    try:
        sys.stderr._line_buffering = True
    except:
        pass
    try:
        sys.stdin._line_buffering = True
    except:
        pass
    
    
try:
    import org.python.core.PyDictionary #@UnresolvedImport @UnusedImport -- just to check if it could be valid
    def dict_contains(d, key):
        return d.has_key(key)
except:
    try:
        #Py3k does not have has_key anymore, and older versions don't have __contains__
        dict_contains = dict.__contains__
    except:
        try:
            dict_contains = dict.has_key
        except NameError:
            def dict_contains(d, key):
                return d.has_key(key)


#----------------------------------------------------------------------------------------------------------------------- 
#now that we've finished the needed pydev sitecustomize, let's run the default one (if available)

#Ok, some weirdness going on in Python 3k: when removing this module from the sys.module to import the 'real'
#sitecustomize, all the variables in this scope become None (as if it was garbage-collected), so, the the reference
#below is now being kept to create a cyclic reference so that it neven dies)
__pydev_sitecustomize_module__ = sys.modules.get('sitecustomize') #A ref to this module


#remove the pydev site customize (and the pythonpath for it)
paths_removed = []
try:
    for c in sys.path[:]:
        #Pydev controls the whole classpath in Jython already, so, we don't want a a duplicate for
        #what we've already added there (this is needed to support Jython 2.5b1 onwards -- otherwise, as
        #we added the sitecustomize to the pythonpath and to the classpath, we'd have to remove it from the
        #classpath too -- and I don't think there's a way to do that... or not?)
        if c.find('pydev_sitecustomize') != -1 or c == '__classpath__' or c == '__pyclasspath__' or \
            c == '__classpath__/' or c == '__pyclasspath__/' or  c == '__classpath__\\' or c == '__pyclasspath__\\':
            sys.path.remove(c)
            if c.find('pydev_sitecustomize') == -1:
                #We'll re-add any paths removed but the pydev_sitecustomize we added from pydev.
                paths_removed.append(c)
            
    if dict_contains(sys.modules, 'sitecustomize'):
        del sys.modules['sitecustomize'] #this module
except:
    #print the error... should never happen (so, always show, and not only on debug)!
    import traceback;traceback.print_exc() #@Reimport
else:
    #Now, execute the default sitecustomize
    try:
        import sitecustomize #@UnusedImport
        sitecustomize.__pydev_sitecustomize_module__ = __pydev_sitecustomize_module__
    except:
        pass
    
    if not dict_contains(sys.modules, 'sitecustomize'):
        #If there was no sitecustomize, re-add the pydev sitecustomize (pypy gives a KeyError if it's not there)
        sys.modules['sitecustomize'] = __pydev_sitecustomize_module__
    
    try:
        if paths_removed:
            if sys is None:
                import sys
            if sys is not None:
                #And after executing the default sitecustomize, restore the paths (if we didn't remove it before,
                #the import sitecustomize would recurse).
                sys.path.extend(paths_removed)
    except:
        #print the error... should never happen (so, always show, and not only on debug)!
        import traceback;traceback.print_exc() #@Reimport




if not IS_PYTHON_3K:
    try:
        #Redefine input and raw_input only after the original sitecustomize was executed
        #(because otherwise, the original raw_input and input would still not be defined)
        import __builtin__
        original_raw_input = __builtin__.raw_input
        original_input = __builtin__.input
        
        
        def raw_input(prompt=''):
            #the original raw_input would only remove a trailing \n, so, at
            #this point if we had a \r\n the \r would remain (which is valid for eclipse)
            #so, let's remove the remaining \r which python didn't expect.
            ret = original_raw_input(prompt)
                
            if ret.endswith('\r'):
                return ret[:-1]
                
            return ret
        raw_input.__doc__ = original_raw_input.__doc__
    
        def input(prompt=''):
            #input must also be rebinded for using the new raw_input defined
            return eval(raw_input(prompt))
        input.__doc__ = original_input.__doc__
        
        
        __builtin__.raw_input = raw_input
        __builtin__.input = input
    
    except:
        #Don't report errors at this stage
        if DEBUG:
            import traceback;traceback.print_exc() #@Reimport
    
else:
    try:
        import builtins #Python 3.0 does not have the __builtin__ module @UnresolvedImport
        original_input = builtins.input
        def input(prompt=''):
            #the original input would only remove a trailing \n, so, at
            #this point if we had a \r\n the \r would remain (which is valid for eclipse)
            #so, let's remove the remaining \r which python didn't expect.
            ret = original_input(prompt)
                
            if ret.endswith('\r'):
                return ret[:-1]
                
            return ret
        input.__doc__ = original_input.__doc__
        builtins.input = input
    except:
        #Don't report errors at this stage
        if DEBUG:
            import traceback;traceback.print_exc() #@Reimport
    


try:
    #The original getpass doesn't work from the eclipse console, so, let's put a replacement
    #here (note that it'll not go into echo mode in the console, so, what' the user writes
    #will actually be seen)
    #Note: same thing from the fix_getpass module -- but we don't want to import it in this
    #custom sitecustomize.
    def fix_get_pass():
        try:
            import getpass
        except ImportError:
            return #If we can't import it, we can't fix it
        import warnings
        fallback = getattr(getpass, 'fallback_getpass', None) # >= 2.6
        if not fallback:
            fallback = getpass.default_getpass # <= 2.5
        getpass.getpass = fallback
        if hasattr(getpass, 'GetPassWarning'):
            warnings.simplefilter("ignore", category=getpass.GetPassWarning)
    fix_get_pass()
    
except:
    #Don't report errors at this stage
    if DEBUG:
        import traceback;traceback.print_exc() #@Reimport
