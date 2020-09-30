"""
This is a docstring, that should be skipped.
"""
# Comments as well
try:
    # even here
    from pkg_resources import declare_namespace
    # and here

    declare_namespace(__name__)
except ImportError:
    __path__ = __import__('pkgutil').extend_path(__path__, __name__)