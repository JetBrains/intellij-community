"""Contains methods for building XML structures for interacting with IDE

The methods from this file are used for the debugger interaction. Please note
that Python console now uses Thrift structures with the similar methods
contained in `pydevd_thrift.py` file.
"""
import sys
import traceback

from _pydev_bundle import pydev_log
from _pydev_bundle.pydev_imports import quote
from _pydevd_bundle import pydevd_extension_utils
from _pydevd_bundle import pydevd_resolver
from _pydevd_bundle.pydevd_constants import dict_keys, IS_PY3K, \
    LOAD_VALUES_POLICY, DEFAULT_VALUES_DICT
from _pydevd_bundle.pydevd_extension_api import TypeResolveProvider, \
    StrPresentationProvider
from _pydevd_bundle.pydevd_frame_type_handler import get_vars_handler, \
    DO_NOT_PROCESS_VARS, XML_COMMUNICATION_VARS_HANDLER
from _pydevd_bundle.pydevd_repr_utils import get_value_repr
from _pydevd_bundle.pydevd_user_type_renderers_utils import \
    try_get_type_renderer_for_var
from _pydevd_bundle.pydevd_utils import is_string, should_evaluate_full_value, \
    should_evaluate_shape

try:
    import types

    frame_type = types.FrameType
except:
    frame_type = None


def make_valid_xml_value(s):
    # Same thing as xml.sax.saxutils.escape but also escaping double quotes.
    return s.replace("&", "&amp;").replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')


class ExceptionOnEvaluate:
    def __init__(self, result):
        self.result = result


_IS_JYTHON = sys.platform.startswith("java")


def _create_default_type_map():
    if not _IS_JYTHON:
        default_type_map = [
            # None means that it should not be treated as a compound variable

            # isintance does not accept a tuple on some versions of python, so, we must declare it expanded
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
            default_type_map.append((long, None))  # @UndefinedVariable
        except:
            pass  # not available on all python versions

        try:
            default_type_map.append((unicode, None))  # @UndefinedVariable
        except:
            pass  # not available on all python versions

        try:
            default_type_map.append((set, pydevd_resolver.setResolver))
        except:
            pass  # not available on all python versions

        try:
            default_type_map.append((frozenset, pydevd_resolver.setResolver))
        except:
            pass  # not available on all python versions

        try:
            from django.utils.datastructures import MultiValueDict
            default_type_map.insert(0, (MultiValueDict, pydevd_resolver.multiValueDictResolver))
            # we should put it before dict
        except:
            pass  # django may not be installed

        try:
            from django.forms import BaseForm
            default_type_map.insert(0, (BaseForm, pydevd_resolver.djangoFormResolver))
            # we should put it before instance resolver
        except:
            pass  # django may not be installed

        try:
            from collections import deque
            default_type_map.append((deque, pydevd_resolver.dequeResolver))
        except:
            pass

        try:
            from collections import OrderedDict
            default_type_map.insert(0, (OrderedDict, pydevd_resolver.orderedDictResolver))
            # we should put it before dict
        except:
            pass

        if frame_type is not None:
            default_type_map.append((frame_type, pydevd_resolver.frameResolver))

    else:
        from org.python import core  # @UnresolvedImport
        default_type_map = [
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
            # Jython 2.5b3 removed it.
            default_type_map.append((core.PyJavaInstance, pydevd_resolver.instanceResolver))

    return default_type_map


class TypeResolveHandler(object):
    NO_PROVIDER = []  # Sentinel value (any mutable object to be used as a constant would be valid).

    def __init__(self):
        # Note: don't initialize with the types we already know about so that the extensions can override
        # the default resolvers that are already available if they want.
        self._type_to_resolver_cache = {}
        self._type_to_str_provider_cache = {}
        self._initialized = False

    def _initialize(self):
        self._default_type_map = _create_default_type_map()
        self._resolve_providers = pydevd_extension_utils.extensions_of_type(TypeResolveProvider)
        self._str_providers = pydevd_extension_utils.extensions_of_type(StrPresentationProvider)
        self._initialized = True

    def get_type(self, o):
        try:
            try:
                # Faster than type(o) as we don't need the function call.
                type_object = o.__class__
            except:
                # Not all objects have __class__ (i.e.: there are bad bindings around).
                type_object = type(o)

            type_name = type_object.__name__
        except:
            # This happens for org.python.core.InitModule
            return 'Unable to get Type', 'Unable to get Type', None

        return self._get_type(o, type_object, type_name)

    def _get_type(self, o, type_object, type_name):
        resolver = self._type_to_resolver_cache.get(type_object)
        if resolver is not None:
            return type_object, type_name, resolver

        if not self._initialized:
            self._initialize()

        try:
            for resolver in self._resolve_providers:
                if resolver.can_provide(type_object, type_name):
                    # Cache it
                    self._type_to_resolver_cache[type_object] = resolver
                    return type_object, type_name, resolver

            for t in self._default_type_map:
                if isinstance(o, t[0]):
                    # Cache it
                    resolver = t[1]
                    self._type_to_resolver_cache[type_object] = resolver
                    return (type_object, type_name, resolver)
        except:
            traceback.print_exc()

        # No match return default (and cache it).
        resolver = pydevd_resolver.defaultResolver
        self._type_to_resolver_cache[type_object] = resolver
        return type_object, type_name, resolver

    if _IS_JYTHON:
        _base_get_type = _get_type

        def _get_type(self, o, type_object, type_name):
            if type_name == 'org.python.core.PyJavaInstance':
                return type_object, type_name, pydevd_resolver.instanceResolver

            if type_name == 'org.python.core.PyArray':
                return type_object, type_name, pydevd_resolver.jyArrayResolver

            return self._base_get_type(o, type_name, type_name)

    def str_from_providers(self, o, type_object, type_name, do_trim=True):
        provider = self._type_to_str_provider_cache.get(type_object)

        if provider is self.NO_PROVIDER:
            return None

        if provider is not None:
            try:
                return provider.get_str(o, do_trim)
            except TypeError:
                return provider.get_str(o)

        if not self._initialized:
            self._initialize()

        for provider in self._str_providers:
            if provider.can_provide(type_object, type_name):
                self._type_to_str_provider_cache[type_object] = provider
                try:
                    return provider.get_str(o, do_trim)
                except TypeError:
                    return provider.get_str(o)

        self._type_to_str_provider_cache[type_object] = self.NO_PROVIDER
        return None


_TYPE_RESOLVE_HANDLER = TypeResolveHandler()

""" 
def get_type(o):
    Receives object and returns a triple (typeObject, typeString, resolver).

    resolver != None means that variable is a container, and should be displayed as a hierarchy.

    Use the resolver to get its attributes.

    All container objects should have a resolver.
"""
get_type = _TYPE_RESOLVE_HANDLER.get_type

_str_from_providers = _TYPE_RESOLVE_HANDLER.str_from_providers


def get_sorted_keys(frame_f_locals):
    keys = dict_keys(frame_f_locals)
    if hasattr(keys, 'sort'):
        keys.sort()  # Python 3.0 does not have it
    else:
        keys = sorted(keys)  # Jython 2.1 does not have it
    return keys


def frame_vars_to_xml(frame_f_locals, group_type, hidden_ns=None, user_type_renderers=None):
    """ dumps frame variables to XML
    <var name="var_name" scope="local" type="type" value="value"/>
    """
    keys = get_sorted_keys(frame_f_locals)

    type_handler = get_vars_handler(var_to_xml,
                                    handler_type=XML_COMMUNICATION_VARS_HANDLER,
                                    group_type=group_type)

    for k in keys:
        try:
            v = frame_f_locals[k]
            eval_full_val = should_evaluate_full_value(v, group_type)

            type_handler.handle(k, v, hidden_ns, eval_full_val, user_type_renderers=user_type_renderers)
        except Exception:
            traceback.print_exc()
            pydev_log.error("Unexpected error, recovered safely.\n")

    return type_handler.get_xml()


def _get_default_var_string_representation(v, _type, typeName, format, do_trim=True):
    str_from_provider = _str_from_providers(v, _type, typeName, do_trim)
    if str_from_provider is not None:
        return str_from_provider

    return get_value_repr(v, do_trim, format)


def var_to_xml(val, name, do_trim=True, additional_in_xml='', evaluate_full_value=True, format='%s', user_type_renderers=None):
    """ single variable or dictionary to xml representation """

    if name in DO_NOT_PROCESS_VARS:
        xml = '<var name="%s" ' % (make_valid_xml_value(name))
        return ''.join((xml, ' />\n'))

    try:
        # This should be faster than isinstance (but we have to protect against not having a '__class__' attribute).
        is_exception_on_eval = val.__class__ == ExceptionOnEvaluate
    except:
        is_exception_on_eval = False

    if is_exception_on_eval:
        v = val.result
    else:
        v = val

    _type, typeName, resolver = get_type(v)

    # type qualifier to xml
    type_qualifier = getattr(_type, "__module__", "")
    if type_qualifier:
        xml_qualifier = 'qualifier="%s"' % make_valid_xml_value(type_qualifier)
    else:
        xml_qualifier = ''

    # type renderer to xml
    type_renderer = None
    if user_type_renderers is not None:
        type_renderer = try_get_type_renderer_for_var(v, user_type_renderers)
    if type_renderer is not None:
        xml_type_renderer_id = 'typeRendererId="%s"' % make_valid_xml_value(type_renderer.to_type)
    else:
        xml_type_renderer_id = ''

    # name and type to xml
    try:
        name = _do_quote(name)  # TODO: Fix PY-5834 without using quote
    except:
        pass
    xml = '<var name="%s" type="%s" ' % (make_valid_xml_value(name), make_valid_xml_value(typeName))

    # value to xml
    value = None
    if not evaluate_full_value:
        value = DEFAULT_VALUES_DICT[LOAD_VALUES_POLICY]
    elif type_renderer is not None:
        value = type_renderer.evaluate_var_string_repr(v)
    if value is None:
        value = _get_default_var_string_representation(v, _type, typeName, format, do_trim)

    xml_value = ' value="%s"' % (make_valid_xml_value(_do_quote(value)))

    # shape to xml
    xml_shape = ''
    try:
        # if should_evaluate_shape() and is_safe_to_access(v, 'shape'):
        if should_evaluate_shape():
            if hasattr(v, 'shape') and not callable(v.shape):
                xml_shape = ' shape="%s"' % make_valid_xml_value(str(tuple(v.shape)))
            elif hasattr(v, '__len__') and not is_string(v):
                xml_shape = ' shape="%s"' % make_valid_xml_value("%s" % str(len(v)))
    except:
        pass

    # additional info to xml
    if is_exception_on_eval:
        xml_container = ' isErrorOnEval="True"'
    else:
        if resolver is not None:
            xml_container = ' isContainer="True"'
        else:
            xml_container = ''

    return ''.join((xml, xml_qualifier, xml_value, xml_container, xml_shape, xml_type_renderer_id, additional_in_xml, ' />\n'))


def _do_quote(elem):
    """ Quote the elem safely,
    e.g. encoding it first if necessary and decoding again after quoting.

    Note that strings in Python 2 are 'str' or 'unicode' types,
    'quote' function works only with 'str' and 'unicode' in ASCII types.
    In Python 3 all strings are unicode and have 'str' type,
    'quote' function works well with 'str' and 'bytes' types.

    :param elem: name or value of variable
    :type elem: str, unicode, bytes
    :return: elem in UTF-8
    :rtype: unicode, str
    """
    if not IS_PY3K:
        if elem.__class__.__name__ == 'unicode':
            # unicode to str
            result = elem.encode('utf-8')
            result = quote(result, '/>_= ')
            # str to unicode in UTF-8
            result = result.decode('utf-8')
        else:
            result = quote(elem, '/>_= ')
    else:
        result = quote(elem, '/>_= ')

    return result
