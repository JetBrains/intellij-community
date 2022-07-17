# Package versioning solution originally found here:
# http://stackoverflow.com/q/458550

__all__ = ["__version__"]

# Store the version here so:
# 1) we don't load dependencies by storing it in __init__.py
# 2) we can import it in setup.py for the same reason
# 3) we can import it into your module
__version__ = "0.9.1"
