from collections import OrderedDict as OrderedDict
from urllib.parse import (
    quote as quote,
    quote_plus as quote_plus,
    unquote as unquote,
    unquote_plus as unquote_plus,
    urldefrag as urldefrag,
    urlencode as urlencode,
    urljoin as urljoin,
    urlparse as urlparse,
    urlsplit as urlsplit,
    urlunparse as urlunparse,
)
from urllib.request import getproxies as getproxies, parse_http_list as parse_http_list, proxy_bypass as proxy_bypass

is_py2: bool
is_py3: bool
has_simplejson: bool
