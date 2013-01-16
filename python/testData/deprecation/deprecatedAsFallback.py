try:
  from urlparse import parse_qs, parse_qsl
except ImportError:
  from cgi import parse_qs, parse_qsl