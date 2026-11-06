import inspect
import sys

from _pydevd_bundle.pydevd_constants import MethodWrapperType
from _pydevd_bundle.pydevd_extension_api import TypeResolveProvider
from _pydevd_bundle.pydevd_resolver import defaultResolver
from .pydevd_helpers import find_mod_attr


class TorchTensorTypeResolveProvider(object):
    """
    Resolves a torch.Tensor with the default resolver, but hides two kinds of attributes.

    H, T, mH, mT, data, real and imag are not read. Each of them is a new tensor with the same attributes,
    so the variable tree never ends. The methods are not shown. A tensor has more than 500 methods,
    and they make the Special Variables group too large.
    """

    TENSOR_VIEW_ATTRIBUTES = frozenset(("H", "T", "mH", "mT", "data", "real", "imag"))

    def can_provide(self, type_object, type_name):
        tensor_class = find_mod_attr("torch", "Tensor")
        return tensor_class is not None and inspect.isclass(type_object) and issubclass(type_object, tensor_class)

    def resolve(self, obj, attribute):
        return defaultResolver.resolve(obj, attribute)

    def get_dictionary(self, obj):
        names = [name for name in dir(obj) if name not in self.TENSOR_VIEW_ATTRIBUTES]
        dct = defaultResolver.get_dictionary(obj, names=names)
        return {name: value for name, value in dct.items() if not self._is_method(value)}

    def get_contents_debug_adapter_protocol(self, obj, fmt=None):
        return [(name, value, ".%s" % name) for name, value in self.get_dictionary(obj).items()]

    @staticmethod
    def _is_method(value):
        return inspect.isroutine(value) or isinstance(value, MethodWrapperType)


if not sys.platform.startswith("java"):
    TypeResolveProvider.register(TorchTensorTypeResolveProvider)