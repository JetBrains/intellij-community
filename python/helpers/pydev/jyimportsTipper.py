import StringIO
import traceback
from java.lang import StringBuffer #@UnresolvedImport
from java.lang import String #@UnresolvedImport
import java.lang #@UnresolvedImport
import sys
from _tipper_common import DoFind


try:
    False
    True
except NameError: # version < 2.3 -- didn't have the True/False builtins
    import __builtin__
    setattr(__builtin__, 'True', 1)
    setattr(__builtin__, 'False', 0)
    
    
from org.python.core import PyReflectedFunction #@UnresolvedImport

from org.python import core #@UnresolvedImport
from org.python.core import PyClass #@UnresolvedImport


#completion types.
TYPE_IMPORT = '0'
TYPE_CLASS = '1'
TYPE_FUNCTION = '2'
TYPE_ATTR = '3'
TYPE_BUILTIN = '4'
TYPE_PARAM = '5'

def _imp(name):
    try:
        return __import__(name)
    except:
        if '.' in name:
            sub = name[0:name.rfind('.')]
            return _imp(sub)
        else:
            s = 'Unable to import module: %s - sys.path: %s' % (str(name), sys.path)
            raise RuntimeError(s)

def Find(name):
    f = None
    if name.startswith('__builtin__'):
        if name == '__builtin__.str':
            name = 'org.python.core.PyString'
        elif name == '__builtin__.dict':
            name = 'org.python.core.PyDictionary'
            
    mod = _imp(name)
    parent = mod
    foundAs = ''
    
    if hasattr(mod, '__file__'):
        f = mod.__file__


    components = name.split('.')
    old_comp = None
    for comp in components[1:]:
        try:
            #this happens in the following case:
            #we have mx.DateTime.mxDateTime.mxDateTime.pyd
            #but after importing it, mx.DateTime.mxDateTime does shadows access to mxDateTime.pyd
            mod = getattr(mod, comp)
        except AttributeError:
            if old_comp != comp:
                raise
        
        if hasattr(mod, '__file__'):
            f = mod.__file__
        else:
            if len(foundAs) > 0:
                foundAs = foundAs + '.'
            foundAs = foundAs + comp
            
        old_comp = comp
        
    return f, mod, parent, foundAs

def formatParamClassName(paramClassName):
    if paramClassName.startswith('['):
        if paramClassName == '[C':
            paramClassName = 'char[]'
        
        elif paramClassName == '[B':
            paramClassName = 'byte[]'
        
        elif paramClassName == '[I':
            paramClassName = 'int[]'
            
        elif paramClassName.startswith('[L') and paramClassName.endswith(';'):
            paramClassName = paramClassName[2:-1]
            paramClassName += '[]'
    return paramClassName


def GenerateTip(data, log=None):
    data = data.replace('\n', '')
    if data.endswith('.'):
        data = data.rstrip('.')
    
    f, mod, parent, foundAs = Find(data)
    tips = GenerateImportsTipForModule(mod)
    return f, tips
    

#=======================================================================================================================
# Info
#=======================================================================================================================
class Info:
    
    def __init__(self, name, **kwargs):
        self.name = name
        self.doc = kwargs.get('doc', None)
        self.args = kwargs.get('args', ()) #tuple of strings
        self.varargs = kwargs.get('varargs', None) #string
        self.kwargs = kwargs.get('kwargs', None) #string
        self.ret = kwargs.get('ret', None) #string
        
    def basicAsStr(self):
        '''@returns this class information as a string (just basic format)
        '''
        
        s = 'function:%s args=%s, varargs=%s, kwargs=%s, docs:%s' % \
            (str(self.name), str(self.args), str(self.varargs), str(self.kwargs), str(self.doc))
        return s
        

    def getAsDoc(self):
        s = str(self.name)
        if self.doc:
            s += '\n@doc %s\n' % str(self.doc)
            
        if self.args:
            s += '\n@params '
            for arg in self.args:
                s += str(formatParamClassName(arg))
                s += '  '
        
        if self.varargs:
            s += '\n@varargs '
            s += str(self.varargs)
            
        if self.kwargs:
            s += '\n@kwargs '
            s += str(self.kwargs)
            
        if self.ret:
            s += '\n@return '
            s += str(formatParamClassName(str(self.ret)))
            
        return str(s)
        
def isclass(cls):
    return isinstance(cls, core.PyClass)

def ismethod(func):
    '''this function should return the information gathered on a function
    
    @param func: this is the function we want to get info on
    @return a tuple where:
        0 = indicates whether the parameter passed is a method or not
        1 = a list of classes 'Info', with the info gathered from the function
            this is a list because when we have methods from java with the same name and different signatures,
            we actually have many methods, each with its own set of arguments
    '''
    
    try:
        if isinstance(func, core.PyFunction):
            #ok, this is from python, created by jython
            #print_ '    PyFunction'
            
            def getargs(func_code):
                """Get information about the arguments accepted by a code object.
            
                Three things are returned: (args, varargs, varkw), where 'args' is
                a list of argument names (possibly containing nested lists), and
                'varargs' and 'varkw' are the names of the * and ** arguments or None."""
            
                nargs = func_code.co_argcount
                names = func_code.co_varnames
                args = list(names[:nargs])
                step = 0
            
                varargs = None
                if func_code.co_flags & func_code.CO_VARARGS:
                    varargs = func_code.co_varnames[nargs]
                    nargs = nargs + 1
                varkw = None
                if func_code.co_flags & func_code.CO_VARKEYWORDS:
                    varkw = func_code.co_varnames[nargs]
                return args, varargs, varkw
            
            args = getargs(func.func_code)
            return 1, [Info(func.func_name, args=args[0], varargs=args[1], kwargs=args[2], doc=func.func_doc)]
            
        if isinstance(func, core.PyMethod):
            #this is something from java itself, and jython just wrapped it...
            
            #things to play in func:
            #['__call__', '__class__', '__cmp__', '__delattr__', '__dir__', '__doc__', '__findattr__', '__name__', '_doget', 'im_class',
            #'im_func', 'im_self', 'toString']
            #print_ '    PyMethod'
            #that's the PyReflectedFunction... keep going to get it
            func = func.im_func
    
        if isinstance(func, PyReflectedFunction):
            #this is something from java itself, and jython just wrapped it...
            
            #print_ '    PyReflectedFunction'
            
            infos = []
            for i in range(len(func.argslist)):
                #things to play in func.argslist[i]:
                    
                #'PyArgsCall', 'PyArgsKeywordsCall', 'REPLACE', 'StandardCall', 'args', 'compare', 'compareTo', 'data', 'declaringClass'
                #'flags', 'isStatic', 'matches', 'precedence']
                
                #print_ '        ', func.argslist[i].data.__class__
                #func.argslist[i].data.__class__ == java.lang.reflect.Method
                
                if func.argslist[i]:
                    met = func.argslist[i].data
                    name = met.getName()
                    try:
                        ret = met.getReturnType()
                    except AttributeError:
                        ret = ''
                    parameterTypes = met.getParameterTypes()
                    
                    args = []
                    for j in range(len(parameterTypes)):
                        paramTypesClass = parameterTypes[j]
                        try:
                            try:
                                paramClassName = paramTypesClass.getName()
                            except:
                                paramClassName = paramTypesClass.getName(paramTypesClass)
                        except AttributeError:
                            try:
                                paramClassName = repr(paramTypesClass) #should be something like <type 'object'>
                                paramClassName = paramClassName.split('\'')[1]
                            except:
                                paramClassName = repr(paramTypesClass) #just in case something else happens... it will at least be visible
                        #if the parameter equals [C, it means it it a char array, so, let's change it
    
                        a = formatParamClassName(paramClassName)
                        #a = a.replace('[]','Array')
                        #a = a.replace('Object', 'obj')
                        #a = a.replace('String', 's')
                        #a = a.replace('Integer', 'i')
                        #a = a.replace('Char', 'c')
                        #a = a.replace('Double', 'd')
                        args.append(a) #so we don't leave invalid code
    
                    
                    info = Info(name, args=args, ret=ret)
                    #print_ info.basicAsStr()
                    infos.append(info)
    
            return 1, infos
    except Exception, e:
        s = StringIO.StringIO()
        traceback.print_exc(file=s)
        return 1, [Info(str('ERROR'), doc=s.getvalue())]
        
    return 0, None

def ismodule(mod):
    #java modules... do we have other way to know that?
    if not hasattr(mod, 'getClass') and not hasattr(mod, '__class__') \
       and hasattr(mod, '__name__'):
            return 1
           
    return isinstance(mod, core.PyModule)


def dirObj(obj):
    ret = []
    found = java.util.HashMap()
    original = obj
    if hasattr(obj, '__class__'):
        if obj.__class__ == java.lang.Class:

            #get info about superclasses
            classes = []
            classes.append(obj)
            try:
                c = obj.getSuperclass()
            except TypeError:
                #may happen on jython when getting the java.lang.Class class
                c = obj.getSuperclass(obj)
                
            while c != None:
                classes.append(c)
                c = c.getSuperclass()
            
            #get info about interfaces
            interfs = []
            for obj in classes:
                try:
                    interfs.extend(obj.getInterfaces())
                except TypeError:
                    interfs.extend(obj.getInterfaces(obj))
            classes.extend(interfs)
                
            #now is the time when we actually get info on the declared methods and fields
            for obj in classes:
                try:
                    declaredMethods = obj.getDeclaredMethods()
                except TypeError:
                    declaredMethods = obj.getDeclaredMethods(obj)
                    
                try:
                    declaredFields = obj.getDeclaredFields()
                except TypeError:
                    declaredFields = obj.getDeclaredFields(obj)
                    
                for i in range(len(declaredMethods)):
                    name = declaredMethods[i].getName()
                    ret.append(name)
                    found.put(name, 1)
                    
                for i in range(len(declaredFields)):
                    name = declaredFields[i].getName()
                    ret.append(name)
                    found.put(name, 1)
                    
                    
        elif isclass(obj.__class__): 
            d = dir(obj.__class__)
            for name in d:
                ret.append(name)
                found.put(name, 1)
            

    #this simple dir does not always get all the info, that's why we have the part before
    #(e.g.: if we do a dir on String, some methods that are from other interfaces such as 
    #charAt don't appear)
    d = dir(original)
    for name in d:
        if found.get(name) != 1:
            ret.append(name)
            
    return ret


def formatArg(arg):
    '''formats an argument to be shown
    '''
    
    s = str(arg)
    dot = s.rfind('.')
    if dot >= 0:
        s = s[dot + 1:]
    
    s = s.replace(';', '')
    s = s.replace('[]', 'Array')
    if len(s) > 0:
        c = s[0].lower()
        s = c + s[1:]

    return s
    
    
    
def Search(data):
    '''@return file, line, col
    '''
    
    data = data.replace('\n', '')
    if data.endswith('.'):
        data = data.rstrip('.')
    f, mod, parent, foundAs = Find(data)
    try:
        return DoFind(f, mod), foundAs
    except:
        return DoFind(f, parent), foundAs
    
    
def GenerateImportsTipForModule(obj_to_complete, dirComps=None, getattr=getattr, filter=lambda name:True):
    '''
        @param obj_to_complete: the object from where we should get the completions
        @param dirComps: if passed, we should not 'dir' the object and should just iterate those passed as a parameter
        @param getattr: the way to get a given object from the obj_to_complete (used for the completer)
        @param filter: a callable that receives the name and decides if it should be appended or not to the results
        @return: list of tuples, so that each tuple represents a completion with:
            name, doc, args, type (from the TYPE_* constants)
    '''
    ret = []
    
    if dirComps is None:
        dirComps = dirObj(obj_to_complete)
    
    for d in dirComps:

        if d is None:
            continue
            
        if not filter(d):
            continue
            
        args = ''
        doc = ''
        retType = TYPE_BUILTIN

        try:
            obj = getattr(obj_to_complete, d)
        except (AttributeError, java.lang.NoClassDefFoundError):
            #jython has a bug in its custom classloader that prevents some things from working correctly, so, let's see if
            #we can fix that... (maybe fixing it in jython itself would be a better idea, as this is clearly a bug)
            #for that we need a custom classloader... we have references from it in the below places:
            #
            #http://mindprod.com/jgloss/classloader.html
            #http://www.javaworld.com/javaworld/jw-03-2000/jw-03-classload-p2.html
            #http://freshmeat.net/articles/view/1643/
            #
            #note: this only happens when we add things to the sys.path at runtime, if they are added to the classpath
            #before the run, everything goes fine.
            #
            #The code below ilustrates what I mean... 
            #
            #import sys
            #sys.path.insert(1, r"C:\bin\eclipse310\plugins\org.junit_3.8.1\junit.jar" )
            #
            #import junit.framework
            #print_ dir(junit.framework) #shows the TestCase class here
            #
            #import junit.framework.TestCase 
            #
            #raises the error:
            #Traceback (innermost last):
            #  File "<console>", line 1, in ?
            #ImportError: No module named TestCase
            #
            #whereas if we had added the jar to the classpath before, everything would be fine by now...

            ret.append((d, '', '', retType))
            #that's ok, private things cannot be gotten...
            continue
        else:

            isMet = ismethod(obj)
            if isMet[0]:
                info = isMet[1][0]
                try:
                    args, vargs, kwargs = info.args, info.varargs, info.kwargs
                    doc = info.getAsDoc()
                    r = ''
                    for a in (args):
                        if len(r) > 0:
                            r += ', '
                        r += formatArg(a)
                    args = '(%s)' % (r)
                except TypeError:
                    traceback.print_exc()
                    args = '()'
    
                retType = TYPE_FUNCTION
                
            elif isclass(obj):
                retType = TYPE_CLASS
                
            elif ismodule(obj):
                retType = TYPE_IMPORT
        
        #add token and doc to return - assure only strings.
        ret.append((d, doc, args, retType))
        
            
    return ret


if __name__ == "__main__":
    sys.path.append(r'D:\dev_programs\eclipse_3\310\eclipse\plugins\org.junit_3.8.1\junit.jar')
    sys.stdout.write('%s\n' % Find('junit.framework.TestCase'))
