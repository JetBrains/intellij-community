import pydev_log
import traceback
import pydevd_resolver
from pydevd_constants import * #@UnusedWildImport
from types import * #@UnusedWildImport

try:
    from urllib import quote
except:
    from urllib.parse import quote #@UnresolvedImport

try:
    from xml.sax.saxutils import escape

    def makeValidXmlValue(s):
        return escape(s, {'"': '&quot;'})
except:
    #Simple replacement if it's not there.
    def makeValidXmlValue(s):
        return s.replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')

class ExceptionOnEvaluate:
    def __init__(self, result):
        self.result = result

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
            return type_object, type_name, pydevd_resolver.instanceResolver

        if type_name == 'org.python.core.PyArray':
            return type_object, type_name, pydevd_resolver.jyArrayResolver

        for t in typeMap:
            if isinstance(o, t[0]):
                return type_object, type_name, t[1]
    except:
        traceback.print_exc()

    #no match return default
    return type_object, type_name, pydevd_resolver.defaultResolver

def frameVarsToXML(frame_f_locals):
    """ dumps frame variables to XML
    <var name="var_name" scope="local" type="type" value="value"/>
    """
    xml = ""

    keys = frame_f_locals.keys()
    if hasattr(keys, 'sort'):
        keys.sort() #Python 3.0 does not have it
    else:
        keys = sorted(keys) #Jython 2.1 does not have it

    for k in keys:
        try:
            v = frame_f_locals[k]
            xml += varToXML(v, str(k))
        except Exception:
            traceback.print_exc()
            pydev_log.error("Unexpected error, recovered safely.\n")

    return xml
    
    
def varToXML(val, name, doTrim=True):
    """ single variable or dictionary to xml representation """

    is_exception_on_eval = isinstance(val, ExceptionOnEvaluate)

    if is_exception_on_eval:
        v = val.result
    else:
        v = val

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

    try:
        name = quote(name, '/>_= ') #TODO: Fix PY-5834 without using quote
    except:
        pass
    xml = '<var name="%s" type="%s"' % (makeValidXmlValue(name), makeValidXmlValue(typeName))

    if value:
        #cannot be too big... communication may not handle it.
        if len(value) > MAXIMUM_VARIABLE_REPRESENTATION_SIZE and doTrim:
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

        xmlValue = ' value="%s"' % (makeValidXmlValue(quote(value, '/>_= ')))
    else:
        xmlValue = ''

    if is_exception_on_eval:
        xmlCont = ' isErrorOnEval="True"'
    else:
        if resolver is not None:
            xmlCont = ' isContainer="True"'
        else:
            xmlCont = ''

    return ''.join((xml, xmlValue, xmlCont, ' />\n'))
