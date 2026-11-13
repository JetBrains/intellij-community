import inspect
from array import array
from collections import deque

from _pydevd_bundle.custom.pydevd_asyncio_provider import \
    get_eval_async_expression_in_context
from _pydevd_bundle.custom.pydevd_constants import dict_iter_items, ValuesPolicy, \
    LOAD_VALUES_POLICY

try:
    from collections import OrderedDict
except:
    OrderedDict = dict

class VariableWithOffset(object):
    def __init__(self, data, offset):
        self.data, self.offset = data, offset


def eval_expression(expression, globals, locals):
    eval_func = get_eval_async_expression_in_context()
    if eval_func is not None:
        return eval_func(expression, globals, locals, False)

    return eval(expression, globals, locals)

def get_var_and_offset(var):
    if isinstance(var, VariableWithOffset):
        return var.data, var.offset
    return var, 0

def take_first_n_coll_elements(coll, n):
    if coll.__class__ in (list, tuple, array, str):
        return coll[:n]
    elif coll.__class__ in (set, frozenset, deque):
        buf = []
        for i, x in enumerate(coll):
            if i >= n:
                break
            buf.append(x)
        return type(coll)(buf)
    elif coll.__class__ in (dict, OrderedDict):
        ret = type(coll)()
        for i, (k, v) in enumerate(dict_iter_items(coll)):
            if i >= n:
                break
            ret[k] = v
        return ret
    else:
        raise TypeError("Unsupported collection type: '%s'" % str(coll.__class__))

def should_evaluate_shape():
    return LOAD_VALUES_POLICY != ValuesPolicy.ON_DEMAND

def _is_numpy_scalar_type(x):
    if getattr(x, '__module__', None) != 'numpy':
        return False
    type_name = x.__name__
    return type_name in ('dtype', 'bool_', 'str_') or 'int' in type_name or 'float' in type_name or 'complex' in type_name

def should_load_value_on_demand(val):
    """
    Whether the Variables view gets the value of `val` only on request, with the On Demand policy.

    The rule is the one of pydevd: a builtin or a numpy scalar is shown at once, because its repr runs no code of the
    user. A list, a tuple, a dict and every other object wait for the request.
    """
    if LOAD_VALUES_POLICY != ValuesPolicy.ON_DEMAND:
        return False
    val_type = type(val)
    is_simple = getattr(val_type, '__module__', None) == 'builtins' or _is_numpy_scalar_type(val_type)
    return not is_simple or isinstance(val, (list, tuple, dict))

def has_attribute_safe(obj, attr_name):
    """Evaluates the existence of attribute without accessing it."""
    attr = inspect.getattr_static(obj, attr_name, None)
    return attr is not None
