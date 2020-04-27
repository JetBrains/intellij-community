# This is a comment in __init__ file

from pkgutil import extend_path # can be on the same line

__path__ = extend_path(__path__, __name__)

# to check that we still understand it's a part of namespace package
