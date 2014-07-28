"""The version and URL for coverage.py"""
# This file is exec'ed in setup.py, don't import anything!

__version__ = "3.7.1"                   # see detailed history in CHANGES.txt

__url__ = "http://nedbatchelder.com/code/coverage"
if max(__version__).isalpha():
    # For pre-releases, use a version-specific URL.
    __url__ += "/" + __version__
