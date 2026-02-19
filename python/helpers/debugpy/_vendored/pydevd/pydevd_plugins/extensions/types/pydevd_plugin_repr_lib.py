from typing import Any

from _pydevd_bundle.custom.pydevd_repr_utils import get_value_repr
from _pydevd_bundle.pydevd_extension_api import StrPresentationProvider
from pydevd_plugins.extensions.types.pydevd_plugin_numpy_types import NDArrayStrProvider
from pydevd_plugins.extensions.types.pydevd_plugins_django_form_str import DjangoFormStr


class PydevdReprStrProvider(StrPresentationProvider):
    def can_provide(self, type_object, type_name):
        # this provider can resolve anything that is not otherwise custom handled by our
        # other custom resolvers, such as NDArrayStrProvider
        return (not NDArrayStrProvider.can_provide(self, type_object, type_name)
                and not DjangoFormStr.can_provide(self, type_object, type_name))

    def get_str_in_context(self, val: Any, context: str):
        return self.get_str(val)

    def get_str(self, val, do_trim=True):
        return get_value_repr(val)

