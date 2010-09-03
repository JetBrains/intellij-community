""" pydevd_vars deals with variables:
    resolution/conversion to XML.
"""
from pydevd_constants import * #@UnusedWildImport
from types import * #@UnusedWildImport
try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO
import sys #@Reimport
try:
    from urllib import quote
except:
    from urllib.parse import quote #@UnresolvedImport
import threading
import pydevd_resolver
import traceback

#-------------------------------------------------------------------------- defining true and false for earlier versions

try:
    __setFalse = False
except:
    import __builtin__
    setattr(__builtin__, 'True', 1)
    setattr(__builtin__, 'False', 0)

#------------------------------------------------------------------------------------------------------ class for errors

class VariableError(RuntimeError):pass
class FrameNotFoundError(RuntimeError):pass


#------------------------------------------------------------------------------------------------------ resolvers in map

if not sys.platform.startswith("java"):
    typeMap = [
        #None means that it should not be treated as a compound variable

        #isintance does not accept a tuple on some versions of python, so, we must declare it expanded
        (type(None), None,),
        (int, None),
        (float, None),
        (complex, None),
        (str, None),
        (tuple, pydevd_resolver.tupleResolver),
        (list, pydevd_resolver.tupleResolver),
        (dict, pydevd_resolver.dictResolver),
    ]

    try:
        typeMap.append((long, None))
    except:
        pass #not available on all python versions

    try:
        typeMap.append((unicode, None))
    except:
        pass #not available on all python versions

    try:
        typeMap.append((set, pydevd_resolver.setResolver))
    except:
        pass #not available on all python versions

    try:
        typeMap.append((frozenset, pydevd_resolver.setResolver))
    except:
        pass #not available on all python versions

else: #platform is java   
    from org.python import core #@UnresolvedImport
    typeMap = [
        (core.PyNone, None),
        (core.PyInteger, None),
        (core.PyLong, None),
        (core.PyFloat, None),
        (core.PyComplex, None),
        (core.PyString, None),
        (core.PyTuple, pydevd_resolver.tupleResolver),
        (core.PyList, pydevd_resolver.tupleResolver),
        (core.PyDictionary, pydevd_resolver.dictResolver),
        (core.PyStringMap, pydevd_resolver.dictResolver),
    ]

    if hasattr(core, 'PyJavaInstance'):
        #Jython 2.5b3 removed it.
        typeMap.append((core.PyJavaInstance, pydevd_resolver.instanceResolver))


def getType(o):
    """ returns a triple (typeObject, typeString, resolver
        resolver != None means that variable is a container, 
        and should be displayed as a hierarchy.
        Use the resolver to get its attributes.
        
        All container objects should have a resolver.
    """

    try:
        type_object = type(o)
        type_name = type_object.__name__
    except:
        #This happens for org.python.core.InitModule
        return 'Unable to get Type', 'Unable to get Type', None

    try:

        if type_name == 'org.python.core.PyJavaInstance':
            return (type_object, type_name, pydevd_resolver.instanceResolver)

        if type_name == 'org.python.core.PyArray':
            return (type_object, type_name, pydevd_resolver.jyArrayResolver)

        for t in typeMap:
            if isinstance(o, t[0]):
                return (type_object, type_name, t[1])
    except:
        traceback.print_exc()

    #no match return default        
    return (type_object, type_name, pydevd_resolver.defaultResolver)


try:
    from xml.sax.saxutils import escape
    def makeValidXmlValue(s):
        return escape(s, {'"':'&quot;'})
except:
    #Simple replacement if it's not there.
    def makeValidXmlValue(s):
        return s.replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')


def varToXML(v, name):
    """ single variable or dictionary to xml representation """
    type, typeName, resolver = getType(v)

    try:
        if hasattr(v, '__class__'):
            try:
                cName = str(v.__class__)
                if cName.find('.') != -1:
                    cName = cName.split('.')[-1]

                elif cName.find("'") != -1: #does not have '.' (could be something like <type 'int'>)
                    cName = cName[cName.index("'") + 1:]

                if cName.endswith("'>"):
                    cName = cName[:-2]
            except:
                cName = str(v.__class__)
            value = '%s: %s' % (cName, v)
        else:
            value = str(v)
    except:
        try:
            value = repr(v)
        except:
            value = 'Unable to get repr for %s' % v.__class__

    xml = '<var name="%s" type="%s"' % (makeValidXmlValue(name),makeValidXmlValue(typeName))

    if value:
        #cannot be too big... communication may not handle it.
        if len(value) > MAXIMUM_VARIABLE_REPRESENTATION_SIZE:
            value = value[0:MAXIMUM_VARIABLE_REPRESENTATION_SIZE]
            value += '...'

        #fix to work with unicode values
        try:
            if not IS_PY3K:
                if isinstance(value, unicode):
                    value = value.encode('utf-8')
            else:
                if isinstance(value, bytes):
                    value = value.encode('utf-8')
        except TypeError: #in java, unicode is a function
            pass

        xmlValue = ' value="%s"' % (makeValidXmlValue(quote(value, '/>_= \t')))
    else:
        xmlValue = ''

    if resolver is not None:
        xmlCont = ' isContainer="True"'
    else:
        xmlCont = ''

    return ''.join((xml, xmlValue, xmlCont, ' />\n'))


if USE_PSYCO_OPTIMIZATION:
    try:
        import psyco
        varToXML = psyco.proxy(varToXML)
    except ImportError:
        if hasattr(sys, 'exc_clear'): #jython does not have it
            sys.exc_clear() #don't keep the traceback -- clients don't want to see it


def frameVarsToXML(frame):
    """ dumps frame variables to XML
    <var name="var_name" scope="local" type="type" value="value"/>
    """
    xml = ""

    keys = frame.f_locals.keys()
    if hasattr(keys, 'sort'):
        keys.sort() #Python 3.0 does not have it
    else:
        keys = sorted(keys) #Jython 2.1 does not have it

    for k in keys:
        try:
            v = frame.f_locals[k]
            xml += varToXML(v, str(k))
        except Exception:
            traceback.print_exc()
            sys.stderr.write("Unexpected error, recovered safely.\n")
    return xml

def iterFrames(initialFrame):
    '''NO-YIELD VERSION: Iterates through all the frames starting at the specified frame (which will be the first returned item)'''
    #cannot use yield
    frames = []

    while initialFrame is not None:
        frames.append(initialFrame)
        initialFrame = initialFrame.f_back

    return frames

def dumpFrames(thread_id):
    sys.stdout.write('dumping frames\n')
    if thread_id != GetThreadId(threading.currentThread()) :
        raise VariableError("findFrame: must execute on same thread")

    curFrame = GetFrame()
    for frame in iterFrames(curFrame):
        sys.stdout.write('%s\n' % id(frame))


#===============================================================================
# AdditionalFramesContainer
#===============================================================================
class AdditionalFramesContainer:
    lock = threading.Lock()
    additional_frames = {} #dict of dicts
    

def addAdditionalFrameById(thread_id, frames_by_id):
    AdditionalFramesContainer.additional_frames[thread_id] = frames_by_id
        
        
def removeAdditionalFrameById(thread_id):
    del AdditionalFramesContainer.additional_frames[thread_id]
        
    
        

def findFrame(thread_id, frame_id):
    """ returns a frame on the thread that has a given frame_id """
    if thread_id != GetThreadId(threading.currentThread()) :
        raise VariableError("findFrame: must execute on same thread")
    
    lookingFor = int(frame_id)
    
    if AdditionalFramesContainer.additional_frames:
        if DictContains(AdditionalFramesContainer.additional_frames, thread_id):
            frame = AdditionalFramesContainer.additional_frames[thread_id].get(lookingFor)
            if frame is not None:
                return frame

    curFrame = GetFrame()
    if frame_id == "*":
        return curFrame # any frame is specified with "*"

    frameFound = None

    for frame in iterFrames(curFrame):
        if lookingFor == id(frame):
            frameFound = frame
            del frame
            break

        del frame

    #Important: python can hold a reference to the frame from the current context 
    #if an exception is raised, so, if we don't explicitly add those deletes
    #we might have those variables living much more than we'd want to.

    #I.e.: sys.exc_info holding reference to frame that raises exception (so, other places
    #need to call sys.exc_clear()) 
    del curFrame

    if frameFound is None:
        msgFrames = ''
        i = 0

        for frame in iterFrames(GetFrame()):
            i += 1
            msgFrames += str(id(frame))
            if i % 5 == 0:
                msgFrames += '\n'
            else:
                msgFrames += '  -  '

        errMsg = '''findFrame: frame not found.
Looking for thread_id:%s, frame_id:%s
Current     thread_id:%s, available frames:
%s
''' % (thread_id, lookingFor, GetThreadId(threading.currentThread()), msgFrames)

        raise FrameNotFoundError(errMsg)

    return frameFound

def resolveCompoundVariable(thread_id, frame_id, scope, attrs):
    """ returns the value of the compound variable as a dictionary"""
    frame = findFrame(thread_id, frame_id)
    attrList = attrs.split('\t')
    if scope == "GLOBAL":
        var = frame.f_globals
        del attrList[0] # globals are special, and they get a single dummy unused attribute
    else:
        var = frame.f_locals

    for k in attrList:
        type, _typeName, resolver = getType(var)
        var = resolver.resolve(var, k)

    try:
        type, _typeName, resolver = getType(var)
        return resolver.getDictionary(var)
    except:
        traceback.print_exc()

def evaluateExpression(thread_id, frame_id, expression, doExec):
    '''returns the result of the evaluated expression
    @param doExec: determines if we should do an exec or an eval
    '''
    frame = findFrame(thread_id, frame_id)

    expression = expression.replace('@LINE@', '\n')


    #Not using frame.f_globals because of https://sourceforge.net/tracker2/?func=detail&aid=2541355&group_id=85796&atid=577329
    #(Names not resolved in generator expression in method)
    #See message: http://mail.python.org/pipermail/python-list/2009-January/526522.html
    updated_globals = dict()
    updated_globals.update(frame.f_globals)
    updated_globals.update(frame.f_locals) #locals later because it has precedence over the actual globals

    try:

        if doExec:
            try:
                #try to make it an eval (if it is an eval we can print it, otherwise we'll exec it and 
                #it will have whatever the user actually did)
                compiled = compile(expression, '<string>', 'eval')
            except:
                exec(expression, updated_globals, frame.f_locals)
            else:
                result = eval(compiled, updated_globals, frame.f_locals)
                if result is not None: #Only print if it's not None (as python does)
                    sys.stdout.write('%s\n' % (result,))
            return

        else:
            result = None
            try:
                result = eval(expression, updated_globals, frame.f_locals)
            except Exception:
                s = StringIO()
                traceback.print_exc(file=s)
                result = s.getvalue()

                try:
                    try:
                        etype, value, tb = sys.exc_info()
                        result = value
                    finally:
                        etype = value = tb = None
                except:
                    pass
                
            return result
    finally:
        #Should not be kept alive if an exception happens and this frame is kept in the stack.
        del updated_globals
        del frame


def changeAttrExpression(thread_id, frame_id, attr, expression):
    '''Changes some attribute in a given frame.
    @note: it will not (currently) work if we're not in the topmost frame (that's a python
    deficiency -- and it appears that there is no way of making it currently work --
    will probably need some change to the python internals)
    '''
    frame = findFrame(thread_id, frame_id)

    try:
        expression = expression.replace('@LINE@', '\n')
#tests (needs proposed patch in python accepted)
#        if hasattr(frame, 'savelocals'):
#            if attr in frame.f_locals:
#                frame.f_locals[attr] = eval(expression, frame.f_globals, frame.f_locals)
#                frame.savelocals()
#                return
#                
#            elif attr in frame.f_globals:
#                frame.f_globals[attr] = eval(expression, frame.f_globals, frame.f_locals)
#                return


        if attr[:7] == "Globals":
            attr = attr[8:]
            if attr in frame.f_globals:
                frame.f_globals[attr] = eval(expression, frame.f_globals, frame.f_locals)
        else:
            #default way (only works for changing it in the topmost frame)
            exec('%s=%s' % (attr, expression), frame.f_globals, frame.f_locals)


    except Exception:
        traceback.print_exc()





