# $Id: __init__.py 5618 2008-07-28 08:37:32Z strank $
# Author: David Goodger <goodger@python.org>
# Copyright: This module has been placed in the public domain.

# Internationalization details are documented in
# <http://docutils.sf.net/docs/howto/i18n.html>.

"""
This package contains modules for language-dependent features of Docutils.
"""

__docformat__ = 'reStructuredText'

_languages = {}

def get_language(language_code):
    if language_code in _languages:
        return _languages[language_code]
    module = __import__(language_code, globals(), locals())
    _languages[language_code] = module
    return module
