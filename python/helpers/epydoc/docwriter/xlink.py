"""
A Docutils_ interpreted text role for cross-API reference support.

This module allows a Docutils_ document to refer to elements defined in
external API documentation. It is possible to refer to many external API
from the same document.

Each API documentation is assigned a new interpreted text role: using such
interpreted text, an user can specify an object name inside an API
documentation. The system will convert such text into an url and generate a
reference to it. For example, if the API ``db`` is defined, being a database
package, then a certain method may be referred as::

    :db:`Connection.cursor()`

To define a new API, an *index file* must be provided. This file contains
a mapping from the object name to the URL part required to resolve such object.

Index file
----------

Each line in the the index file describes an object.

Each line contains the fully qualified name of the object and the URL at which
the documentation is located. The fields are separated by a ``<tab>``
character.

The URL's in the file are relative from the documentation root: the system can
be configured to add a prefix in front of each returned URL.

Allowed names
-------------

When a name is used in an API text role, it is split over any *separator*.
The separators defined are '``.``', '``::``', '``->``'. All the text from the
first noise char (neither a separator nor alphanumeric or '``_``') is
discarded. The same algorithm is applied when the index file is read.

First the sequence of name parts is looked for in the provided index file.
If no matching name is found, a partial match against the trailing part of the
names in the index is performed. If no object is found, or if the trailing part
of the name may refer to many objects, a warning is issued and no reference
is created.

Configuration
-------------

This module provides the class `ApiLinkReader` a replacement for the Docutils
standalone reader. Such reader specifies the settings required for the
API canonical roles configuration. The same command line options are exposed by
Epydoc.

The script ``apirst2html.py`` is a frontend for the `ApiLinkReader` reader.

API Linking Options::

    --external-api=NAME
                        Define a new API document.  A new interpreted text
                        role NAME will be added.
    --external-api-file=NAME:FILENAME
                        Use records in FILENAME to resolve objects in the API
                        named NAME.
    --external-api-root=NAME:STRING
                        Use STRING as prefix for the URL generated from the
                        API NAME.

.. _Docutils: http://docutils.sourceforge.net/
"""

# $Id: xlink.py 1586 2007-03-14 01:53:42Z dvarrazzo $
__version__ = "$Revision: 1586 $"[11:-2]
__author__ = "Daniele Varrazzo"
__copyright__ = "Copyright (C) 2007 by Daniele Varrazzo"
__docformat__ = 'reStructuredText en'

import re
import sys
from optparse import OptionValueError

from epydoc import log

class UrlGenerator:
    """
    Generate URL from an object name.
    """
    class IndexAmbiguous(IndexError):
        """
        The name looked for is ambiguous
        """

    def get_url(self, name):
        """Look for a name and return the matching URL documentation.

        First look for a fully qualified name. If not found, try with partial
        name.

        If no url exists for the given object, return `None`.

        :Parameters:
          `name` : `str`
            the name to look for

        :return: the URL that can be used to reach the `name` documentation.
            `None` if no such URL exists.
        :rtype: `str`

        :Exceptions:
          - `IndexError`: no object found with `name`
          - `DocUrlGenerator.IndexAmbiguous` : more than one object found with
            a non-fully qualified name; notice that this is an ``IndexError``
            subclass
        """
        raise NotImplementedError

    def get_canonical_name(self, name):
        """
        Convert an object name into a canonical name.

        the canonical name of an object is a tuple of strings containing its
        name fragments, splitted on any allowed separator ('``.``', '``::``',
        '``->``').

        Noise such parenthesis to indicate a function is discarded.

        :Parameters:
          `name` : `str`
            an object name, such as ``os.path.prefix()`` or ``lib::foo::bar``

        :return: the fully qualified name such ``('os', 'path', 'prefix')`` and
            ``('lib', 'foo', 'bar')``
        :rtype: `tuple` of `str`
        """
        rv = []
        for m in self._SEP_RE.finditer(name):
            groups = m.groups()
            if groups[0] is not None:
                rv.append(groups[0])
            elif groups[2] is not None:
                break

        return tuple(rv)

    _SEP_RE = re.compile(r"""(?x)
        # Tokenize the input into keyword, separator, noise
        ([a-zA-Z0-9_]+)         |   # A keyword is a alphanum word
        ( \. | \:\: | \-\> )    |   # These are the allowed separators
        (.)                         # If it doesn't fit, it's noise.
            # Matching a single noise char is enough, because it
            # is used to break the tokenization as soon as some noise
            # is found.
        """)


class VoidUrlGenerator(UrlGenerator):
    """
    Don't actually know any url, but don't report any error.

    Useful if an index file is not available, but a document linking to it
    is to be generated, and warnings are to be avoided.

    Don't report any object as missing, Don't return any url anyway.
    """
    def get_url(self, name):
        return None


class DocUrlGenerator(UrlGenerator):
    """
    Read a *documentation index* and generate URL's for it.
    """
    def __init__(self):
        self._exact_matches = {}
        """
        A map from an object fully qualified name to its URL.

        Values are both the name as tuple of fragments and as read from the
        records (see `load_records()`), mostly to help `_partial_names` to
        perform lookup for unambiguous names.
        """

        self._partial_names= {}
        """
        A map from partial names to the fully qualified names they may refer.

        The keys are the possible left sub-tuples of fully qualified names,
        the values are list of strings as provided by the index.

        If the list for a given tuple contains a single item, the partial
        match is not ambuguous. In this case the string can be looked up in
        `_exact_matches`.

        If the name fragment is ambiguous, a warning may be issued to the user.
        The items can be used to provide an informative message to the user,
        to help him qualifying the name in a unambiguous manner.
        """

        self.prefix = ''
        """
        Prefix portion for the URL's returned by `get_url()`.
        """

        self._filename = None
        """
        Not very important: only for logging.
        """

    def get_url(self, name):
        cname = self.get_canonical_name(name)
        url = self._exact_matches.get(cname, None)
        if url is None:

            # go for a partial match
            vals = self._partial_names.get(cname)
            if vals is None:
                raise IndexError(
                    "no object named '%s' found" % (name))

            elif len(vals) == 1:
                url = self._exact_matches[vals[0]]

            else:
                raise self.IndexAmbiguous(
                    "found %d objects that '%s' may refer to: %s"
                    % (len(vals), name, ", ".join(["'%s'" % n for n in vals])))

        return self.prefix + url

    #{ Content loading
    #  ---------------

    def clear(self):
        """
        Clear the current class content.
        """
        self._exact_matches.clear()
        self._partial_names.clear()

    def load_index(self, f):
        """
        Read the content of an index file.

        Populate the internal maps with the file content using `load_records()`.

        :Parameters:
          f : `str` or file
            a file name or file-like object fron which read the index.
        """
        self._filename = str(f)

        if isinstance(f, basestring):
            f = open(f)

        self.load_records(self._iter_tuples(f))

    def _iter_tuples(self, f):
        """Iterate on a file returning 2-tuples."""
        for nrow, row in enumerate(f):
            # skip blank lines
            row = row.rstrip()
            if not row: continue

            rec = row.split('\t', 2)
            if len(rec) == 2:
                yield rec
            else:
                log.warning("invalid row in '%s' row %d: '%s'"
                            % (self._filename, nrow+1, row))

    def load_records(self, records):
        """
        Read a sequence of pairs name -> url and populate the internal maps.

        :Parameters:
          records : iterable
            the sequence of pairs (*name*, *url*) to add to the maps.
        """
        for name, url in records:
            cname = self.get_canonical_name(name)
            if not cname:
                log.warning("invalid object name in '%s': '%s'"
                    % (self._filename, name))
                continue

            # discard duplicates
            if name in self._exact_matches:
                continue

            self._exact_matches[name] = url
            self._exact_matches[cname] = url

            # Link the different ambiguous fragments to the url
            for i in range(1, len(cname)):
                self._partial_names.setdefault(cname[i:], []).append(name)

#{ API register
#  ------------

api_register = {}
"""
Mapping from the API name to the `UrlGenerator` to be used.

Use `register_api()` to add new generators to the register.
"""

def register_api(name, generator=None):
    """Register the API `name` into the `api_register`.

    A registered API will be available to the markup as the interpreted text
    role ``name``.

    If a `generator` is not provided, register a `VoidUrlGenerator` instance:
    in this case no warning will be issued for missing names, but no URL will
    be generated and all the dotted names will simply be rendered as literals.

    :Parameters:
      `name` : `str`
        the name of the generator to be registered
      `generator` : `UrlGenerator`
        the object to register to translate names into URLs.
    """
    if generator is None:
        generator = VoidUrlGenerator()

    api_register[name] = generator

def set_api_file(name, file):
    """Set an URL generator populated with data from `file`.

    Use `file` to populate a new `DocUrlGenerator` instance and register it
    as `name`.

    :Parameters:
      `name` : `str`
        the name of the generator to be registered
      `file` : `str` or file
        the file to parse populate the URL generator
    """
    generator = DocUrlGenerator()
    generator.load_index(file)
    register_api(name, generator)

def set_api_root(name, prefix):
    """Set the root for the URLs returned by a registered URL generator.

    :Parameters:
      `name` : `str`
        the name of the generator to be updated
      `prefix` : `str`
        the prefix for the generated URL's

    :Exceptions:
      - `IndexError`: `name` is not a registered generator
    """
    api_register[name].prefix = prefix

######################################################################
# Below this point requires docutils.
try:
    import docutils
    from docutils.parsers.rst import roles
    from docutils import nodes, utils
    from docutils.readers.standalone import Reader
except ImportError:
    docutils = roles = nodes = utils = None
    class Reader: settings_spec = ()

def create_api_role(name, problematic):
    """
    Create and register a new role to create links for an API documentation.

    Create a role called `name`, which will use the URL resolver registered as
    ``name`` in `api_register` to create a link for an object.

    :Parameters:
      `name` : `str`
        name of the role to create.
      `problematic` : `bool`
        if True, the registered role will create problematic nodes in
        case of failed references. If False, a warning will be raised
        anyway, but the output will appear as an ordinary literal.
    """
    def resolve_api_name(n, rawtext, text, lineno, inliner,
                options={}, content=[]):
        if docutils is None:
            raise AssertionError('requires docutils')

        # node in monotype font
        text = utils.unescape(text)
        node = nodes.literal(rawtext, text, **options)

        # Get the resolver from the register and create an url from it.
        try:
            url = api_register[name].get_url(text)
        except IndexError, exc:
            msg = inliner.reporter.warning(str(exc), line=lineno)
            if problematic:
                prb = inliner.problematic(rawtext, text, msg)
                return [prb], [msg]
            else:
                return [node], []

        if url is not None:
            node = nodes.reference(rawtext, '', node, refuri=url, **options)
        return [node], []

    roles.register_local_role(name, resolve_api_name)


#{ Command line parsing
#  --------------------


def split_name(value):
    """
    Split an option in form ``NAME:VALUE`` and check if ``NAME`` exists.
    """
    parts = value.split(':', 1)
    if len(parts) != 2:
        raise OptionValueError(
            "option value must be specified as NAME:VALUE; got '%s' instead"
            % value)

    name, val = parts

    if name not in api_register:
        raise OptionValueError(
            "the name '%s' has not been registered; use --external-api"
            % name)

    return (name, val)


class ApiLinkReader(Reader):
    """
    A Docutils standalone reader allowing external documentation links.

    The reader configure the url resolvers at the time `read()` is invoked the
    first time.
    """
    #: The option parser configuration.
    settings_spec = (
    'API Linking Options',
    None,
    ((
        'Define a new API document.  A new interpreted text role NAME will be '
        'added.',
        ['--external-api'],
        {'metavar': 'NAME', 'action': 'append'}
    ), (
        'Use records in FILENAME to resolve objects in the API named NAME.',
        ['--external-api-file'],
        {'metavar': 'NAME:FILENAME', 'action': 'append'}
    ), (
        'Use STRING as prefix for the URL generated from the API NAME.',
        ['--external-api-root'],
        {'metavar': 'NAME:STRING', 'action': 'append'}
    ),)) + Reader.settings_spec

    def __init__(self, *args, **kwargs):
        if docutils is None:
            raise AssertionError('requires docutils')
        Reader.__init__(self, *args, **kwargs)

    def read(self, source, parser, settings):
        self.read_configuration(settings, problematic=True)
        return Reader.read(self, source, parser, settings)

    def read_configuration(self, settings, problematic=True):
        """
        Read the configuration for the configured URL resolver.

        Register a new role for each configured API.

        :Parameters:
          `settings`
            the settings structure containing the options to read.
          `problematic` : `bool`
            if True, the registered role will create problematic nodes in
            case of failed references. If False, a warning will be raised
            anyway, but the output will appear as an ordinary literal.
        """
        # Read config only once
        if hasattr(self, '_conf'):
            return
        ApiLinkReader._conf = True

        try:
            if settings.external_api is not None:
                for name in settings.external_api:
                    register_api(name)
                    create_api_role(name, problematic=problematic)

            if settings.external_api_file is not None:
                for name, file in map(split_name, settings.external_api_file):
                    set_api_file(name, file)

            if settings.external_api_root is not None:
                for name, root in map(split_name, settings.external_api_root):
                    set_api_root(name, root)

        except OptionValueError, exc:
            print >>sys.stderr, "%s: %s" % (exc.__class__.__name__, exc)
            sys.exit(2)

    read_configuration = classmethod(read_configuration)
