from typing_extensions import TypeAlias

# noinspection PyUnresolvedReferences
from .base import clear_script_prefix as clear_script_prefix
from .base import clear_url_caches as clear_url_caches
from .base import get_script_prefix as get_script_prefix
from .base import get_urlconf as get_urlconf
from .base import is_valid_path as is_valid_path
from .base import resolve as resolve
from .base import reverse as reverse
from .base import reverse_lazy as reverse_lazy
from .base import set_script_prefix as set_script_prefix
from .base import set_urlconf as set_urlconf
from .base import translate_url as translate_url

# noinspection PyUnresolvedReferences
from .conf import include as include
from .conf import path as path
from .conf import re_path as re_path

# noinspection PyUnresolvedReferences
from .converters import register_converter as register_converter

# noinspection PyUnresolvedReferences
from .exceptions import NoReverseMatch as NoReverseMatch
from .exceptions import Resolver404 as Resolver404

# noinspection PyUnresolvedReferences
from .resolvers import LocalePrefixPattern as LocalePrefixPattern
from .resolvers import ResolverMatch as ResolverMatch
from .resolvers import URLPattern as URLPattern
from .resolvers import URLResolver as URLResolver
from .resolvers import get_ns_resolver as get_ns_resolver
from .resolvers import get_resolver as get_resolver

# noinspection PyUnresolvedReferences
from .utils import get_callable as get_callable
from .utils import get_mod_func as get_mod_func

_AnyURL: TypeAlias = URLPattern | URLResolver  # noqa: PYI047
