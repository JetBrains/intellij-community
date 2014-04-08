try:
  from urlparse import parse_qs, parse_qsl
except ImportError:
  from tmp import bar