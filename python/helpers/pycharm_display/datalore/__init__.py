from pkgutil import extend_path

# To handle the situation when 'datalore' package is shared my modules in different locations.
__path__ = extend_path(__path__, __name__)
