""" pydevd_vars deals with variables:
    resolution/conversion to XML.
"""
import pickle
from django_frame import DjangoTemplateFrame
from pydevd_constants import * #@UnusedWildImport
from types import * #@UnusedWildImport

from pydevd_xml import *

try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO
import sys #@Reimport

if USE_LIB_COPY:
    import _pydev_threading as threading
else:
    import threading
import pydevd_resolver
import traceback

try:
    from pydevd_exec import Exec
except:
    from pydevd_exec2 import Exec

#-------------------------------------------------------------------------- defining true and false for earlier versions

try:
    __setFalse = False
except:
    import __builtin__

    setattr(__builtin__, 'True', 1)
    setattr(__builtin__, 'False', 0)

#------------------------------------------------------------------------------------------------------ class for errors

class VariableError(RuntimeError): pass

class FrameNotFoundError(RuntimeError): pass


if USE_PSYCO_OPTIMIZATION:
    try:
        import psyco

        varToXML = psyco.proxy(varToXML)
    except ImportError:
        if hasattr(sys, 'exc_clear'): #jython does not have it
            sys.exc_clear() #don't keep the traceback -- clients don't want to see it

def iterFrames(initialFrame):
    """NO-YIELD VERSION: Iterates through all the frames starting at the specified frame (which will be the first returned item)"""
    #cannot use yield
    frames = []

    while initialFrame is not None:
        frames.append(initialFrame)
        initialFrame = initialFrame.f_back

    return frames

def dumpFrames(thread_id):
    sys.stdout.write('dumping frames\n')
    if thread_id != GetThreadId(threading.currentThread()):
        raise VariableError("findFrame: must execute on same thread")

    curFrame = GetFrame()
    for frame in iterFrames(curFrame):
        sys.stdout.write('%s\n' % pickle.dumps(frame))


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
    if thread_id != GetThreadId(threading.currentThread()):
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

        sys.stderr.write(errMsg)
        return None

    return frameFound

def resolveCompoundVariable(thread_id, frame_id, scope, attrs):
    """ returns the value of the compound variable as a dictionary"""
    frame = findFrame(thread_id, frame_id)
    if frame is None:
        return {}

    attrList = attrs.split('\t')
    
    if scope == "GLOBAL":
        var = frame.f_globals
        del attrList[0] # globals are special, and they get a single dummy unused attribute
    else:
        var = frame.f_locals
        type, _typeName, resolver = getType(var)
        try:
            resolver.resolve(var, attrList[0])
        except:
            var = frame.f_globals

    for k in attrList:
        type, _typeName, resolver = getType(var)
        var = resolver.resolve(var, k)

    try:
        type, _typeName, resolver = getType(var)
        return resolver.getDictionary(var)
    except:
        traceback.print_exc()
        
        
def resolveVar(var, attrs):
    attrList = attrs.split('\t')
    
    for k in attrList:
        type, _typeName, resolver = getType(var)
        
        var = resolver.resolve(var, k)
    
    try:
        type, _typeName, resolver = getType(var)
        return resolver.getDictionary(var)
    except:
        traceback.print_exc()
    

def evaluateExpression(thread_id, frame_id, expression, doExec):
    """returns the result of the evaluated expression
    @param doExec: determines if we should do an exec or an eval
    """
    frame = findFrame(thread_id, frame_id)
    if frame is None:
        return

    expression = str(expression.replace('@LINE@', '\n'))


    #Not using frame.f_globals because of https://sourceforge.net/tracker2/?func=detail&aid=2541355&group_id=85796&atid=577329
    #(Names not resolved in generator expression in method)
    #See message: http://mail.python.org/pipermail/python-list/2009-January/526522.html
    updated_globals = {}
    updated_globals.update(frame.f_globals)
    updated_globals.update(frame.f_locals) #locals later because it has precedence over the actual globals

    try:
        if doExec:
            try:
                #try to make it an eval (if it is an eval we can print it, otherwise we'll exec it and
                #it will have whatever the user actually did)
                compiled = compile(expression, '<string>', 'eval')
            except:
                Exec(expression, updated_globals, frame.f_locals)
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

                result = ExceptionOnEvaluate(result)

            return result
    finally:
        #Should not be kept alive if an exception happens and this frame is kept in the stack.
        del updated_globals
        del frame

def changeAttrExpression(thread_id, frame_id, attr, expression):
    """Changes some attribute in a given frame.
    @note: it will not (currently) work if we're not in the topmost frame (that's a python
    deficiency -- and it appears that there is no way of making it currently work --
    will probably need some change to the python internals)
    """
    frame = findFrame(thread_id, frame_id)
    if frame is None:
        return

    if isinstance(frame, DjangoTemplateFrame):
        result = eval(expression, frame.f_globals, frame.f_locals)
        frame.changeVariable(attr, result)

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
                return frame.f_globals[attr]
        else:
            #default way (only works for changing it in the topmost frame)
            result = eval(expression, frame.f_globals, frame.f_locals)
            Exec('%s=%s' % (attr, expression), frame.f_globals, frame.f_locals)
            return result


    except Exception:
        traceback.print_exc()





