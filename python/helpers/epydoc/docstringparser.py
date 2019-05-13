# epydoc -- Docstring processing
#
# Copyright (C) 2005 Edward Loper
# Author: Edward Loper <edloper@loper.org>
# URL: <http://epydoc.sf.net>
#
# $Id: docstringparser.py 1689 2008-01-30 17:01:02Z edloper $

"""
Parse docstrings and handle any fields it defines, such as C{@type}
and C{@author}.  Fields are used to describe specific information
about an object.  There are two classes of fields: X{simple fields}
and X{special fields}.

Simple fields are fields that get stored directly in an C{APIDoc}'s
metadata dictionary, without any special processing.  The set of
simple fields is defined by the list L{STANDARD_FIELDS}, whose
elements are L{DocstringField}s.

Special fields are fields that perform some sort of processing on the
C{APIDoc}, or add information to attributes other than the metadata
dictionary.  Special fields are are handled by field handler
functions, which are registered using L{register_field_handler}.
"""
__docformat__ = 'epytext en'


######################################################################
## Imports
######################################################################

import re, sys
from epydoc import markup
from epydoc.markup import epytext
from epydoc.apidoc import *
from epydoc.docintrospecter import introspect_docstring_lineno
from epydoc.util import py_src_filename
from epydoc import log
import epydoc.docparser
import __builtin__, exceptions

######################################################################
# Docstring Fields
######################################################################

class DocstringField:
    """
    A simple docstring field, which can be used to describe specific
    information about an object, such as its author or its version.
    Simple docstring fields are fields that take no arguments, and
    are displayed as simple sections.

    @ivar tags: The set of tags that can be used to identify this
        field.
    @ivar singular: The label that should be used to identify this
        field in the output, if the field contains one value.
    @ivar plural: The label that should be used to identify this
        field in the output, if the field contains multiple values.
    @ivar short: If true, then multiple values should be combined
        into a single comma-delimited list.  If false, then
        multiple values should be listed separately in a bulleted
        list.
    @ivar multivalue: If true, then multiple values may be given
        for this field; if false, then this field can only take a
        single value, and a warning should be issued if it is
        redefined.
    @ivar takes_arg: If true, then this field expects an argument;
        and a separate field section will be constructed for each
        argument value.  The label (and plural label) should include
        a '%s' to mark where the argument's string rep should be
        added.
    """
    def __init__(self, tags, label, plural=None,
                 short=0, multivalue=1, takes_arg=0,
                 varnames=None):
        if type(tags) in (list, tuple):
            self.tags = tuple(tags)
        elif type(tags) is str:
            self.tags = (tags,)
        else: raise TypeError('Bad tags: %s' % tags)
        self.singular = label
        if plural is None: self.plural = label
        else: self.plural = plural
        self.multivalue = multivalue
        self.short = short
        self.takes_arg = takes_arg
        self.varnames = varnames or []

    def __cmp__(self, other):
        if not isinstance(other, DocstringField): return -1
        return cmp(self.tags, other.tags)
    
    def __hash__(self):
        return hash(self.tags)

    def __repr__(self):
        return '<Field: %s>' % self.tags[0]

STANDARD_FIELDS = [
    #: A list of the standard simple fields accepted by epydoc.  This
    #: list can be augmented at run-time by a docstring with the special
    #: C{@deffield} field.  The order in which fields are listed here
    #: determines the order in which they will be displayed in the
    #: output.
    
    # If it's deprecated, put that first.
    DocstringField(['deprecated', 'depreciated'],
             'Deprecated', multivalue=0, varnames=['__deprecated__']),

    # Status info
    DocstringField(['version'], 'Version', multivalue=0,
                   varnames=['__version__']),
    DocstringField(['date'], 'Date', multivalue=0,
                   varnames=['__date__']),
    DocstringField(['status'], 'Status', multivalue=0),
    
    # Bibliographic Info
    DocstringField(['author', 'authors'], 'Author', 'Authors', short=1,
                   varnames=['__author__', '__authors__']),
    DocstringField(['contact'], 'Contact', 'Contacts', short=1,
                   varnames=['__contact__']),
    DocstringField(['organization', 'org'],
                   'Organization', 'Organizations'),
    DocstringField(['copyright', '(c)'], 'Copyright', multivalue=0,
                   varnames=['__copyright__']),
    DocstringField(['license'], 'License', multivalue=0,
                   varnames=['__license__']),

    # Various warnings etc.
    DocstringField(['bug'], 'Bug', 'Bugs'),
    DocstringField(['warning', 'warn'], 'Warning', 'Warnings'),
    DocstringField(['attention'], 'Attention'),
    DocstringField(['note'], 'Note', 'Notes'),

    # Formal conditions
    DocstringField(['requires', 'require', 'requirement'], 'Requires'),
    DocstringField(['precondition', 'precond'],
             'Precondition', 'Preconditions'),
    DocstringField(['postcondition', 'postcond'],
             'Postcondition', 'Postconditions'),
    DocstringField(['invariant'], 'Invariant'),

    # When was it introduced (version # or date)
    DocstringField(['since'], 'Since', multivalue=0),

    # Changes made
    DocstringField(['change', 'changed'], 'Change Log'),
                   
    # Crossreferences
    DocstringField(['see', 'seealso'], 'See Also', short=1),

    # Future Work
    DocstringField(['todo'], 'To Do', takes_arg=True),

    # Permissions (used by zope-based projects)
    DocstringField(['permission', 'permissions'], 'Permission', 'Permissions')
    ]

######################################################################
#{ Docstring Parsing
######################################################################

DEFAULT_DOCFORMAT = 'epytext'
"""The name of the default markup languge used to process docstrings."""

# [xx] keep track of which ones we've already done, in case we're
# asked to process one twice?  e.g., for @include we might have to
# parse the included docstring earlier than we might otherwise..??

def parse_docstring(api_doc, docindex, suppress_warnings=[]):
    """
    Process the given C{APIDoc}'s docstring.  In particular, populate
    the C{APIDoc}'s C{descr} and C{summary} attributes, and add any
    information provided by fields in the docstring.
    
    @param docindex: A DocIndex, used to find the containing
        module (to look up the docformat); and to find any
        user docfields defined by containing objects.
    @param suppress_warnings: A set of objects for which docstring
        warnings should be suppressed.
    """
    if api_doc.metadata is not UNKNOWN:
        if not (isinstance(api_doc, RoutineDoc)
                and api_doc.canonical_name[-1] == '__init__'):
            log.debug("%s's docstring processed twice" %
                      api_doc.canonical_name)
        return
        
    initialize_api_doc(api_doc)

    # If there's no docstring, then check for special variables (e.g.,
    # __version__), and then return -- there's nothing else to do.
    if (api_doc.docstring in (None, UNKNOWN)):
        if isinstance(api_doc, NamespaceDoc):
            for field in STANDARD_FIELDS + user_docfields(api_doc, docindex):
                add_metadata_from_var(api_doc, field)
        return

    # Remove leading indentation from the docstring.
    api_doc.docstring = unindent_docstring(api_doc.docstring)

    # Decide which docformat is used by this module.
    docformat = get_docformat(api_doc, docindex)

    # A list of markup errors from parsing.
    parse_errors = []
    
    # Extract a signature from the docstring, if it has one.  This
    # overrides any signature we got via introspection/parsing.
    if isinstance(api_doc, RoutineDoc):
        parse_function_signature(api_doc, None, docformat, parse_errors)

    # Parse the docstring.  Any errors encountered are stored as
    # `ParseError` objects in the errors list.
    parsed_docstring = markup.parse(api_doc.docstring, docformat,
                                    parse_errors)
        
    # Divide the docstring into a description and a list of
    # fields.
    descr, fields = parsed_docstring.split_fields(parse_errors)
    api_doc.descr = descr

    field_warnings = []

    # Handle the constructor fields that have been defined in the class
    # docstring. This code assumes that a class docstring is parsed before
    # the same class __init__ docstring.
    if isinstance(api_doc, ClassDoc):

        # Parse ahead the __init__ docstring for this class
        initvar = api_doc.variables.get('__init__')
        if initvar and isinstance(initvar.value, RoutineDoc):
            init_api_doc = initvar.value
            parse_docstring(init_api_doc, docindex, suppress_warnings)

            parse_function_signature(init_api_doc, api_doc,
                                     docformat, parse_errors)
            init_fields = split_init_fields(fields, field_warnings)

            # Process fields
            for field in init_fields:
                try:
                    process_field(init_api_doc, docindex, field.tag(),
                                    field.arg(), field.body())
                except ValueError, e: field_warnings.append(str(e))

    # Process fields
    for field in fields:
        try:
            process_field(api_doc, docindex, field.tag(),
                               field.arg(), field.body())
        except ValueError, e: field_warnings.append(str(e))

    # Check to make sure that all type parameters correspond to
    # some documented parameter.
    check_type_fields(api_doc, field_warnings)

    # Check for special variables (e.g., __version__)
    if isinstance(api_doc, NamespaceDoc):
        for field in STANDARD_FIELDS + user_docfields(api_doc, docindex):
            add_metadata_from_var(api_doc, field)

    # Extract a summary
    if api_doc.summary is None and api_doc.descr is not None:
        api_doc.summary, api_doc.other_docs = api_doc.descr.summary()

    # If the summary is empty, but the return field is not, then use
    # the return field to generate a summary description.
    if (isinstance(api_doc, RoutineDoc) and api_doc.summary is None and
        api_doc.return_descr is not None):
        s, o = api_doc.return_descr.summary()
        api_doc.summary = RETURN_PDS + s
        api_doc.other_docs = o

    # [XX] Make sure we don't have types/param descrs for unknown
    # vars/params?

    # Report any errors that occured
    if api_doc in suppress_warnings:
        if parse_errors or field_warnings:
            log.info("Suppressing docstring warnings for %s, since it "
                     "is not included in the documented set." %
                     api_doc.canonical_name)
    else:
        report_errors(api_doc, docindex, parse_errors, field_warnings)

def add_metadata_from_var(api_doc, field):
    for varname in field.varnames:
        # Check if api_doc has a variable w/ the given name.
        if varname not in api_doc.variables: continue

        # Check moved here from before the for loop because we expect to
        # reach rarely this point. The loop below is to be performed more than
        # once only for fields with more than one varname, which currently is
        # only 'author'.
        for md in api_doc.metadata:
            if field == md[0]:
                return # We already have a value for this metadata.

        var_doc = api_doc.variables[varname]
        if var_doc.value is UNKNOWN: continue
        val_doc = var_doc.value
        value = []

        # Try extracting the value from the pyval.
        ok_types = (basestring, int, float, bool, type(None))
        if val_doc.pyval is not UNKNOWN:
            if isinstance(val_doc.pyval, ok_types):
                value = [val_doc.pyval]
            elif field.multivalue:
                if isinstance(val_doc.pyval, (tuple, list)):
                    for elt in val_doc.pyval:
                        if not isinstance(elt, ok_types): break
                    else:
                        value = list(val_doc.pyval)

        # Try extracting the value from the parse tree.
        elif val_doc.toktree is not UNKNOWN:
            try: value = [epydoc.docparser.parse_string(val_doc.toktree)]
            except KeyboardInterrupt: raise
            except: pass
            if field.multivalue and not value:
                try: value = epydoc.docparser.parse_string_list(val_doc.toktree)
                except KeyboardInterrupt: raise
                except: raise
                
        # Add any values that we found.
        for elt in value:
            if isinstance(elt, str):
                elt = decode_with_backslashreplace(elt)
            else:
                elt = unicode(elt)
            elt = epytext.ParsedEpytextDocstring(
                epytext.parse_as_para(elt), inline=True)

            # Add in the metadata and remove from the variables
            api_doc.metadata.append( (field, varname, elt) )

        # Remove the variable itself (unless it's documented)
        if var_doc.docstring in (None, UNKNOWN):
            del api_doc.variables[varname]
            if api_doc.sort_spec is not UNKNOWN:
                try: api_doc.sort_spec.remove(varname)
                except ValueError: pass

def initialize_api_doc(api_doc):
    """A helper function for L{parse_docstring()} that initializes
    the attributes that C{parse_docstring()} will write to."""
    if api_doc.descr is UNKNOWN:
        api_doc.descr = None
    if api_doc.summary is UNKNOWN:
        api_doc.summary = None
    if api_doc.metadata is UNKNOWN:
        api_doc.metadata = []
    if isinstance(api_doc, RoutineDoc):
        if api_doc.arg_descrs is UNKNOWN:
            api_doc.arg_descrs = []
        if api_doc.arg_types is UNKNOWN:
            api_doc.arg_types = {}
        if api_doc.return_descr is UNKNOWN:
            api_doc.return_descr = None
        if api_doc.return_type is UNKNOWN:
            api_doc.return_type = None
        if api_doc.exception_descrs is UNKNOWN:
            api_doc.exception_descrs = []
    if isinstance(api_doc, (VariableDoc, PropertyDoc)):
        if api_doc.type_descr is UNKNOWN:
            api_doc.type_descr = None
    if isinstance(api_doc, NamespaceDoc):
        if api_doc.group_specs is UNKNOWN:
            api_doc.group_specs = []
        if api_doc.sort_spec is UNKNOWN:
            api_doc.sort_spec = []

def split_init_fields(fields, warnings):
    """
    Remove the fields related to the constructor from a class docstring
    fields list.

    @param fields: The fields to process. The list will be modified in place
    @type fields: C{list} of L{markup.Field}
    @param warnings: A list to emit processing warnings
    @type warnings: C{list}
    @return: The C{fields} items to be applied to the C{__init__} method
    @rtype: C{list} of L{markup.Field}
    """
    init_fields = []

    # Split fields in lists according to their argument, keeping order.
    arg_fields = {}
    args_order = []
    i = 0
    while i < len(fields):
        field = fields[i]

        # gather together all the fields with the same arg
        if field.arg() is not None:
            arg_fields.setdefault(field.arg(), []).append(fields.pop(i))
            args_order.append(field.arg())
        else:
            i += 1

    # Now check that for each argument there is at most a single variable
    # and a single parameter, and at most a single type for each of them.
    for arg in args_order:
        ff = arg_fields.pop(arg, None)
        if ff is None:
            continue

        var = tvar = par = tpar = None
        for field in ff:
            if field.tag() in VARIABLE_TAGS:
                if var is None:
                    var = field
                    fields.append(field)
                else:
                    warnings.append(
                        "There is more than one variable named '%s'"
                        % arg)
            elif field.tag() in PARAMETER_TAGS:
                if par is None:
                    par = field
                    init_fields.append(field)
                else:
                    warnings.append(
                        "There is more than one parameter named '%s'"
                        % arg)

            elif field.tag() == 'type':
                if var is None and par is None:
                    # type before obj
                    tvar = tpar = field
                else:
                    if var is not None and tvar is None:
                        tvar = field
                    if par is not None and tpar is None:
                        tpar = field

            elif field.tag() in EXCEPTION_TAGS:
                init_fields.append(field)

            else: # Unespected field
                fields.append(field)

        # Put selected types into the proper output lists
        if tvar is not None:
            if var is not None:
                fields.append(tvar)
            else:
                pass # [xx] warn about type w/o object?

        if tpar is not None:
            if par is not None:
                init_fields.append(tpar)
            else:
                pass # [xx] warn about type w/o object?

    return init_fields

def report_errors(api_doc, docindex, parse_errors, field_warnings):
    """A helper function for L{parse_docstring()} that reports any
    markup warnings and field warnings that we encountered while
    processing C{api_doc}'s docstring."""
    if not parse_errors and not field_warnings: return

    # Get the name of the item containing the error, and the
    # filename of its containing module.
    name = api_doc.canonical_name
    module = api_doc.defining_module
    if module is not UNKNOWN and module.filename not in (None, UNKNOWN):
        try: filename = py_src_filename(module.filename)
        except: filename = module.filename
    else:
        filename = '??'

    # [xx] Don't report markup errors for standard builtins.
    # n.b. that we must use 'is' to compare pyvals here -- if we use
    # 'in' or '==', then a user __cmp__ method might raise an
    # exception, or lie.
    if isinstance(api_doc, ValueDoc) and api_doc != module:
        if module not in (None, UNKNOWN) and module.pyval is exceptions:
            return
        for builtin_val in __builtin__.__dict__.values():
            if builtin_val is api_doc.pyval:
                return
        
    # Get the start line of the docstring containing the error.
    startline = api_doc.docstring_lineno
    if startline in (None, UNKNOWN):
        startline = introspect_docstring_lineno(api_doc)
        if startline in (None, UNKNOWN):
            startline = None

    # Display a block header.
    header = 'File %s, ' % filename
    if startline is not None:
        header += 'line %d, ' % startline
    header += 'in %s' % name
    log.start_block(header)
    

    # Display all parse errors.  But first, combine any errors
    # with duplicate description messages.
    if startline is None:
        # remove dups, but keep original order:
        dups = {}
        for error in parse_errors:
            message = error.descr()
            if message not in dups:
                log.docstring_warning(message)
                dups[message] = 1
    else:
        # Combine line number fields for dup messages:
        messages = {} # maps message -> list of linenum
        for error in parse_errors:
            error.set_linenum_offset(startline)
            message = error.descr()
            messages.setdefault(message, []).append(error.linenum())
        message_items = messages.items()
        message_items.sort(lambda a,b:cmp(min(a[1]), min(b[1])))
        for message, linenums in message_items:
            linenums = [n for n in linenums if n is not None]
            if len(linenums) == 0:
                log.docstring_warning(message)
            elif len(linenums) == 1:
                log.docstring_warning("Line %s: %s" % (linenums[0], message))
            else:
                linenums = ', '.join(['%s' % l for l in linenums])
                log.docstring_warning("Lines %s: %s" % (linenums, message))

    # Display all field warnings.
    for warning in field_warnings:
        log.docstring_warning(warning)

    # End the message block.
    log.end_block()

RETURN_PDS = markup.parse('Returns:', markup='epytext')
"""A ParsedDocstring containing the text 'Returns'.  This is used to
construct summary descriptions for routines that have empty C{descr},
but non-empty C{return_descr}."""
RETURN_PDS._tree.children[0].attribs['inline'] = True

######################################################################
#{ Field Processing Error Messages
######################################################################

UNEXPECTED_ARG = '%r did not expect an argument'
EXPECTED_ARG = '%r expected an argument'
EXPECTED_SINGLE_ARG = '%r expected a single argument'
BAD_CONTEXT = 'Invalid context for %r'
REDEFINED = 'Redefinition of %s'
UNKNOWN_TAG = 'Unknown field tag %r'
BAD_PARAM = '@%s for unknown parameter %s'

######################################################################
#{ Field Processing
######################################################################

def process_field(api_doc, docindex, tag, arg, descr):
    """
    Process a single field, and use it to update C{api_doc}.  If
    C{tag} is the name of a special field, then call its handler
    function.  If C{tag} is the name of a simple field, then use
    C{process_simple_field} to process it.  Otherwise, check if it's a
    user-defined field, defined in this docstring or the docstring of
    a containing object; and if so, process it with
    C{process_simple_field}.

    @param tag: The field's tag, such as C{'author'}
    @param arg: The field's optional argument
    @param descr: The description following the field tag and
        argument.
    @raise ValueError: If a problem was encountered while processing
        the field.  The C{ValueError}'s string argument is an
        explanation of the problem, which should be displayed as a
        warning message.
    """
    # standard special fields
    if tag in _field_dispatch_table:
        handler = _field_dispatch_table[tag]
        handler(api_doc, docindex, tag, arg, descr)
        return

    # standard simple fields & user-defined fields
    for field in STANDARD_FIELDS + user_docfields(api_doc, docindex):
        if tag in field.tags:
            # [xx] check if it's redefined if it's not multivalue??
            if not field.takes_arg:
                _check(api_doc, tag, arg, expect_arg=False)
            api_doc.metadata.append((field, arg, descr))
            return

    # If we didn't handle the field, then report a warning.
    raise ValueError(UNKNOWN_TAG % tag)

def user_docfields(api_doc, docindex):
    """
    Return a list of user defined fields that can be used for the
    given object.  This list is taken from the given C{api_doc}, and
    any of its containing C{NamepaceDoc}s.

    @note: We assume here that a parent's docstring will always be
        parsed before its childrens'.  This is indeed the case when we
        are called via L{docbuilder.build_doc_index()}.  If a child's
        docstring is parsed before its parents, then its parent won't
        yet have had its C{extra_docstring_fields} attribute
        initialized.
    """
    docfields = []
    # Get any docfields from `api_doc` itself
    if api_doc.extra_docstring_fields not in (None, UNKNOWN):
        docfields += api_doc.extra_docstring_fields
    # Get any docfields from `api_doc`'s ancestors
    for i in range(len(api_doc.canonical_name)-1, 0, -1):
        ancestor = docindex.get_valdoc(api_doc.canonical_name[:i])
        if ancestor is not None \
        and ancestor.extra_docstring_fields not in (None, UNKNOWN):
            docfields += ancestor.extra_docstring_fields
    return docfields

_field_dispatch_table = {}
def register_field_handler(handler, *field_tags):
    """
    Register the given field handler function for processing any
    of the given field tags.  Field handler functions should
    have the following signature:

        >>> def field_handler(api_doc, docindex, tag, arg, descr):
        ...     '''update api_doc in response to the field.'''

    Where C{api_doc} is the documentation object to update;
    C{docindex} is a L{DocIndex} that can be used to look up the
    documentation for related objects; C{tag} is the field tag that
    was used; C{arg} is the optional argument; and C{descr} is the
    description following the field tag and argument.
    """
    for field_tag in field_tags:
        _field_dispatch_table[field_tag] = handler

######################################################################
#{ Field Handler Functions
######################################################################

def process_summary_field(api_doc, docindex, tag, arg, descr):
    """Store C{descr} in C{api_doc.summary}"""
    _check(api_doc, tag, arg, expect_arg=False)
    if api_doc.summary is not None:
        raise ValueError(REDEFINED % tag)
    api_doc.summary = descr

def process_include_field(api_doc, docindex, tag, arg, descr):
    """Copy the docstring contents from the object named in C{descr}"""
    _check(api_doc, tag, arg, expect_arg=False)
    # options:
    #   a. just append the descr to our own
    #   b. append descr and update metadata
    #   c. append descr and process all fields.
    # in any case, mark any errors we may find as coming from an
    # imported docstring.
    
    # how does this interact with documentation inheritance??
    raise ValueError('%s not implemented yet' % tag)

def process_undocumented_field(api_doc, docindex, tag, arg, descr):
    """Remove any documentation for the variables named in C{descr}"""
    _check(api_doc, tag, arg, context=NamespaceDoc, expect_arg=False)
    for ident in _descr_to_identifiers(descr):
        var_name_re = re.compile('^%s$' % ident.replace('*', '(.*)'))
        for var_name, var_doc in api_doc.variables.items():
            if var_name_re.match(var_name):
                # Remove the variable from `variables`.
                api_doc.variables.pop(var_name, None)
                if api_doc.sort_spec is not UNKNOWN:
                    try: api_doc.sort_spec.remove(var_name)
                    except ValueError: pass
        # For modules, remove any submodules that match var_name_re.
        if isinstance(api_doc, ModuleDoc):
            removed = set([m for m in api_doc.submodules
                           if var_name_re.match(m.canonical_name[-1])])
            if removed:
                # Remove the indicated submodules from this module.
                api_doc.submodules = [m for m in api_doc.submodules
                                      if m not in removed]
                # Remove all ancestors of the indicated submodules
                # from the docindex root.  E.g., if module x
                # declares y to be undocumented, then x.y.z should
                # also be undocumented.
                for elt in docindex.root[:]:
                    for m in removed:
                        if m.canonical_name.dominates(elt.canonical_name):
                            docindex.root.remove(elt)

def process_group_field(api_doc, docindex, tag, arg, descr):
    """Define a group named C{arg} containing the variables whose
    names are listed in C{descr}."""
    _check(api_doc, tag, arg, context=NamespaceDoc, expect_arg=True)
    api_doc.group_specs.append( (arg, _descr_to_identifiers(descr)) )
    # [xx] should this also set sort order?

def process_deffield_field(api_doc, docindex, tag, arg, descr):
    """Define a new custom field."""
    _check(api_doc, tag, arg, expect_arg=True)
    if api_doc.extra_docstring_fields is UNKNOWN:
        api_doc.extra_docstring_fields = []
    try:
        docstring_field = _descr_to_docstring_field(arg, descr)
        docstring_field.varnames.append("__%s__" % arg)
        api_doc.extra_docstring_fields.append(docstring_field)
    except ValueError, e:
        raise ValueError('Bad %s: %s' % (tag, e))

def process_raise_field(api_doc, docindex, tag, arg, descr):
    """Record the fact that C{api_doc} can raise the exception named
    C{tag} in C{api_doc.exception_descrs}."""
    _check(api_doc, tag, arg, context=RoutineDoc, expect_arg='single')
    try: name = DottedName(arg, strict=True)
    except DottedName.InvalidDottedName: name = arg
    api_doc.exception_descrs.append( (name, descr) )

def process_sort_field(api_doc, docindex, tag, arg, descr):
    _check(api_doc, tag, arg, context=NamespaceDoc, expect_arg=False)
    api_doc.sort_spec = _descr_to_identifiers(descr) + api_doc.sort_spec

# [xx] should I notice when they give a type for an unknown var?
def process_type_field(api_doc, docindex, tag, arg, descr):
    # In namespace, "@type var: ..." describes the type of a var.
    if isinstance(api_doc, NamespaceDoc):
        _check(api_doc, tag, arg, expect_arg='single')
        set_var_type(api_doc, arg, descr)

    # For variables & properties, "@type: ..." describes the variable.
    elif isinstance(api_doc, (VariableDoc, PropertyDoc)):
        _check(api_doc, tag, arg, expect_arg=False)
        if api_doc.type_descr is not None:
            raise ValueError(REDEFINED % tag)
        api_doc.type_descr = descr

    # For routines, "@type param: ..." describes a parameter.
    elif isinstance(api_doc, RoutineDoc):
        _check(api_doc, tag, arg, expect_arg='single')
        if arg in api_doc.arg_types:
            raise ValueError(REDEFINED % ('type for '+arg))
        api_doc.arg_types[arg] = descr

    else:
        raise ValueError(BAD_CONTEXT % tag)
        
def process_var_field(api_doc, docindex, tag, arg, descr):
    _check(api_doc, tag, arg, context=ModuleDoc, expect_arg=True)
    for ident in re.split('[:;, ] *', arg):
        set_var_descr(api_doc, ident, descr)
        
def process_cvar_field(api_doc, docindex, tag, arg, descr):
    # If @cvar is used *within* a variable, then use it as the
    # variable's description, and treat the variable as a class var.
    if (isinstance(api_doc, VariableDoc) and
        isinstance(api_doc.container, ClassDoc)):
        _check(api_doc, tag, arg, expect_arg=False)
        api_doc.is_instvar = False
        api_doc.descr = markup.ConcatenatedDocstring(api_doc.descr, descr)
        api_doc.summary, api_doc.other_docs = descr.summary()

    # Otherwise, @cvar should be used in a class.
    else:
        _check(api_doc, tag, arg, context=ClassDoc, expect_arg=True)
        for ident in re.split('[:;, ] *', arg):
            set_var_descr(api_doc, ident, descr)
            api_doc.variables[ident].is_instvar = False
        
def process_ivar_field(api_doc, docindex, tag, arg, descr):
    # If @ivar is used *within* a variable, then use it as the
    # variable's description, and treat the variable as an instvar.
    if (isinstance(api_doc, VariableDoc) and
        isinstance(api_doc.container, ClassDoc)):
        _check(api_doc, tag, arg, expect_arg=False)
        # require that there be no other descr?
        api_doc.is_instvar = True
        api_doc.descr = markup.ConcatenatedDocstring(api_doc.descr, descr)
        api_doc.summary, api_doc.other_docs = descr.summary()

    # Otherwise, @ivar should be used in a class.
    else:
        _check(api_doc, tag, arg, context=ClassDoc, expect_arg=True)
        for ident in re.split('[:;, ] *', arg):
            set_var_descr(api_doc, ident, descr)
            api_doc.variables[ident].is_instvar = True

# [xx] '@return: foo' used to get used as a descr if no other
# descr was present.  is that still true?
def process_return_field(api_doc, docindex, tag, arg, descr):
    _check(api_doc, tag, arg, context=RoutineDoc, expect_arg=False)
    if api_doc.return_descr is not None:
        raise ValueError(REDEFINED % 'return value description')
    api_doc.return_descr = descr

def process_rtype_field(api_doc, docindex, tag, arg, descr):
    _check(api_doc, tag, arg,
           context=(RoutineDoc, PropertyDoc), expect_arg=False)
    if isinstance(api_doc, RoutineDoc):
        if api_doc.return_type is not None:
            raise ValueError(REDEFINED % 'return value type')
        api_doc.return_type = descr

    elif isinstance(api_doc, PropertyDoc):
        _check(api_doc, tag, arg, expect_arg=False)
        if api_doc.type_descr is not None:
            raise ValueError(REDEFINED % tag)
        api_doc.type_descr = descr

def process_arg_field(api_doc, docindex, tag, arg, descr):
    _check(api_doc, tag, arg, context=RoutineDoc, expect_arg=True)
    idents = re.split('[:;, ] *', arg)
    api_doc.arg_descrs.append( (idents, descr) )
    # Check to make sure that the documented parameter(s) are
    # actually part of the function signature.
    all_args = api_doc.all_args()
    if all_args not in (['...'], UNKNOWN):
        bad_params = ['"%s"' % i for i in idents if i not in all_args]
        if bad_params:
            raise ValueError(BAD_PARAM % (tag, ', '.join(bad_params)))

def process_kwarg_field(api_doc, docindex, tag, arg, descr):
    # [xx] these should -not- be checked if they exist..
    # and listed separately or not??
    _check(api_doc, tag, arg, context=RoutineDoc, expect_arg=True)
    idents = re.split('[:;, ] *', arg)
    api_doc.arg_descrs.append( (idents, descr) )

register_field_handler(process_group_field, 'group')
register_field_handler(process_deffield_field, 'deffield', 'newfield')
register_field_handler(process_sort_field, 'sort')
register_field_handler(process_summary_field, 'summary')
register_field_handler(process_undocumented_field, 'undocumented')
register_field_handler(process_include_field, 'include')
register_field_handler(process_var_field, 'var', 'variable')
register_field_handler(process_type_field, 'type')
register_field_handler(process_cvar_field, 'cvar', 'cvariable')
register_field_handler(process_ivar_field, 'ivar', 'ivariable')
register_field_handler(process_return_field, 'return', 'returns')
register_field_handler(process_rtype_field, 'rtype', 'returntype')
register_field_handler(process_arg_field, 'arg', 'argument',
                                          'parameter', 'param')
register_field_handler(process_kwarg_field, 'kwarg', 'keyword', 'kwparam')
register_field_handler(process_raise_field, 'raise', 'raises',
                                            'except', 'exception')

# Tags related to function parameters
PARAMETER_TAGS = ('arg', 'argument', 'parameter', 'param',
                  'kwarg', 'keyword', 'kwparam')

# Tags related to variables in a class
VARIABLE_TAGS = ('cvar', 'cvariable', 'ivar', 'ivariable')

# Tags related to exceptions
EXCEPTION_TAGS = ('raise', 'raises', 'except', 'exception')

######################################################################
#{ Helper Functions
######################################################################

def check_type_fields(api_doc, field_warnings):
    """Check to make sure that all type fields correspond to some
    documented parameter; if not, append a warning to field_warnings."""
    if isinstance(api_doc, RoutineDoc):
        for arg in api_doc.arg_types:
            if arg not in api_doc.all_args():
                for args, descr in api_doc.arg_descrs:
                    if arg in args:
                        break
                else:
                    field_warnings.append(BAD_PARAM % ('type', '"%s"' % arg))

def set_var_descr(api_doc, ident, descr):
    if ident not in api_doc.variables:
        api_doc.variables[ident] = VariableDoc(
            container=api_doc, name=ident,
            canonical_name=api_doc.canonical_name+ident)
                                      
    var_doc = api_doc.variables[ident]
    if var_doc.descr not in (None, UNKNOWN):
        raise ValueError(REDEFINED % ('description for '+ident))
    var_doc.descr = descr
    if var_doc.summary in (None, UNKNOWN):
        var_doc.summary, var_doc.other_docs = var_doc.descr.summary()

def set_var_type(api_doc, ident, descr):
    if ident not in api_doc.variables:
        api_doc.variables[ident] = VariableDoc(
            container=api_doc, name=ident,
            canonical_name=api_doc.canonical_name+ident)
        
    var_doc = api_doc.variables[ident]
    if var_doc.type_descr not in (None, UNKNOWN):
        raise ValueError(REDEFINED % ('type for '+ident))
    var_doc.type_descr = descr
        
def _check(api_doc, tag, arg, context=None, expect_arg=None):
    if context is not None:
        if not isinstance(api_doc, context):
            raise ValueError(BAD_CONTEXT % tag)
    if expect_arg is not None:
        if expect_arg == True:
            if arg is None:
                raise ValueError(EXPECTED_ARG % tag)
        elif expect_arg == False:
            if arg is not None:
                raise ValueError(UNEXPECTED_ARG % tag)
        elif expect_arg == 'single':
            if (arg is None or ' ' in arg):
                raise ValueError(EXPECTED_SINGLE_ARG % tag)
        else:
            assert 0, 'bad value for expect_arg'

def get_docformat(api_doc, docindex):
    """
    Return the name of the markup language that should be used to
    parse the API documentation for the given object.
    """
    # Find the module that defines api_doc.
    module = api_doc.defining_module
    # Look up its docformat.
    if module is not UNKNOWN and module.docformat not in (None, UNKNOWN):
        docformat = module.docformat
    else:
        docformat = DEFAULT_DOCFORMAT
    # Convert to lower case & strip region codes.
    try: return docformat.lower().split()[0]
    except: return DEFAULT_DOCFORMAT

def unindent_docstring(docstring):
    # [xx] copied from inspect.getdoc(); we can't use inspect.getdoc()
    # itself, since it expects an object, not a string.
    
    if not docstring: return ''
    lines = docstring.expandtabs().split('\n')

    # Find minimum indentation of any non-blank lines after first line.
    margin = sys.maxint
    for line in lines[1:]:
        content = len(line.lstrip())
        if content:
            indent = len(line) - content
            margin = min(margin, indent)
    # Remove indentation.
    if lines:
        lines[0] = lines[0].lstrip()
    if margin < sys.maxint:
        for i in range(1, len(lines)): lines[i] = lines[i][margin:]
    # Remove any trailing (but not leading!) blank lines.
    while lines and not lines[-1]:
        lines.pop()
    #while lines and not lines[0]:
    #    lines.pop(0)
    return '\n'.join(lines)
                           
_IDENTIFIER_LIST_REGEXP = re.compile(r'^[\w.\*]+([\s,:;]\s*[\w.\*]+)*$')
def _descr_to_identifiers(descr):
    """
    Given a C{ParsedDocstring} that contains a list of identifiers,
    return a list of those identifiers.  This is used by fields such
    as C{@group} and C{@sort}, which expect lists of identifiers as
    their values.  To extract the identifiers, the docstring is first
    converted to plaintext, and then split.  The plaintext content of
    the docstring must be a a list of identifiers, separated by
    spaces, commas, colons, or semicolons.
    
    @rtype: C{list} of C{string}
    @return: A list of the identifier names contained in C{descr}.
    @type descr: L{markup.ParsedDocstring}
    @param descr: A C{ParsedDocstring} containing a list of
        identifiers.
    @raise ValueError: If C{descr} does not contain a valid list of
        identifiers.
    """
    idents = descr.to_plaintext(None).strip()
    idents = re.sub(r'\s+', ' ', idents)
    if not _IDENTIFIER_LIST_REGEXP.match(idents):
        raise ValueError, 'Bad Identifier list: %r' % idents
    rval = re.split('[:;, ] *', idents)
    return rval
    
def _descr_to_docstring_field(arg, descr):
    tags = [s.lower() for s in re.split('[:;, ] *', arg)]
    descr = descr.to_plaintext(None).strip()
    args = re.split('[:;,] *', descr)
    if len(args) == 0 or len(args) > 3:
        raise ValueError, 'Wrong number of arguments'
    singular = args[0]
    if len(args) >= 2: plural = args[1]
    else: plural = None
    short = 0
    if len(args) >= 3:
        if args[2] == 'short': short = 1
        else: raise ValueError('Bad arg 2 (expected "short")')
    return DocstringField(tags, singular, plural, short)

######################################################################
#{ Function Signature Extraction
######################################################################

# [XX] todo: add optional type modifiers?
_SIGNATURE_RE = re.compile(
    # Class name (for builtin methods)
    r'^\s*((?P<self>\w+)\.)?' +
    # The function name (must match exactly) [XX] not anymore!
    r'(?P<func>\w+)' +
    # The parameters
    r'\((?P<params>(\s*\[?\s*\*{0,2}[\w\-\.]+(\s*=.+?)?'+
    r'(\s*\[?\s*,\s*\]?\s*\*{0,2}[\w\-\.]+(\s*=.+?)?)*\]*)?)\s*\)' +
    # The return value (optional)
    r'(\s*(->)\s*(?P<return>\S.*?))?'+
    # The end marker
    r'\s*(\n|\s+(--|<=+>)\s+|$|\.\s+|\.\n)')
"""A regular expression that is used to extract signatures from
docstrings."""
    
def parse_function_signature(func_doc, doc_source, docformat, parse_errors):
    """
    Construct the signature for a builtin function or method from
    its docstring.  If the docstring uses the standard convention
    of including a signature in the first line of the docstring
    (and formats that signature according to standard
    conventions), then it will be used to extract a signature.
    Otherwise, the signature will be set to a single varargs
    variable named C{"..."}.

    @param func_doc: The target object where to store parsed signature. Also
        container of the docstring to parse if doc_source is C{None}
    @type func_doc: L{RoutineDoc}
    @param doc_source: Contains the docstring to parse. If C{None}, parse
        L{func_doc} docstring instead
    @type doc_source: L{APIDoc}
    @rtype: C{None}
    """
    if doc_source is None:
        doc_source = func_doc

    # If there's no docstring, then don't do anything.
    if not doc_source.docstring: return False

    m = _SIGNATURE_RE.match(doc_source.docstring)
    if m is None: return False

    # Do I want to be this strict?
    # Notice that __init__ must match the class name instead, if the signature
    # comes from the class docstring
#     if not (m.group('func') == func_doc.canonical_name[-1] or
#             '_'+m.group('func') == func_doc.canonical_name[-1]):
#         log.warning("Not extracting function signature from %s's "
#                     "docstring, since the name doesn't match." %
#                     func_doc.canonical_name)
#         return False
    
    params = m.group('params')
    rtype = m.group('return')
    selfparam = m.group('self')
    
    # Extract the parameters from the signature.
    func_doc.posargs = []
    func_doc.vararg = None
    func_doc.kwarg = None
    if func_doc.posarg_defaults is UNKNOWN:
        func_doc.posarg_defaults = []
    if params:
        # Figure out which parameters are optional.
        while '[' in params or ']' in params:
            m2 = re.match(r'(.*)\[([^\[\]]+)\](.*)', params)
            if not m2: return False
            (start, mid, end) = m2.groups()
            mid = re.sub(r'((,|^)\s*[\w\-\.]+)', r'\1=...', mid)
            params = start+mid+end

        params = re.sub(r'=...=' , r'=', params)
        for name in params.split(','):
            if '=' in name:
                (name, default_repr) = name.split('=',1)
                default = GenericValueDoc(parse_repr=default_repr)
            else:
                default = None
            name = name.strip()
            if name == '...':
                func_doc.vararg = '...'
            elif name.startswith('**'):
                func_doc.kwarg = name[2:]
            elif name.startswith('*'):
                func_doc.vararg = name[1:]
            else:
                func_doc.posargs.append(name)
                if len(func_doc.posarg_defaults) < len(func_doc.posargs):
                    func_doc.posarg_defaults.append(default)
                elif default is not None:
                    argnum = len(func_doc.posargs)-1
                    func_doc.posarg_defaults[argnum] = default

    # Extract the return type/value from the signature
    if rtype:
        func_doc.return_type = markup.parse(rtype, docformat, parse_errors,
                                            inline=True)

    # Add the self parameter, if it was specified.
    if selfparam:
        func_doc.posargs.insert(0, selfparam)
        func_doc.posarg_defaults.insert(0, None)

    # Remove the signature from the docstring.
    doc_source.docstring = doc_source.docstring[m.end():]
        
    # We found a signature.
    return True

