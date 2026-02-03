# This is a comment in __init__ file
# to check that we still understand it's a part of namespace package
#

__path__= __import__('pkgutil').extend_path(__path__, __name__) # can be on the same line