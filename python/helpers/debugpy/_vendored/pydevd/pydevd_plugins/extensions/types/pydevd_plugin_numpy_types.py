import inspect

try:
    from collections import OrderedDict
except:
    OrderedDict = dict

from _pydevd_bundle.custom.pydevd_repr_utils import get_value_repr
from _pydevd_bundle.custom.pydevd_utils import get_var_and_offset
from _pydevd_bundle.pydevd_extension_api import TypeResolveProvider, \
    StrPresentationProvider
from _pydevd_bundle.pydevd_resolver import defaultResolver
from .pydevd_helpers import find_mod_attr, sorted_attributes_key


TOO_LARGE_MSG = "Maximum number of items (%s) reached. To show more items customize the value of the PYDEVD_CONTAINER_NUMPY_MAX_ITEMS environment variable."
TOO_LARGE_ATTR = "Unable to handle:"
IS_PYCHARM = True
MAX_ITEMS_TO_HANDLE = 300 if not IS_PYCHARM else 100
DEFAULT_PRECISION = 5
ARRAY_CONTAINER_ATTR_NAME = "array"

class NdArrayItemsContainer(object):
    pass


class NDArrayTypeResolveProvider(object):
    """
    This resolves a numpy ndarray returning some metadata about the NDArray
    """

    def can_provide(self, type_object, type_name):
        nd_array = find_mod_attr('numpy', 'ndarray')
        return nd_array is not None and inspect.isclass(type_object) and issubclass(type_object, nd_array)

    '''
       This resolves a numpy ndarray returning some metadata about the NDArray
   '''

    def is_numeric(self, obj):
        if not hasattr(obj, 'dtype'):
            return False
        return obj.dtype.kind in 'biufc'

    def round_if_possible(self, obj):
        try:
            return obj.round(DEFAULT_PRECISION)
        except TypeError:
            return obj

    def resolve(self, obj, attribute):
        if attribute == '__internals__':
            if not IS_PYCHARM:
                return defaultResolver.get_dictionary(obj)
        if attribute == 'min':
            if self.is_numeric(obj):
                return obj.min()
            else:
                return None
        if attribute == 'max':
            if self.is_numeric(obj):
                return obj.max()
            else:
                return None
        if attribute == 'shape':
            return obj.shape
        if attribute == 'dtype':
            return obj.dtype
        if attribute == 'size':
            return obj.size
        if attribute.startswith('['):
            container = NdArrayItemsContainer()
            i = 0
            format_str = '%0' + str(int(len(str(len(obj))))) + 'd'
            for item in obj:
                setattr(container, format_str % i, item)
                i += 1
                if i > MAX_ITEMS_TO_HANDLE:
                    setattr(container, TOO_LARGE_ATTR, TOO_LARGE_MSG)
                    break
            return container
        if IS_PYCHARM and attribute == ARRAY_CONTAINER_ATTR_NAME:
            container = NdArrayItemsContainer()
            container.items = obj
            return container
        return None

    def get_dictionary(self, obj):
        ret = dict()
        if not IS_PYCHARM:
            ret['__internals__'] = defaultResolver.get_dictionary(obj)
        if obj.size > 1024 * 1024:
            ret['min'] = 'ndarray too big, calculating min would slow down debugging'
            ret['max'] = 'ndarray too big, calculating max would slow down debugging'
        else:
            if self.is_numeric(obj):
                ret['min'] = obj.min()
                ret['max'] = obj.max()
            else:
                ret['min'] = 'not a numeric object'
                ret['max'] = 'not a numeric object'
        ret['shape'] = obj.shape
        ret['dtype'] = obj.dtype
        ret['size'] = obj.size
        if IS_PYCHARM:
            container = NdArrayItemsContainer()
            container.items = obj
            ret[ARRAY_CONTAINER_ATTR_NAME] = container
        else:
            ret['[0:%s] ' % (len(obj))] = list(obj[0:MAX_ITEMS_TO_HANDLE])
        return ret

    def get_contents_debug_adapter_protocol(self, value, fmt):
        dct = self.get_dictionary(value)
        lst = sorted(dct.items(), key=lambda tup: sorted_attributes_key(tup[0]))

        def evaluate_name(key):
            if key == ARRAY_CONTAINER_ATTR_NAME:
                # container is just a variable for display, it cannot be accessed
                return ""
            else:
                return key

        lst = [(key, value, evaluate_name(key)) for (key, value) in lst]
        return lst

class NDArrayStrProvider(StrPresentationProvider):
    def can_provide(self, type_object, type_name):
        nd_array = find_mod_attr('numpy', 'ndarray')
        return nd_array is not None and inspect.isclass(type_object) and issubclass(type_object, nd_array)

    def _to_str_no_trim(self, val):
        return str(val.tolist()).replace('\n', ',').strip()

    def get_str(self, val, do_trim=True):
        if do_trim:
            return get_value_repr(val)
        try:
            import numpy as np
            with np.printoptions(threshold=sys.maxsize):
                return self._to_str_no_trim(val)
        except:
            return self._to_str_no_trim(val)

class NdArrayItemsContainerProvider(object):
    def can_provide(self, type_object, type_name):
        return inspect.isclass(type_object) and issubclass(type_object, NdArrayItemsContainer)

    def resolve(self, obj, attribute):
        if attribute == '__len__':
            return None
        return obj.items[int(attribute)]

    def get_dictionary(self, obj):
        obj, offset = get_var_and_offset(obj)

        l = len(obj.items)
        d = OrderedDict()

        format_str = '%0' + str(int(len(str(l)))) + 'd'

        i = offset
        for item in obj.items[offset:offset + MAX_ITEMS_TO_HANDLE]:
            d[format_str % i] = item
            i += 1

            if i > MAX_ITEMS_TO_HANDLE + offset:
                break
        d['__len__'] = l
        return d

    def get_contents_debug_adapter_protocol(self, value, fmt):
        dct = self.get_dictionary(value)
        lst = sorted(dct.items(), key=lambda tup: sorted_attributes_key(tup[0]))

        def evaluate_name(key: str):
            if key.isdigit():
                # container indices have trailing zeros, we turn the representation
                # into an int first
                return f"[{int(key)}]"
            return f".{key}"

        lst = [(key, value, evaluate_name(key)) for (key, value) in lst]
        return lst
import sys

if not sys.platform.startswith("java"):
    TypeResolveProvider.register(NDArrayTypeResolveProvider)
    if IS_PYCHARM:
        TypeResolveProvider.register(NdArrayItemsContainerProvider)
        StrPresentationProvider.register(NDArrayStrProvider)
