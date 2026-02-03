#
# epytext.py: epydoc formatted docstring parsing
# Edward Loper
#
# Created [04/10/01 12:00 AM]
# $Id: epytext.py 1652 2007-09-26 04:45:34Z edloper $
#

"""
Parser for epytext strings.  Epytext is a lightweight markup whose
primary intended application is Python documentation strings.  This
parser converts Epytext strings to a simple DOM-like representation
(encoded as a tree of L{Element} objects and strings).  Epytext
strings can contain the following X{structural blocks}:

    - X{epytext}: The top-level element of the DOM tree.
    - X{para}: A paragraph of text.  Paragraphs contain no newlines, 
      and all spaces are soft.
    - X{section}: A section or subsection.
    - X{field}: A tagged field.  These fields provide information
      about specific aspects of a Python object, such as the
      description of a function's parameter, or the author of a
      module.
    - X{literalblock}: A block of literal text.  This text should be
      displayed as it would be displayed in plaintext.  The
      parser removes the appropriate amount of leading whitespace 
      from each line in the literal block.
    - X{doctestblock}: A block containing sample python code,
      formatted according to the specifications of the C{doctest}
      module.
    - X{ulist}: An unordered list.
    - X{olist}: An ordered list.
    - X{li}: A list item.  This tag is used both for unordered list
      items and for ordered list items.

Additionally, the following X{inline regions} may be used within
C{para} blocks:
    
    - X{code}:   Source code and identifiers.
    - X{math}:   Mathematical expressions.
    - X{index}:  A term which should be included in an index, if one
                 is generated.
    - X{italic}: Italicized text.
    - X{bold}:   Bold-faced text.
    - X{uri}:    A Universal Resource Indicator (URI) or Universal
                 Resource Locator (URL)
    - X{link}:   A Python identifier which should be hyperlinked to
                 the named object's documentation, when possible.

The returned DOM tree will conform to the the following Document Type
Description::

   <!ENTITY % colorized '(code | math | index | italic |
                          bold | uri | link | symbol)*'>

   <!ELEMENT epytext ((para | literalblock | doctestblock |
                      section | ulist | olist)*, fieldlist?)>

   <!ELEMENT para (#PCDATA | %colorized;)*>

   <!ELEMENT section (para | listblock | doctestblock |
                      section | ulist | olist)+>

   <!ELEMENT fieldlist (field+)>
   <!ELEMENT field (tag, arg?, (para | listblock | doctestblock)
                                ulist | olist)+)>
   <!ELEMENT tag (#PCDATA)>
   <!ELEMENT arg (#PCDATA)>
   
   <!ELEMENT literalblock (#PCDATA | %colorized;)*>
   <!ELEMENT doctestblock (#PCDATA)>

   <!ELEMENT ulist (li+)>
   <!ELEMENT olist (li+)>
   <!ELEMENT li (para | literalblock | doctestblock | ulist | olist)+>
   <!ATTLIST li bullet NMTOKEN #IMPLIED>
   <!ATTLIST olist start NMTOKEN #IMPLIED>

   <!ELEMENT uri     (name, target)>
   <!ELEMENT link    (name, target)>
   <!ELEMENT name    (#PCDATA | %colorized;)*>
   <!ELEMENT target  (#PCDATA)>
   
   <!ELEMENT code    (#PCDATA | %colorized;)*>
   <!ELEMENT math    (#PCDATA | %colorized;)*>
   <!ELEMENT italic  (#PCDATA | %colorized;)*>
   <!ELEMENT bold    (#PCDATA | %colorized;)*>
   <!ELEMENT indexed (#PCDATA | %colorized;)>
   <!ATTLIST code style CDATA #IMPLIED>

   <!ELEMENT symbol (#PCDATA)>

@var SYMBOLS: A list of the of escape symbols that are supported
      by epydoc.  Currently the following symbols are supported:
<<<SYMBOLS>>>
"""
# Note: the symbol list is appended to the docstring automatically,
# below.

__docformat__ = 'epytext en'

# Code organization..
#   1. parse()
#   2. tokenize()
#   3. colorize()
#   4. helpers
#   5. testing

import re, string, types, sys, os.path
from epydoc.markup import *
from epydoc.util import wordwrap, plaintext_to_html, plaintext_to_latex
from epydoc.markup.doctest import doctest_to_html, doctest_to_latex

##################################################
## DOM-Like Encoding
##################################################

class Element:
    """
    A very simple DOM-like representation for parsed epytext
    documents.  Each epytext document is encoded as a tree whose nodes
    are L{Element} objects, and whose leaves are C{string}s.  Each
    node is marked by a I{tag} and zero or more I{attributes}.  Each
    attribute is a mapping from a string key to a string value.
    """
    def __init__(self, tag, *children, **attribs):
        self.tag = tag
        """A string tag indicating the type of this element.
        @type: C{string}"""
        
        self.children = list(children)
        """A list of the children of this element.
        @type: C{list} of (C{string} or C{Element})"""
        
        self.attribs = attribs
        """A dictionary mapping attribute names to attribute values
        for this element.
        @type: C{dict} from C{string} to C{string}"""

    def __str__(self):
        """
        Return a string representation of this element, using XML
        notation.
        @bug: Doesn't escape '<' or '&' or '>'.
        """
        attribs = ''.join([' %s=%r' % t for t in self.attribs.items()])
        return ('<%s%s>' % (self.tag, attribs) +
                ''.join([str(child) for child in self.children]) +
                '</%s>' % self.tag)

    def __repr__(self):
        attribs = ''.join([', %s=%r' % t for t in self.attribs.items()])
        args = ''.join([', %r' % c for c in self.children])
        return 'Element(%s%s%s)' % (self.tag, args, attribs)

##################################################
## Constants
##################################################

# The possible heading underline characters, listed in order of
# heading depth. 
_HEADING_CHARS = "=-~"

# Escape codes.  These should be needed very rarely.
_ESCAPES = {'lb':'{', 'rb': '}'}

# Symbols.  These can be generated via S{...} escapes.
SYMBOLS = [
    # Arrows
    '<-', '->', '^', 'v', 

    # Greek letters
    'alpha', 'beta', 'gamma', 'delta', 'epsilon', 'zeta',  
    'eta', 'theta', 'iota', 'kappa', 'lambda', 'mu',  
    'nu', 'xi', 'omicron', 'pi', 'rho', 'sigma',  
    'tau', 'upsilon', 'phi', 'chi', 'psi', 'omega',
    'Alpha', 'Beta', 'Gamma', 'Delta', 'Epsilon', 'Zeta',  
    'Eta', 'Theta', 'Iota', 'Kappa', 'Lambda', 'Mu',  
    'Nu', 'Xi', 'Omicron', 'Pi', 'Rho', 'Sigma',  
    'Tau', 'Upsilon', 'Phi', 'Chi', 'Psi', 'Omega',
    
    # HTML character entities
    'larr', 'rarr', 'uarr', 'darr', 'harr', 'crarr',
    'lArr', 'rArr', 'uArr', 'dArr', 'hArr', 
    'copy', 'times', 'forall', 'exist', 'part',
    'empty', 'isin', 'notin', 'ni', 'prod', 'sum',
    'prop', 'infin', 'ang', 'and', 'or', 'cap', 'cup',
    'int', 'there4', 'sim', 'cong', 'asymp', 'ne',
    'equiv', 'le', 'ge', 'sub', 'sup', 'nsub',
    'sube', 'supe', 'oplus', 'otimes', 'perp',

    # Alternate (long) names
    'infinity', 'integral', 'product',
    '>=', '<=', 
    ]
# Convert to a dictionary, for quick lookup
_SYMBOLS = {}
for symbol in SYMBOLS: _SYMBOLS[symbol] = 1

# Add symbols to the docstring.
symblist = '      '
symblist += ';\n      '.join([' - C{E{S}{%s}}=S{%s}' % (symbol, symbol)
                              for symbol in SYMBOLS])
__doc__ = __doc__.replace('<<<SYMBOLS>>>', symblist)
del symbol, symblist

# Tags for colorizing text.
_COLORIZING_TAGS = {
    'C': 'code',
    'M': 'math',
    'X': 'indexed',
    'I': 'italic', 
    'B': 'bold',
    'U': 'uri',
    'L': 'link',       # A Python identifier that should be linked to 
    'E': 'escape',     # escapes characters or creates symbols
    'S': 'symbol',
    'G': 'graph',
    }

# Which tags can use "link syntax" (e.g., U{Python<www.python.org>})?
_LINK_COLORIZING_TAGS = ['link', 'uri']

##################################################
## Structuring (Top Level)
##################################################

def parse(str, errors = None):
    """
    Return a DOM tree encoding the contents of an epytext string.  Any
    errors generated during parsing will be stored in C{errors}.

    @param str: The epytext string to parse.
    @type str: C{string}
    @param errors: A list where any errors generated during parsing
        will be stored.  If no list is specified, then fatal errors
        will generate exceptions, and non-fatal errors will be
        ignored.
    @type errors: C{list} of L{ParseError}
    @return: a DOM tree encoding the contents of an epytext string.
    @rtype: C{Element}
    @raise ParseError: If C{errors} is C{None} and an error is
        encountered while parsing.
    """
    # Initialize errors list.
    if errors == None:
        errors = []
        raise_on_error = 1
    else:
        raise_on_error = 0

    # Preprocess the string.
    str = re.sub('\015\012', '\012', str)
    str = string.expandtabs(str)

    # Tokenize the input string.
    tokens = _tokenize(str, errors)

    # Have we encountered a field yet?
    encountered_field = 0

    # Create an document to hold the epytext.
    doc = Element('epytext')

    # Maintain two parallel stacks: one contains DOM elements, and
    # gives the ancestors of the current block.  The other contains
    # indentation values, and gives the indentation of the
    # corresponding DOM elements.  An indentation of "None" reflects
    # an unknown indentation.  However, the indentation must be
    # greater than, or greater than or equal to, the indentation of
    # the prior element (depending on what type of DOM element it
    # corresponds to).  No 2 consecutive indent_stack values will be
    # ever be "None."  Use initial dummy elements in the stack, so we
    # don't have to worry about bounds checking.
    stack = [None, doc]
    indent_stack = [-1, None]

    for token in tokens:
        # Uncomment this for debugging:
        #print ('%s: %s\n%s: %s\n' % 
        #       (''.join(['%-11s' % (t and t.tag) for t in stack]),
        #        token.tag, ''.join(['%-11s' % i for i in indent_stack]),
        #        token.indent))
        
        # Pop any completed blocks off the stack.
        _pop_completed_blocks(token, stack, indent_stack)

        # If Token has type PARA, colorize and add the new paragraph
        if token.tag == Token.PARA:
            _add_para(doc, token, stack, indent_stack, errors)
                     
        # If Token has type HEADING, add the new section
        elif token.tag == Token.HEADING:
            _add_section(doc, token, stack, indent_stack, errors)

        # If Token has type LBLOCK, add the new literal block
        elif token.tag == Token.LBLOCK:
            stack[-1].children.append(token.to_dom(doc))

        # If Token has type DTBLOCK, add the new doctest block
        elif token.tag == Token.DTBLOCK:
            stack[-1].children.append(token.to_dom(doc))

        # If Token has type BULLET, add the new list/list item/field
        elif token.tag == Token.BULLET:
            _add_list(doc, token, stack, indent_stack, errors)
        else:
            assert 0, 'Unknown token type: '+token.tag

        # Check if the DOM element we just added was a field..
        if stack[-1].tag == 'field':
            encountered_field = 1
        elif encountered_field == 1:
            if len(stack) <= 3:
                estr = ("Fields must be the final elements in an "+
                        "epytext string.")
                errors.append(StructuringError(estr, token.startline))

    # Graphs use inline markup (G{...}) but are really block-level
    # elements; so "raise" any graphs we generated.  This is a bit of
    # a hack, but the alternative is to define a new markup for
    # block-level elements, which I'd rather not do.  (See sourceforge
    # bug #1673017.)
    for child in doc.children:
        _raise_graphs(child, doc)

    # If there was an error, then signal it!
    if len([e for e in errors if e.is_fatal()]) > 0:
        if raise_on_error:
            raise errors[0]
        else:
            return None
        
    # Return the top-level epytext DOM element.
    return doc

def _raise_graphs(tree, parent):
    # Recurse to children.
    have_graph_child = False
    for elt in tree.children:
        if isinstance(elt, Element):
            _raise_graphs(elt, tree)
            if elt.tag == 'graph': have_graph_child = True

    block = ('section', 'fieldlist', 'field', 'ulist', 'olist', 'li')
    if have_graph_child and tree.tag not in block:
        child_index = 0
        for elt in tree.children:
            if isinstance(elt, Element) and elt.tag == 'graph':
                # We found a graph: splice it into the parent.
                parent_index = parent.children.index(tree)
                left = tree.children[:child_index]
                right = tree.children[child_index+1:]
                parent.children[parent_index:parent_index+1] = [
                    Element(tree.tag, *left, **tree.attribs),
                    elt,
                    Element(tree.tag, *right, **tree.attribs)]
                child_index = 0
                parent_index += 2
            else:
                child_index += 1

def _pop_completed_blocks(token, stack, indent_stack):
    """
    Pop any completed blocks off the stack.  This includes any
    blocks that we have dedented past, as well as any list item
    blocks that we've dedented to.  The top element on the stack 
    should only be a list if we're about to start a new list
    item (i.e., if the next token is a bullet).
    """
    indent = token.indent
    if indent != None:
        while (len(stack) > 2):
            pop = 0
            
            # Dedent past a block
            if indent_stack[-1]!=None and indent<indent_stack[-1]: pop=1
            elif indent_stack[-1]==None and indent<indent_stack[-2]: pop=1

            # Dedent to a list item, if it is follwed by another list
            # item with the same indentation.
            elif (token.tag == 'bullet' and indent==indent_stack[-2] and 
                  stack[-1].tag in ('li', 'field')): pop=1

            # End of a list (no more list items available)
            elif (stack[-1].tag in ('ulist', 'olist') and
                  (token.tag != 'bullet' or token.contents[-1] == ':')):
                pop=1

            # Pop the block, if it's complete.  Otherwise, we're done.
            if pop == 0: return
            stack.pop()
            indent_stack.pop()

def _add_para(doc, para_token, stack, indent_stack, errors):
    """Colorize the given paragraph, and add it to the DOM tree."""
    # Check indentation, and update the parent's indentation
    # when appropriate.
    if indent_stack[-1] == None:
        indent_stack[-1] = para_token.indent
    if para_token.indent == indent_stack[-1]:
        # Colorize the paragraph and add it.
        para = _colorize(doc, para_token, errors)
        if para_token.inline:
            para.attribs['inline'] = True
        stack[-1].children.append(para)
    else:
        estr = "Improper paragraph indentation."
        errors.append(StructuringError(estr, para_token.startline))

def _add_section(doc, heading_token, stack, indent_stack, errors):
    """Add a new section to the DOM tree, with the given heading."""
    if indent_stack[-1] == None:
        indent_stack[-1] = heading_token.indent
    elif indent_stack[-1] != heading_token.indent:
        estr = "Improper heading indentation."
        errors.append(StructuringError(estr, heading_token.startline))

    # Check for errors.
    for tok in stack[2:]:
        if tok.tag != "section":
            estr = "Headings must occur at the top level."
            errors.append(StructuringError(estr, heading_token.startline))
            break
    if (heading_token.level+2) > len(stack):
        estr = "Wrong underline character for heading."
        errors.append(StructuringError(estr, heading_token.startline))

    # Pop the appropriate number of headings so we're at the
    # correct level.
    stack[heading_token.level+2:] = []
    indent_stack[heading_token.level+2:] = []

    # Colorize the heading
    head = _colorize(doc, heading_token, errors, 'heading')

    # Add the section's and heading's DOM elements.
    sec = Element("section")
    stack[-1].children.append(sec)
    stack.append(sec)
    sec.children.append(head)
    indent_stack.append(None)
        
def _add_list(doc, bullet_token, stack, indent_stack, errors):
    """
    Add a new list item or field to the DOM tree, with the given
    bullet or field tag.  When necessary, create the associated
    list.
    """
    # Determine what type of bullet it is.
    if bullet_token.contents[-1] == '-':
        list_type = 'ulist'
    elif bullet_token.contents[-1] == '.':
        list_type = 'olist'
    elif bullet_token.contents[-1] == ':':
        list_type = 'fieldlist'
    else:
        raise AssertionError('Bad Bullet: %r' % bullet_token.contents)

    # Is this a new list?
    newlist = 0
    if stack[-1].tag != list_type:
        newlist = 1
    elif list_type == 'olist' and stack[-1].tag == 'olist':
        old_listitem = stack[-1].children[-1]
        old_bullet = old_listitem.attribs.get("bullet").split('.')[:-1]
        new_bullet = bullet_token.contents.split('.')[:-1]
        if (new_bullet[:-1] != old_bullet[:-1] or
            int(new_bullet[-1]) != int(old_bullet[-1])+1):
            newlist = 1

    # Create the new list.
    if newlist:
        if stack[-1].tag is 'fieldlist':
            # The new list item is not a field list item (since this
            # is a new list); but it's indented the same as the field
            # list.  This either means that they forgot to indent the
            # list, or they are trying to put something after the
            # field list.  The first one seems more likely, so we'll
            # just warn about that (to avoid confusion).
            estr = "Lists must be indented."
            errors.append(StructuringError(estr, bullet_token.startline))
        if stack[-1].tag in ('ulist', 'olist', 'fieldlist'):
            stack.pop()
            indent_stack.pop()

        if (list_type != 'fieldlist' and indent_stack[-1] is not None and
            bullet_token.indent == indent_stack[-1]):
            # Ignore this error if there's text on the same line as
            # the comment-opening quote -- epydoc can't reliably
            # determine the indentation for that line.
            if bullet_token.startline != 1 or bullet_token.indent != 0:
                estr = "Lists must be indented."
                errors.append(StructuringError(estr, bullet_token.startline))

        if list_type == 'fieldlist':
            # Fieldlist should be at the top-level.
            for tok in stack[2:]:
                if tok.tag != "section":
                    estr = "Fields must be at the top level."
                    errors.append(
                        StructuringError(estr, bullet_token.startline))
                    break
            stack[2:] = []
            indent_stack[2:] = []

        # Add the new list.
        lst = Element(list_type)
        stack[-1].children.append(lst)
        stack.append(lst)
        indent_stack.append(bullet_token.indent)
        if list_type == 'olist':
            start = bullet_token.contents.split('.')[:-1]
            if start != '1':
                lst.attribs["start"] = start[-1]

    # Fields are treated somewhat specially: A "fieldlist"
    # node is created to make the parsing simpler, but fields
    # are adjoined directly into the "epytext" node, not into
    # the "fieldlist" node.
    if list_type == 'fieldlist':
        li = Element("field")
        token_words = bullet_token.contents[1:-1].split(None, 1)
        tag_elt = Element("tag")
        tag_elt.children.append(token_words[0])
        li.children.append(tag_elt)

        if len(token_words) > 1:
            arg_elt = Element("arg")
            arg_elt.children.append(token_words[1])
            li.children.append(arg_elt)
    else:
        li = Element("li")
        if list_type == 'olist':
            li.attribs["bullet"] = bullet_token.contents

    # Add the bullet.
    stack[-1].children.append(li)
    stack.append(li)
    indent_stack.append(None)

##################################################
## Tokenization
##################################################

class Token:
    """
    C{Token}s are an intermediate data structure used while
    constructing the structuring DOM tree for a formatted docstring.
    There are five types of C{Token}:
    
        - Paragraphs
        - Literal blocks
        - Doctest blocks
        - Headings
        - Bullets

    The text contained in each C{Token} is stored in the
    C{contents} variable.  The string in this variable has been
    normalized.  For paragraphs, this means that it has been converted 
    into a single line of text, with newline/indentation replaced by
    single spaces.  For literal blocks and doctest blocks, this means
    that the appropriate amount of leading whitespace has been removed 
    from each line.

    Each C{Token} has an indentation level associated with it,
    stored in the C{indent} variable.  This indentation level is used
    by the structuring procedure to assemble hierarchical blocks.

    @type tag: C{string}
    @ivar tag: This C{Token}'s type.  Possible values are C{Token.PARA} 
        (paragraph), C{Token.LBLOCK} (literal block), C{Token.DTBLOCK}
        (doctest block), C{Token.HEADINGC}, and C{Token.BULLETC}.
        
    @type startline: C{int}
    @ivar startline: The line on which this C{Token} begins.  This 
        line number is only used for issuing errors.

    @type contents: C{string}
    @ivar contents: The normalized text contained in this C{Token}.
    
    @type indent: C{int} or C{None}
    @ivar indent: The indentation level of this C{Token} (in
        number of leading spaces).  A value of C{None} indicates an
        unknown indentation; this is used for list items and fields
        that begin with one-line paragraphs.
        
    @type level: C{int} or C{None}
    @ivar level: The heading-level of this C{Token} if it is a
        heading; C{None}, otherwise.  Valid heading levels are 0, 1,
        and 2.

    @type inline: C{bool}
    @ivar inline: If True, the element is an inline level element, comparable
        to an HTML C{<span>} tag. Else, it is a block level element, comparable
        to an HTML C{<div>}.

    @type PARA: C{string}
    @cvar PARA: The C{tag} value for paragraph C{Token}s.
    @type LBLOCK: C{string}
    @cvar LBLOCK: The C{tag} value for literal C{Token}s.
    @type DTBLOCK: C{string}
    @cvar DTBLOCK: The C{tag} value for doctest C{Token}s.
    @type HEADING: C{string}
    @cvar HEADING: The C{tag} value for heading C{Token}s.
    @type BULLET: C{string}
    @cvar BULLET: The C{tag} value for bullet C{Token}s.  This C{tag}
        value is also used for field tag C{Token}s, since fields
        function syntactically the same as list items.
    """
    # The possible token types.
    PARA = "para"
    LBLOCK = "literalblock"
    DTBLOCK = "doctestblock"
    HEADING = "heading"
    BULLET = "bullet"

    def __init__(self, tag, startline, contents, indent, level=None,
                 inline=False):
        """
        Create a new C{Token}.

        @param tag: The type of the new C{Token}.
        @type tag: C{string}
        @param startline: The line on which the new C{Token} begins.
        @type startline: C{int}
        @param contents: The normalized contents of the new C{Token}.
        @type contents: C{string}
        @param indent: The indentation of the new C{Token} (in number
            of leading spaces).  A value of C{None} indicates an
            unknown indentation.
        @type indent: C{int} or C{None}
        @param level: The heading-level of this C{Token} if it is a
            heading; C{None}, otherwise.
        @type level: C{int} or C{None}
        @param inline: Is this C{Token} inline as a C{<span>}?.
        @type inline: C{bool}
        """
        self.tag = tag
        self.startline = startline
        self.contents = contents
        self.indent = indent
        self.level = level
        self.inline = inline

    def __repr__(self):
        """
        @rtype: C{string}
        @return: the formal representation of this C{Token}.
            C{Token}s have formal representaitons of the form:: 
                <Token: para at line 12>
        """
        return '<Token: %s at line %s>' % (self.tag, self.startline)

    def to_dom(self, doc):
        """
        @return: a DOM representation of this C{Token}.
        @rtype: L{Element}
        """
        e = Element(self.tag)
        e.children.append(self.contents)
        return e

# Construct regular expressions for recognizing bullets.  These are
# global so they don't have to be reconstructed each time we tokenize
# a docstring.
_ULIST_BULLET = '[-]( +|$)'
_OLIST_BULLET = '(\d+[.])+( +|$)'
_FIELD_BULLET = '@\w+( [^{}:\n]+)?:'
_BULLET_RE = re.compile(_ULIST_BULLET + '|' +
                        _OLIST_BULLET + '|' +
                        _FIELD_BULLET)
_LIST_BULLET_RE = re.compile(_ULIST_BULLET + '|' + _OLIST_BULLET)
_FIELD_BULLET_RE = re.compile(_FIELD_BULLET)
del _ULIST_BULLET, _OLIST_BULLET, _FIELD_BULLET

def _tokenize_doctest(lines, start, block_indent, tokens, errors):
    """
    Construct a L{Token} containing the doctest block starting at
    C{lines[start]}, and append it to C{tokens}.  C{block_indent}
    should be the indentation of the doctest block.  Any errors
    generated while tokenizing the doctest block will be appended to
    C{errors}.

    @param lines: The list of lines to be tokenized
    @param start: The index into C{lines} of the first line of the
        doctest block to be tokenized.
    @param block_indent: The indentation of C{lines[start]}.  This is
        the indentation of the doctest block.
    @param errors: A list where any errors generated during parsing
        will be stored.  If no list is specified, then errors will 
        generate exceptions.
    @return: The line number of the first line following the doctest
        block.
        
    @type lines: C{list} of C{string}
    @type start: C{int}
    @type block_indent: C{int}
    @type tokens: C{list} of L{Token}
    @type errors: C{list} of L{ParseError}
    @rtype: C{int}
    """
    # If they dedent past block_indent, keep track of the minimum
    # indentation.  This is used when removing leading indentation
    # from the lines of the doctest block.
    min_indent = block_indent

    linenum = start + 1
    while linenum < len(lines):
        # Find the indentation of this line.
        line = lines[linenum]
        indent = len(line) - len(line.lstrip())
        
        # A blank line ends doctest block.
        if indent == len(line): break
        
        # A Dedent past block_indent is an error.
        if indent < block_indent:
            min_indent = min(min_indent, indent)
            estr = 'Improper doctest block indentation.'
            errors.append(TokenizationError(estr, linenum))

        # Go on to the next line.
        linenum += 1

    # Add the token, and return the linenum after the token ends.
    contents = [line[min_indent:] for line in lines[start:linenum]]
    contents = '\n'.join(contents)
    tokens.append(Token(Token.DTBLOCK, start, contents, block_indent))
    return linenum

def _tokenize_literal(lines, start, block_indent, tokens, errors):
    """
    Construct a L{Token} containing the literal block starting at
    C{lines[start]}, and append it to C{tokens}.  C{block_indent}
    should be the indentation of the literal block.  Any errors
    generated while tokenizing the literal block will be appended to
    C{errors}.

    @param lines: The list of lines to be tokenized
    @param start: The index into C{lines} of the first line of the
        literal block to be tokenized.
    @param block_indent: The indentation of C{lines[start]}.  This is
        the indentation of the literal block.
    @param errors: A list of the errors generated by parsing.  Any
        new errors generated while will tokenizing this paragraph
        will be appended to this list.
    @return: The line number of the first line following the literal
        block. 
        
    @type lines: C{list} of C{string}
    @type start: C{int}
    @type block_indent: C{int}
    @type tokens: C{list} of L{Token}
    @type errors: C{list} of L{ParseError}
    @rtype: C{int}
    """
    linenum = start + 1
    while linenum < len(lines):
        # Find the indentation of this line.
        line = lines[linenum]
        indent = len(line) - len(line.lstrip())

        # A Dedent to block_indent ends the literal block.
        # (Ignore blank likes, though)
        if len(line) != indent and indent <= block_indent:
            break
        
        # Go on to the next line.
        linenum += 1

    # Add the token, and return the linenum after the token ends.
    contents = [line[block_indent+1:] for line in lines[start:linenum]]
    contents = '\n'.join(contents)
    contents = re.sub('(\A[ \n]*\n)|(\n[ \n]*\Z)', '', contents)
    tokens.append(Token(Token.LBLOCK, start, contents, block_indent))
    return linenum

def _tokenize_listart(lines, start, bullet_indent, tokens, errors):
    """
    Construct L{Token}s for the bullet and the first paragraph of the
    list item (or field) starting at C{lines[start]}, and append them
    to C{tokens}.  C{bullet_indent} should be the indentation of the
    list item.  Any errors generated while tokenizing will be
    appended to C{errors}.

    @param lines: The list of lines to be tokenized
    @param start: The index into C{lines} of the first line of the
        list item to be tokenized.
    @param bullet_indent: The indentation of C{lines[start]}.  This is
        the indentation of the list item.
    @param errors: A list of the errors generated by parsing.  Any
        new errors generated while will tokenizing this paragraph
        will be appended to this list.
    @return: The line number of the first line following the list
        item's first paragraph.
        
    @type lines: C{list} of C{string}
    @type start: C{int}
    @type bullet_indent: C{int}
    @type tokens: C{list} of L{Token}
    @type errors: C{list} of L{ParseError}
    @rtype: C{int}
    """
    linenum = start + 1
    para_indent = None
    doublecolon = lines[start].rstrip()[-2:] == '::'

    # Get the contents of the bullet.
    para_start = _BULLET_RE.match(lines[start], bullet_indent).end()
    bcontents = lines[start][bullet_indent:para_start].strip()
    
    while linenum < len(lines):
        # Find the indentation of this line.
        line = lines[linenum]
        indent = len(line) - len(line.lstrip())

        # "::" markers end paragraphs.
        if doublecolon: break
        if line.rstrip()[-2:] == '::': doublecolon = 1

        # A blank line ends the token
        if indent == len(line): break

        # Dedenting past bullet_indent ends the list item.
        if indent < bullet_indent: break
        
        # A line beginning with a bullet ends the token.
        if _BULLET_RE.match(line, indent): break
        
        # If this is the second line, set the paragraph indentation, or 
        # end the token, as appropriate.
        if para_indent == None: para_indent = indent

        # A change in indentation ends the token
        if indent != para_indent: break

        # Go on to the next line.
        linenum += 1

    # Add the bullet token.
    tokens.append(Token(Token.BULLET, start, bcontents, bullet_indent,
                        inline=True))

    # Add the paragraph token.
    pcontents = ([lines[start][para_start:].strip()] + 
                 [line.strip() for line in lines[start+1:linenum]])
    pcontents = ' '.join(pcontents).strip()
    if pcontents:
        tokens.append(Token(Token.PARA, start, pcontents, para_indent,
                            inline=True))

    # Return the linenum after the paragraph token ends.
    return linenum

def _tokenize_para(lines, start, para_indent, tokens, errors):
    """
    Construct a L{Token} containing the paragraph starting at
    C{lines[start]}, and append it to C{tokens}.  C{para_indent}
    should be the indentation of the paragraph .  Any errors
    generated while tokenizing the paragraph will be appended to
    C{errors}.

    @param lines: The list of lines to be tokenized
    @param start: The index into C{lines} of the first line of the
        paragraph to be tokenized.
    @param para_indent: The indentation of C{lines[start]}.  This is
        the indentation of the paragraph.
    @param errors: A list of the errors generated by parsing.  Any
        new errors generated while will tokenizing this paragraph
        will be appended to this list.
    @return: The line number of the first line following the
        paragraph. 
        
    @type lines: C{list} of C{string}
    @type start: C{int}
    @type para_indent: C{int}
    @type tokens: C{list} of L{Token}
    @type errors: C{list} of L{ParseError}
    @rtype: C{int}
    """
    linenum = start + 1
    doublecolon = 0
    while linenum < len(lines):
        # Find the indentation of this line.
        line = lines[linenum]
        indent = len(line) - len(line.lstrip())

        # "::" markers end paragraphs.
        if doublecolon: break
        if line.rstrip()[-2:] == '::': doublecolon = 1

        # Blank lines end paragraphs
        if indent == len(line): break

        # Indentation changes end paragraphs
        if indent != para_indent: break

        # List bullets end paragraphs
        if _BULLET_RE.match(line, indent): break

        # Check for mal-formatted field items.
        if line[indent] == '@':
            estr = "Possible mal-formatted field item."
            errors.append(TokenizationError(estr, linenum, is_fatal=0))
            
        # Go on to the next line.
        linenum += 1

    contents = [line.strip() for line in lines[start:linenum]]
    
    # Does this token look like a heading?
    if ((len(contents) < 2) or
        (contents[1][0] not in _HEADING_CHARS) or
        (abs(len(contents[0])-len(contents[1])) > 5)):
        looks_like_heading = 0
    else:
        looks_like_heading = 1
        for char in contents[1]:
            if char != contents[1][0]:
                looks_like_heading = 0
                break

    if looks_like_heading:
        if len(contents[0]) != len(contents[1]):
            estr = ("Possible heading typo: the number of "+
                    "underline characters must match the "+
                    "number of heading characters.")
            errors.append(TokenizationError(estr, start, is_fatal=0))
        else:
            level = _HEADING_CHARS.index(contents[1][0])
            tokens.append(Token(Token.HEADING, start,
                                contents[0], para_indent, level))
            return start+2
                 
    # Add the paragraph token, and return the linenum after it ends.
    contents = ' '.join(contents)
    tokens.append(Token(Token.PARA, start, contents, para_indent))
    return linenum
        
def _tokenize(str, errors):
    """
    Split a given formatted docstring into an ordered list of
    C{Token}s, according to the epytext markup rules.

    @param str: The epytext string
    @type str: C{string}
    @param errors: A list where any errors generated during parsing
        will be stored.  If no list is specified, then errors will 
        generate exceptions.
    @type errors: C{list} of L{ParseError}
    @return: a list of the C{Token}s that make up the given string.
    @rtype: C{list} of L{Token}
    """
    tokens = []
    lines = str.split('\n')

    # Scan through the lines, determining what @type of token we're
    # dealing with, and tokenizing it, as appropriate.
    linenum = 0
    while linenum < len(lines):
        # Get the current line and its indentation.
        line = lines[linenum]
        indent = len(line)-len(line.lstrip())

        if indent == len(line):
            # Ignore blank lines.
            linenum += 1
            continue
        elif line[indent:indent+4] == '>>> ':
            # blocks starting with ">>> " are doctest block tokens.
            linenum = _tokenize_doctest(lines, linenum, indent,
                                        tokens, errors)
        elif _BULLET_RE.match(line, indent):
            # blocks starting with a bullet are LI start tokens.
            linenum = _tokenize_listart(lines, linenum, indent,
                                        tokens, errors)
            if tokens[-1].indent != None:
                indent = tokens[-1].indent
        else:
            # Check for mal-formatted field items.
            if line[indent] == '@':
                estr = "Possible mal-formatted field item."
                errors.append(TokenizationError(estr, linenum, is_fatal=0))
            
            # anything else is either a paragraph or a heading.
            linenum = _tokenize_para(lines, linenum, indent, tokens, errors)

        # Paragraph tokens ending in '::' initiate literal blocks.
        if (tokens[-1].tag == Token.PARA and
            tokens[-1].contents[-2:] == '::'):
            tokens[-1].contents = tokens[-1].contents[:-1]
            linenum = _tokenize_literal(lines, linenum, indent, tokens, errors)

    return tokens


##################################################
## Inline markup ("colorizing")
##################################################

# Assorted regular expressions used for colorizing.
_BRACE_RE = re.compile('{|}')
_TARGET_RE = re.compile('^(.*?)\s*<(?:URI:|URL:)?([^<>]+)>$')

def _colorize(doc, token, errors, tagName='para'):
    """
    Given a string containing the contents of a paragraph, produce a
    DOM C{Element} encoding that paragraph.  Colorized regions are
    represented using DOM C{Element}s, and text is represented using
    DOM C{Text}s.

    @param errors: A list of errors.  Any newly generated errors will
        be appended to this list.
    @type errors: C{list} of C{string}
    
    @param tagName: The element tag for the DOM C{Element} that should
        be generated.
    @type tagName: C{string}
    
    @return: a DOM C{Element} encoding the given paragraph.
    @returntype: C{Element}
    """
    str = token.contents
    linenum = 0
    
    # Maintain a stack of DOM elements, containing the ancestors of
    # the text currently being analyzed.  New elements are pushed when 
    # "{" is encountered, and old elements are popped when "}" is
    # encountered. 
    stack = [Element(tagName)]

    # This is just used to make error-reporting friendlier.  It's a
    # stack parallel to "stack" containing the index of each element's 
    # open brace.
    openbrace_stack = [0]

    # Process the string, scanning for '{' and '}'s.  start is the
    # index of the first unprocessed character.  Each time through the
    # loop, we process the text from the first unprocessed character
    # to the next open or close brace.
    start = 0
    while 1:
        match = _BRACE_RE.search(str, start)
        if match == None: break
        end = match.start()
        
        # Open braces start new colorizing elements.  When preceeded
        # by a capital letter, they specify a colored region, as
        # defined by the _COLORIZING_TAGS dictionary.  Otherwise, 
        # use a special "literal braces" element (with tag "litbrace"),
        # and convert them to literal braces once we find the matching 
        # close-brace.
        if match.group() == '{':
            if (end>0) and 'A' <= str[end-1] <= 'Z':
                if (end-1) > start:
                    stack[-1].children.append(str[start:end-1])
                if str[end-1] not in _COLORIZING_TAGS:
                    estr = "Unknown inline markup tag."
                    errors.append(ColorizingError(estr, token, end-1))
                    stack.append(Element('unknown'))
                else:
                    tag = _COLORIZING_TAGS[str[end-1]]
                    stack.append(Element(tag))
            else:
                if end > start:
                    stack[-1].children.append(str[start:end])
                stack.append(Element('litbrace'))
            openbrace_stack.append(end)
            stack[-2].children.append(stack[-1])
            
        # Close braces end colorizing elements.
        elif match.group() == '}':
            # Check for (and ignore) unbalanced braces.
            if len(stack) <= 1:
                estr = "Unbalanced '}'."
                errors.append(ColorizingError(estr, token, end))
                start = end + 1
                continue

            # Add any remaining text.
            if end > start:
                stack[-1].children.append(str[start:end])

            # Special handling for symbols:
            if stack[-1].tag == 'symbol':
                if (len(stack[-1].children) != 1 or
                    not isinstance(stack[-1].children[0], basestring)):
                    estr = "Invalid symbol code."
                    errors.append(ColorizingError(estr, token, end))
                else:
                    symb = stack[-1].children[0]
                    if symb in _SYMBOLS:
                        # It's a symbol
                        stack[-2].children[-1] = Element('symbol', symb)
                    else:
                        estr = "Invalid symbol code."
                        errors.append(ColorizingError(estr, token, end))
                        
            # Special handling for escape elements:
            if stack[-1].tag == 'escape':
                if (len(stack[-1].children) != 1 or
                    not isinstance(stack[-1].children[0], basestring)):
                    estr = "Invalid escape code."
                    errors.append(ColorizingError(estr, token, end))
                else:
                    escp = stack[-1].children[0]
                    if escp in _ESCAPES:
                        # It's an escape from _ESCPAES
                        stack[-2].children[-1] = _ESCAPES[escp]
                    elif len(escp) == 1:
                        # It's a single-character escape (eg E{.})
                        stack[-2].children[-1] = escp
                    else:
                        estr = "Invalid escape code."
                        errors.append(ColorizingError(estr, token, end))

            # Special handling for literal braces elements:
            if stack[-1].tag == 'litbrace':
                stack[-2].children[-1:] = ['{'] + stack[-1].children + ['}']

            # Special handling for graphs:
            if stack[-1].tag == 'graph':
                _colorize_graph(doc, stack[-1], token, end, errors)

            # Special handling for link-type elements:
            if stack[-1].tag in _LINK_COLORIZING_TAGS:
                _colorize_link(doc, stack[-1], token, end, errors)

            # Pop the completed element.
            openbrace_stack.pop()
            stack.pop()

        start = end+1

    # Add any final text.
    if start < len(str):
        stack[-1].children.append(str[start:])
        
    if len(stack) != 1: 
        estr = "Unbalanced '{'."
        errors.append(ColorizingError(estr, token, openbrace_stack[-1]))

    return stack[0]

GRAPH_TYPES = ['classtree', 'packagetree', 'importgraph', 'callgraph']

def _colorize_graph(doc, graph, token, end, errors):
    """
    Eg::
      G{classtree}
      G{classtree x, y, z}
      G{importgraph}
    """
    bad_graph_spec = False
    
    children = graph.children[:]
    graph.children = []

    if len(children) != 1 or not isinstance(children[0], basestring):
        bad_graph_spec = "Bad graph specification"
    else:
        pieces = children[0].split(None, 1)
        graphtype = pieces[0].replace(':','').strip().lower()
        if graphtype in GRAPH_TYPES:
            if len(pieces) == 2:
                if re.match(r'\s*:?\s*([\w\.]+\s*,?\s*)*', pieces[1]):
                    args = pieces[1].replace(',', ' ').replace(':','').split()
                else:
                    bad_graph_spec = "Bad graph arg list"
            else:
                args = []
        else:
            bad_graph_spec = ("Bad graph type %s -- use one of %s" %
                              (pieces[0], ', '.join(GRAPH_TYPES)))

    if bad_graph_spec:
        errors.append(ColorizingError(bad_graph_spec, token, end))
        graph.children.append('none')
        graph.children.append('')
        return

    graph.children.append(graphtype)
    for arg in args:
        graph.children.append(arg)

def _colorize_link(doc, link, token, end, errors):
    variables = link.children[:]

    # If the last child isn't text, we know it's bad.
    if len(variables)==0 or not isinstance(variables[-1], basestring):
        estr = "Bad %s target." % link.tag
        errors.append(ColorizingError(estr, token, end))
        return
    
    # Did they provide an explicit target?
    match2 = _TARGET_RE.match(variables[-1])
    if match2:
        (text, target) = match2.groups()
        variables[-1] = text
    # Can we extract an implicit target?
    elif len(variables) == 1:
        target = variables[0]
    else:
        estr = "Bad %s target." % link.tag
        errors.append(ColorizingError(estr, token, end))
        return

    # Construct the name element.
    name_elt = Element('name', *variables)

    # Clean up the target.  For URIs, assume http or mailto if they
    # don't specify (no relative urls)
    target = re.sub(r'\s', '', target)
    if link.tag=='uri':
        if not re.match(r'\w+:', target):
            if re.match(r'\w+@(\w+)(\.\w+)*', target):
                target = 'mailto:' + target
            else:
                target = 'http://'+target
    elif link.tag=='link':
        # Remove arg lists for functions (e.g., L{_colorize_link()})
        target = re.sub(r'\(.*\)$', '', target)
        if not re.match(r'^[a-zA-Z_]\w*(\.[a-zA-Z_]\w*)*$', target):
            estr = "Bad link target."
            errors.append(ColorizingError(estr, token, end))
            return

    # Construct the target element.
    target_elt = Element('target', target)

    # Add them to the link element.
    link.children = [name_elt, target_elt]

##################################################
## Formatters
##################################################

def to_epytext(tree, indent=0, seclevel=0):
    """
    Convert a DOM document encoding epytext back to an epytext string.
    This is the inverse operation from L{parse}.  I.e., assuming there
    are no errors, the following is true:
        - C{parse(to_epytext(tree)) == tree}

    The inverse is true, except that whitespace, line wrapping, and
    character escaping may be done differently.
        - C{to_epytext(parse(str)) == str} (approximately)

    @param tree: A DOM document encoding of an epytext string.
    @type tree: C{Element}
    @param indent: The indentation for the string representation of
        C{tree}.  Each line of the returned string will begin with
        C{indent} space characters.
    @type indent: C{int}
    @param seclevel: The section level that C{tree} appears at.  This
        is used to generate section headings.
    @type seclevel: C{int}
    @return: The epytext string corresponding to C{tree}.
    @rtype: C{string}
    """
    if isinstance(tree, basestring):
        str = re.sub(r'\{', '\0', tree)
        str = re.sub(r'\}', '\1', str)
        return str

    if tree.tag == 'epytext': indent -= 2
    if tree.tag == 'section': seclevel += 1
    variables = [to_epytext(c, indent+2, seclevel) for c in tree.children]
    childstr = ''.join(variables)

    # Clean up for literal blocks (add the double "::" back)
    childstr = re.sub(':(\s*)\2', '::\\1', childstr)

    if tree.tag == 'para':
        str = wordwrap(childstr, indent)+'\n'
        str = re.sub(r'((^|\n)\s*\d+)\.', r'\1E{.}', str)
        str = re.sub(r'((^|\n)\s*)-', r'\1E{-}', str)
        str = re.sub(r'((^|\n)\s*)@', r'\1E{@}', str)
        str = re.sub(r'::(\s*($|\n))', r'E{:}E{:}\1', str)
        str = re.sub('\0', 'E{lb}', str)
        str = re.sub('\1', 'E{rb}', str)
        return str
    elif tree.tag == 'li':
        bullet = tree.attribs.get('bullet') or '-'
        return indent*' '+ bullet + ' ' + childstr.lstrip()
    elif tree.tag == 'heading':
        str = re.sub('\0', 'E{lb}',childstr)
        str = re.sub('\1', 'E{rb}', str)
        uline = len(childstr)*_HEADING_CHARS[seclevel-1]
        return (indent-2)*' ' + str + '\n' + (indent-2)*' '+uline+'\n'
    elif tree.tag == 'doctestblock':
        str = re.sub('\0', '{', childstr)
        str = re.sub('\1', '}', str)
        lines = ['  '+indent*' '+line for line in str.split('\n')]
        return '\n'.join(lines) + '\n\n'
    elif tree.tag == 'literalblock':
        str = re.sub('\0', '{', childstr)
        str = re.sub('\1', '}', str)
        lines = [(indent+1)*' '+line for line in str.split('\n')]
        return '\2' + '\n'.join(lines) + '\n\n'
    elif tree.tag == 'field':
        numargs = 0
        while tree.children[numargs+1].tag == 'arg': numargs += 1
        tag = variables[0]
        args = variables[1:1+numargs]
        body = variables[1+numargs:]
        str = (indent)*' '+'@'+variables[0]
        if args: str += '(' + ', '.join(args) + ')'
        return str + ':\n' + ''.join(body)
    elif tree.tag == 'target':
        return '<%s>' % childstr
    elif tree.tag in ('fieldlist', 'tag', 'arg', 'epytext',
                          'section', 'olist', 'ulist', 'name'):
        return childstr
    elif tree.tag == 'symbol':
        return 'E{%s}' % childstr
    elif tree.tag == 'graph':
        return 'G{%s}' % ' '.join(variables)
    else:
        for (tag, name) in _COLORIZING_TAGS.items():
            if name == tree.tag:
                return '%s{%s}' % (tag, childstr)
    raise ValueError('Unknown DOM element %r' % tree.tag)

SYMBOL_TO_PLAINTEXT = {
    'crarr': '\\',
    }

def to_plaintext(tree, indent=0, seclevel=0):
    """    
    Convert a DOM document encoding epytext to a string representation.
    This representation is similar to the string generated by
    C{to_epytext}, but C{to_plaintext} removes inline markup, prints
    escaped characters in unescaped form, etc.

    @param tree: A DOM document encoding of an epytext string.
    @type tree: C{Element}
    @param indent: The indentation for the string representation of
        C{tree}.  Each line of the returned string will begin with
        C{indent} space characters.
    @type indent: C{int}
    @param seclevel: The section level that C{tree} appears at.  This
        is used to generate section headings.
    @type seclevel: C{int}
    @return: The epytext string corresponding to C{tree}.
    @rtype: C{string}
    """
    if isinstance(tree, basestring): return tree

    if tree.tag == 'section': seclevel += 1

    # Figure out the child indent level.
    if tree.tag == 'epytext': cindent = indent
    elif tree.tag == 'li' and tree.attribs.get('bullet'):
        cindent = indent + 1 + len(tree.attribs.get('bullet'))
    else:
        cindent = indent + 2
    variables = [to_plaintext(c, cindent, seclevel) for c in tree.children]
    childstr = ''.join(variables)

    if tree.tag == 'para':
        return wordwrap(childstr, indent)+'\n'
    elif tree.tag == 'li':
        # We should be able to use getAttribute here; but there's no
        # convenient way to test if an element has an attribute..
        bullet = tree.attribs.get('bullet') or '-'
        return indent*' ' + bullet + ' ' + childstr.lstrip()
    elif tree.tag == 'heading':
        uline = len(childstr)*_HEADING_CHARS[seclevel-1]
        return ((indent-2)*' ' + childstr + '\n' +
                (indent-2)*' ' + uline + '\n')
    elif tree.tag == 'doctestblock':
        lines = [(indent+2)*' '+line for line in childstr.split('\n')]
        return '\n'.join(lines) + '\n\n'
    elif tree.tag == 'literalblock':
        lines = [(indent+1)*' '+line for line in childstr.split('\n')]
        return '\n'.join(lines) + '\n\n'
    elif tree.tag == 'fieldlist':
        return childstr
    elif tree.tag == 'field':
        numargs = 0
        while tree.children[numargs+1].tag == 'arg': numargs += 1
        tag = variables[0]
        args = variables[1:1+numargs]
        body = variables[1+numargs:]
        str = (indent)*' '+'@'+variables[0]
        if args: str += '(' + ', '.join(args) + ')'
        return str + ':\n' + ''.join(body)
    elif tree.tag == 'uri':
        if len(variables) != 2: raise ValueError('Bad URI ')
        elif variables[0] == variables[1]: return '<%s>' % variables[1]
        else: return '%r<%s>' % (variables[0], variables[1])
    elif tree.tag == 'link':
        if len(variables) != 2: raise ValueError('Bad Link')
        return '%s' % variables[0]
    elif tree.tag in ('olist', 'ulist'):
        # [xx] always use condensed lists.
        ## Use a condensed list if each list item is 1 line long.
        #for child in variables:
        #    if child.count('\n') > 2: return childstr
        return childstr.replace('\n\n', '\n')+'\n'
    elif tree.tag == 'symbol':
        return '%s' % SYMBOL_TO_PLAINTEXT.get(childstr, childstr)
    elif tree.tag == 'graph':
        return '<<%s graph: %s>>' % (variables[0], ', '.join(variables[1:]))
    else:
        # Assume that anything else can be passed through.
        return childstr

def to_debug(tree, indent=4, seclevel=0):
    """    
    Convert a DOM document encoding epytext back to an epytext string,
    annotated with extra debugging information.  This function is
    similar to L{to_epytext}, but it adds explicit information about
    where different blocks begin, along the left margin.

    @param tree: A DOM document encoding of an epytext string.
    @type tree: C{Element}
    @param indent: The indentation for the string representation of
        C{tree}.  Each line of the returned string will begin with
        C{indent} space characters.
    @type indent: C{int}
    @param seclevel: The section level that C{tree} appears at.  This
        is used to generate section headings.
    @type seclevel: C{int}
    @return: The epytext string corresponding to C{tree}.
    @rtype: C{string}
    """
    if isinstance(tree, basestring):
        str = re.sub(r'\{', '\0', tree)
        str = re.sub(r'\}', '\1', str)
        return str

    if tree.tag == 'section': seclevel += 1
    variables = [to_debug(c, indent+2, seclevel) for c in tree.children]
    childstr = ''.join(variables)

    # Clean up for literal blocks (add the double "::" back)
    childstr = re.sub(':( *\n     \|\n)\2', '::\\1', childstr)

    if tree.tag == 'para':
        str = wordwrap(childstr, indent-6, 69)+'\n'
        str = re.sub(r'((^|\n)\s*\d+)\.', r'\1E{.}', str)
        str = re.sub(r'((^|\n)\s*)-', r'\1E{-}', str)
        str = re.sub(r'((^|\n)\s*)@', r'\1E{@}', str)
        str = re.sub(r'::(\s*($|\n))', r'E{:}E{:}\1', str)
        str = re.sub('\0', 'E{lb}', str)
        str = re.sub('\1', 'E{rb}', str)
        lines = str.rstrip().split('\n')
        lines[0] = '   P>|' + lines[0]
        lines[1:] = ['     |'+l for l in lines[1:]]
        return '\n'.join(lines)+'\n     |\n'
    elif tree.tag == 'li':
        bullet = tree.attribs.get('bullet') or '-'
        return '  LI>|'+ (indent-6)*' '+ bullet + ' ' + childstr[6:].lstrip()
    elif tree.tag in ('olist', 'ulist'):
        return 'LIST>|'+(indent-4)*' '+childstr[indent+2:]
    elif tree.tag == 'heading':
        str = re.sub('\0', 'E{lb}', childstr)
        str = re.sub('\1', 'E{rb}', str)
        uline = len(childstr)*_HEADING_CHARS[seclevel-1]
        return ('SEC'+`seclevel`+'>|'+(indent-8)*' ' + str + '\n' +
                '     |'+(indent-8)*' ' + uline + '\n')
    elif tree.tag == 'doctestblock':
        str = re.sub('\0', '{', childstr)
        str = re.sub('\1', '}', str)
        lines = ['     |'+(indent-4)*' '+line for line in str.split('\n')]
        lines[0] = 'DTST>'+lines[0][5:]
        return '\n'.join(lines) + '\n     |\n'
    elif tree.tag == 'literalblock':
        str = re.sub('\0', '{', childstr)
        str = re.sub('\1', '}', str)
        lines = ['     |'+(indent-5)*' '+line for line in str.split('\n')]
        lines[0] = ' LIT>'+lines[0][5:]
        return '\2' + '\n'.join(lines) + '\n     |\n'
    elif tree.tag == 'field':
        numargs = 0
        while tree.children[numargs+1].tag == 'arg': numargs += 1
        tag = variables[0]
        args = variables[1:1+numargs]
        body = variables[1+numargs:]
        str = ' FLD>|'+(indent-6)*' '+'@'+variables[0]
        if args: str += '(' + ', '.join(args) + ')'
        return str + ':\n' + ''.join(body)
    elif tree.tag == 'target':
        return '<%s>' % childstr
    elif tree.tag in ('fieldlist', 'tag', 'arg', 'epytext',
                          'section', 'olist', 'ulist', 'name'):
        return childstr
    elif tree.tag == 'symbol':
        return 'E{%s}' % childstr
    elif tree.tag == 'graph':
        return 'G{%s}' % ' '.join(variables)
    else:
        for (tag, name) in _COLORIZING_TAGS.items():
            if name == tree.tag:
                return '%s{%s}' % (tag, childstr)
    raise ValueError('Unknown DOM element %r' % tree.tag)

##################################################
## Top-Level Wrapper function
##################################################
def pparse(str, show_warnings=1, show_errors=1, stream=sys.stderr):
    """
    Pretty-parse the string.  This parses the string, and catches any
    warnings or errors produced.  Any warnings and errors are
    displayed, and the resulting DOM parse structure is returned.

    @param str: The string to parse.
    @type str: C{string}
    @param show_warnings: Whether or not to display non-fatal errors
        generated by parsing C{str}.
    @type show_warnings: C{boolean}
    @param show_errors: Whether or not to display fatal errors 
        generated by parsing C{str}.
    @type show_errors: C{boolean}
    @param stream: The stream that warnings and errors should be
        written to.
    @type stream: C{stream}
    @return: a DOM document encoding the contents of C{str}.
    @rtype: C{Element}
    @raise SyntaxError: If any fatal errors were encountered.
    """
    errors = []
    confused = 0
    try:
        val = parse(str, errors)
        warnings = [e for e in errors if not e.is_fatal()]
        errors = [e for e in errors if e.is_fatal()]
    except:
        confused = 1
        
    if not show_warnings: warnings = []
    warnings.sort()
    errors.sort()
    if warnings:
        print >>stream, '='*SCRWIDTH
        print >>stream, "WARNINGS"
        print >>stream, '-'*SCRWIDTH
        for warning in warnings:
            print >>stream, warning.as_warning()
        print >>stream, '='*SCRWIDTH
    if errors and show_errors:
        if not warnings: print >>stream, '='*SCRWIDTH
        print >>stream, "ERRORS"
        print >>stream, '-'*SCRWIDTH
        for error in errors:
            print >>stream, error
        print >>stream, '='*SCRWIDTH

    if confused: raise
    elif errors: raise SyntaxError('Encountered Errors')
    else: return val

##################################################
## Parse Errors
##################################################

class TokenizationError(ParseError):
    """
    An error generated while tokenizing a formatted documentation
    string.
    """

class StructuringError(ParseError):
    """
    An error generated while structuring a formatted documentation
    string.
    """

class ColorizingError(ParseError):
    """
    An error generated while colorizing a paragraph.
    """
    def __init__(self, descr, token, charnum, is_fatal=1):
        """
        Construct a new colorizing exception.
        
        @param descr: A short description of the error.
        @type descr: C{string}
        @param token: The token where the error occured
        @type token: L{Token}
        @param charnum: The character index of the position in
            C{token} where the error occured.
        @type charnum: C{int}
        """
        ParseError.__init__(self, descr, token.startline, is_fatal)
        self.token = token
        self.charnum = charnum

    CONTEXT_RANGE = 20
    def descr(self):
        RANGE = self.CONTEXT_RANGE
        if self.charnum <= RANGE:
            left = self.token.contents[0:self.charnum]
        else:
            left = '...'+self.token.contents[self.charnum-RANGE:self.charnum]
        if (len(self.token.contents)-self.charnum) <= RANGE:
            right = self.token.contents[self.charnum:]
        else:
            right = (self.token.contents[self.charnum:self.charnum+RANGE]
                     + '...')
        return ('%s\n\n%s%s\n%s^' % (self._descr, left, right, ' '*len(left)))
                
##################################################
## Convenience parsers
##################################################

def parse_as_literal(str):
    """
    Return a DOM document matching the epytext DTD, containing a
    single literal block.  That literal block will include the
    contents of the given string.  This method is typically used as a
    fall-back when the parser fails.

    @param str: The string which should be enclosed in a literal
        block.
    @type str: C{string}
    
    @return: A DOM document containing C{str} in a single literal
        block.
    @rtype: C{Element}
    """
    return Element('epytext', Element('literalblock', str))

def parse_as_para(str):
    """
    Return a DOM document matching the epytext DTD, containing a
    single paragraph.  That paragraph will include the contents of the
    given string.  This can be used to wrap some forms of
    automatically generated information (such as type names) in
    paragraphs.

    @param str: The string which should be enclosed in a paragraph.
    @type str: C{string}
    
    @return: A DOM document containing C{str} in a single paragraph.
    @rtype: C{Element}
    """
    return Element('epytext', Element('para', str))

#################################################################
##                    SUPPORT FOR EPYDOC
#################################################################

def parse_docstring(docstring, errors, **options):
    """
    Parse the given docstring, which is formatted using epytext; and
    return a C{ParsedDocstring} representation of its contents.
    @param docstring: The docstring to parse
    @type docstring: C{string}
    @param errors: A list where any errors generated during parsing
        will be stored.
    @type errors: C{list} of L{ParseError}
    @param options: Extra options.  Unknown options are ignored.
        Currently, no extra options are defined.
    @rtype: L{ParsedDocstring}
    """
    return ParsedEpytextDocstring(parse(docstring, errors), **options)
    
class ParsedEpytextDocstring(ParsedDocstring):
    SYMBOL_TO_HTML = {
        # Symbols
        '<-': '&larr;', '->': '&rarr;', '^': '&uarr;', 'v': '&darr;',
    
        # Greek letters
        'alpha': '&alpha;', 'beta': '&beta;', 'gamma': '&gamma;',
        'delta': '&delta;', 'epsilon': '&epsilon;', 'zeta': '&zeta;',  
        'eta': '&eta;', 'theta': '&theta;', 'iota': '&iota;', 
        'kappa': '&kappa;', 'lambda': '&lambda;', 'mu': '&mu;',  
        'nu': '&nu;', 'xi': '&xi;', 'omicron': '&omicron;',  
        'pi': '&pi;', 'rho': '&rho;', 'sigma': '&sigma;',  
        'tau': '&tau;', 'upsilon': '&upsilon;', 'phi': '&phi;',  
        'chi': '&chi;', 'psi': '&psi;', 'omega': '&omega;',
        'Alpha': '&Alpha;', 'Beta': '&Beta;', 'Gamma': '&Gamma;',
        'Delta': '&Delta;', 'Epsilon': '&Epsilon;', 'Zeta': '&Zeta;',  
        'Eta': '&Eta;', 'Theta': '&Theta;', 'Iota': '&Iota;', 
        'Kappa': '&Kappa;', 'Lambda': '&Lambda;', 'Mu': '&Mu;',  
        'Nu': '&Nu;', 'Xi': '&Xi;', 'Omicron': '&Omicron;',  
        'Pi': '&Pi;', 'Rho': '&Rho;', 'Sigma': '&Sigma;',  
        'Tau': '&Tau;', 'Upsilon': '&Upsilon;', 'Phi': '&Phi;',  
        'Chi': '&Chi;', 'Psi': '&Psi;', 'Omega': '&Omega;',
    
        # HTML character entities
        'larr': '&larr;', 'rarr': '&rarr;', 'uarr': '&uarr;',
        'darr': '&darr;', 'harr': '&harr;', 'crarr': '&crarr;',
        'lArr': '&lArr;', 'rArr': '&rArr;', 'uArr': '&uArr;',
        'dArr': '&dArr;', 'hArr': '&hArr;', 
        'copy': '&copy;', 'times': '&times;', 'forall': '&forall;',
        'exist': '&exist;', 'part': '&part;',
        'empty': '&empty;', 'isin': '&isin;', 'notin': '&notin;',
        'ni': '&ni;', 'prod': '&prod;', 'sum': '&sum;',
        'prop': '&prop;', 'infin': '&infin;', 'ang': '&ang;',
        'and': '&and;', 'or': '&or;', 'cap': '&cap;', 'cup': '&cup;',
        'int': '&int;', 'there4': '&there4;', 'sim': '&sim;',
        'cong': '&cong;', 'asymp': '&asymp;', 'ne': '&ne;',
        'equiv': '&equiv;', 'le': '&le;', 'ge': '&ge;',
        'sub': '&sub;', 'sup': '&sup;', 'nsub': '&nsub;',
        'sube': '&sube;', 'supe': '&supe;', 'oplus': '&oplus;',
        'otimes': '&otimes;', 'perp': '&perp;',
    
        # Alternate (long) names
        'infinity': '&infin;', 'integral': '&int;', 'product': '&prod;',
        '<=': '&le;', '>=': '&ge;',
        }
    
    SYMBOL_TO_LATEX = {
        # Symbols
        '<-': r'\(\leftarrow\)', '->': r'\(\rightarrow\)',
        '^': r'\(\uparrow\)', 'v': r'\(\downarrow\)',
    
        # Greek letters (use lower case when upcase not available)

        'alpha': r'\(\alpha\)', 'beta': r'\(\beta\)', 'gamma':
        r'\(\gamma\)', 'delta': r'\(\delta\)', 'epsilon':
        r'\(\epsilon\)', 'zeta': r'\(\zeta\)', 'eta': r'\(\eta\)',
        'theta': r'\(\theta\)', 'iota': r'\(\iota\)', 'kappa':
        r'\(\kappa\)', 'lambda': r'\(\lambda\)', 'mu': r'\(\mu\)',
        'nu': r'\(\nu\)', 'xi': r'\(\xi\)', 'omicron': r'\(o\)', 'pi':
        r'\(\pi\)', 'rho': r'\(\rho\)', 'sigma': r'\(\sigma\)', 'tau':
        r'\(\tau\)', 'upsilon': r'\(\upsilon\)', 'phi': r'\(\phi\)',
        'chi': r'\(\chi\)', 'psi': r'\(\psi\)', 'omega':
        r'\(\omega\)',
        
        'Alpha': r'\(\alpha\)', 'Beta': r'\(\beta\)', 'Gamma':
        r'\(\Gamma\)', 'Delta': r'\(\Delta\)', 'Epsilon':
        r'\(\epsilon\)', 'Zeta': r'\(\zeta\)', 'Eta': r'\(\eta\)',
        'Theta': r'\(\Theta\)', 'Iota': r'\(\iota\)', 'Kappa':
        r'\(\kappa\)', 'Lambda': r'\(\Lambda\)', 'Mu': r'\(\mu\)',
        'Nu': r'\(\nu\)', 'Xi': r'\(\Xi\)', 'Omicron': r'\(o\)', 'Pi':
        r'\(\Pi\)', 'ho': r'\(\rho\)', 'Sigma': r'\(\Sigma\)', 'Tau':
        r'\(\tau\)', 'Upsilon': r'\(\Upsilon\)', 'Phi': r'\(\Phi\)',
        'Chi': r'\(\chi\)', 'Psi': r'\(\Psi\)', 'Omega':
        r'\(\Omega\)',
    
        # HTML character entities
        'larr': r'\(\leftarrow\)', 'rarr': r'\(\rightarrow\)', 'uarr':
        r'\(\uparrow\)', 'darr': r'\(\downarrow\)', 'harr':
        r'\(\leftrightarrow\)', 'crarr': r'\(\hookleftarrow\)',
        'lArr': r'\(\Leftarrow\)', 'rArr': r'\(\Rightarrow\)', 'uArr':
        r'\(\Uparrow\)', 'dArr': r'\(\Downarrow\)', 'hArr':
        r'\(\Leftrightarrow\)', 'copy': r'{\textcopyright}',
        'times': r'\(\times\)', 'forall': r'\(\forall\)', 'exist':
        r'\(\exists\)', 'part': r'\(\partial\)', 'empty':
        r'\(\emptyset\)', 'isin': r'\(\in\)', 'notin': r'\(\notin\)',
        'ni': r'\(\ni\)', 'prod': r'\(\prod\)', 'sum': r'\(\sum\)',
        'prop': r'\(\propto\)', 'infin': r'\(\infty\)', 'ang':
        r'\(\angle\)', 'and': r'\(\wedge\)', 'or': r'\(\vee\)', 'cap':
        r'\(\cap\)', 'cup': r'\(\cup\)', 'int': r'\(\int\)', 'there4':
        r'\(\therefore\)', 'sim': r'\(\sim\)', 'cong': r'\(\cong\)',
        'asymp': r'\(\approx\)', 'ne': r'\(\ne\)', 'equiv':
        r'\(\equiv\)', 'le': r'\(\le\)', 'ge': r'\(\ge\)', 'sub':
        r'\(\subset\)', 'sup': r'\(\supset\)', 'nsub': r'\(\supset\)',
        'sube': r'\(\subseteq\)', 'supe': r'\(\supseteq\)', 'oplus':
        r'\(\oplus\)', 'otimes': r'\(\otimes\)', 'perp': r'\(\perp\)',
    
        # Alternate (long) names
        'infinity': r'\(\infty\)', 'integral': r'\(\int\)', 'product':
        r'\(\prod\)', '<=': r'\(\le\)', '>=': r'\(\ge\)',
        }
    
    def __init__(self, dom_tree, **options):
        self._tree = dom_tree
        # Caching:
        self._html = self._latex = self._plaintext = None
        self._terms = None
        # inline option -- mark top-level children as inline.
        if options.get('inline') and self._tree is not None:
            for elt in self._tree.children:
                elt.attribs['inline'] = True

    def __str__(self):
        return str(self._tree)
        
    def to_html(self, docstring_linker, directory=None, docindex=None,
                context=None, **options):
        if self._html is not None: return self._html
        if self._tree is None: return ''
        indent = options.get('indent', 0)
        self._html = self._to_html(self._tree, docstring_linker, directory, 
                                   docindex, context, indent)
        return self._html

    def to_latex(self, docstring_linker, **options):
        if self._latex is not None: return self._latex
        if self._tree is None: return ''
        indent = options.get('indent', 0)
        self._hyperref = options.get('hyperref', 1)
        self._latex = self._to_latex(self._tree, docstring_linker, indent)
        return self._latex

    def to_plaintext(self, docstring_linker, **options):
        # [XX] don't cache -- different options might be used!!
        #if self._plaintext is not None: return self._plaintext
        if self._tree is None: return ''
        if 'indent' in options:
            self._plaintext = to_plaintext(self._tree,
                                           indent=options['indent'])
        else:
            self._plaintext = to_plaintext(self._tree)
        return self._plaintext

    def _index_term_key(self, tree):
        str = to_plaintext(tree)
        str = re.sub(r'\s\s+', '-', str)
        return "index-"+re.sub("[^a-zA-Z0-9]", "_", str)

    def _to_html(self, tree, linker, directory, docindex, context,
                 indent=0, seclevel=0):
        if isinstance(tree, basestring):
            return plaintext_to_html(tree)

        if tree.tag == 'epytext': indent -= 2
        if tree.tag == 'section': seclevel += 1

        # Process the variables first.
        variables = [self._to_html(c, linker, directory, docindex, context,
                                   indent+2, seclevel)
                    for c in tree.children]
    
        # Construct the HTML string for the variables.
        childstr = ''.join(variables)
    
        # Perform the approriate action for the DOM tree type.
        if tree.tag == 'para':
            return wordwrap(
                (tree.attribs.get('inline') and '%s' or '<p>%s</p>') % childstr,
                indent)
        elif tree.tag == 'code':
            style = tree.attribs.get('style')
            if style:
                return '<code class="%s">%s</code>' % (style, childstr)
            else:
                return '<code>%s</code>' % childstr
        elif tree.tag == 'uri':
            return ('<a href="%s" target="_top">%s</a>' %
                    (variables[1], variables[0]))
        elif tree.tag == 'link':
            return linker.translate_identifier_xref(variables[1], variables[0])
        elif tree.tag == 'italic':
            return '<i>%s</i>' % childstr
        elif tree.tag == 'math':
            return '<i class="math">%s</i>' % childstr
        elif tree.tag == 'indexed':
            term = Element('epytext', *tree.children, **tree.attribs)
            return linker.translate_indexterm(ParsedEpytextDocstring(term))
            #term_key = self._index_term_key(tree)
            #return linker.translate_indexterm(childstr, term_key)
        elif tree.tag == 'bold':
            return '<b>%s</b>' % childstr
        elif tree.tag == 'ulist':
            return '%s<ul>\n%s%s</ul>\n' % (indent*' ', childstr, indent*' ')
        elif tree.tag == 'olist':
            start = tree.attribs.get('start') or ''
            return ('%s<ol start="%s">\n%s%s</ol>\n' %
                    (indent*' ', start, childstr, indent*' '))
        elif tree.tag == 'li':
            return indent*' '+'<li>\n%s%s</li>\n' % (childstr, indent*' ')
        elif tree.tag == 'heading':
            return ('%s<h%s class="heading">%s</h%s>\n' %
                    ((indent-2)*' ', seclevel, childstr, seclevel))
        elif tree.tag == 'literalblock':
            return '<pre class="literalblock">\n%s\n</pre>\n' % childstr
        elif tree.tag == 'doctestblock':
            return doctest_to_html(tree.children[0].strip())
        elif tree.tag == 'fieldlist':
            raise AssertionError("There should not be any field lists left")
        elif tree.tag in ('epytext', 'section', 'tag', 'arg',
                              'name', 'target', 'html'):
            return childstr
        elif tree.tag == 'symbol':
            symbol = tree.children[0]
            return self.SYMBOL_TO_HTML.get(symbol, '[%s]' % symbol)
        elif tree.tag == 'graph':
            # Generate the graph.
            graph = self._build_graph(variables[0], variables[1:], linker,
                                      docindex, context)
            if not graph: return ''
            # Write the graph.
            image_url = '%s.gif' % graph.uid
            image_file = os.path.join(directory, image_url)
            return graph.to_html(image_file, image_url)
        else:
            raise ValueError('Unknown epytext DOM element %r' % tree.tag)

    #GRAPH_TYPES = ['classtree', 'packagetree', 'importgraph']
    def _build_graph(self, graph_type, graph_args, linker, 
                     docindex, context):
        # Generate the graph
        if graph_type == 'classtree':
            from epydoc.apidoc import ClassDoc
            if graph_args:
                bases = [docindex.find(name, context)
                         for name in graph_args]
            elif isinstance(context, ClassDoc):
                bases = [context]
            else:
                log.warning("Could not construct class tree: you must "
                            "specify one or more base classes.")
                return None
            from epydoc.docwriter.dotgraph import class_tree_graph
            return class_tree_graph(bases, linker, context)
        elif graph_type == 'packagetree':
            from epydoc.apidoc import ModuleDoc
            if graph_args:
                packages = [docindex.find(name, context)
                            for name in graph_args]
            elif isinstance(context, ModuleDoc):
                packages = [context]
            else:
                log.warning("Could not construct package tree: you must "
                            "specify one or more root packages.")
                return None
            from epydoc.docwriter.dotgraph import package_tree_graph
            return package_tree_graph(packages, linker, context)
        elif graph_type == 'importgraph':
            from epydoc.apidoc import ModuleDoc
            modules = [d for d in docindex.root if isinstance(d, ModuleDoc)]
            from epydoc.docwriter.dotgraph import import_graph
            return import_graph(modules, docindex, linker, context)

        elif graph_type == 'callgraph':
            if graph_args:
                docs = [docindex.find(name, context) for name in graph_args]
                docs = [doc for doc in docs if doc is not None]
            else:
                docs = [context]
            from epydoc.docwriter.dotgraph import call_graph
            return call_graph(docs, docindex, linker, context)
        else:
            log.warning("Unknown graph type %s" % graph_type)
            
    
    def _to_latex(self, tree, linker, indent=0, seclevel=0, breakany=0):
        if isinstance(tree, basestring):
            return plaintext_to_latex(tree, breakany=breakany)

        if tree.tag == 'section': seclevel += 1
    
        # Figure out the child indent level.
        if tree.tag == 'epytext': cindent = indent
        else: cindent = indent + 2
        variables = [self._to_latex(c, linker, cindent, seclevel, breakany)
                    for c in tree.children]
        childstr = ''.join(variables)
    
        if tree.tag == 'para':
            return wordwrap(childstr, indent)+'\n'
        elif tree.tag == 'code':
            return '\\texttt{%s}' % childstr
        elif tree.tag == 'uri':
            if len(variables) != 2: raise ValueError('Bad URI ')
            if self._hyperref:
                # ~ and # should not be escaped in the URI.
                uri = tree.children[1].children[0]
                uri = uri.replace('{\\textasciitilde}', '~')
                uri = uri.replace('\\#', '#')
                if variables[0] == variables[1]:
                    return '\\href{%s}{\\textit{%s}}' % (uri, variables[1])
                else:
                    return ('%s\\footnote{\\href{%s}{%s}}' %
                            (variables[0], uri, variables[1]))
            else:
                if variables[0] == variables[1]:
                    return '\\textit{%s}' % variables[1]
                else:
                    return '%s\\footnote{%s}' % (variables[0], variables[1])
        elif tree.tag == 'link':
            if len(variables) != 2: raise ValueError('Bad Link')
            return linker.translate_identifier_xref(variables[1], variables[0])
        elif tree.tag == 'italic':
            return '\\textit{%s}' % childstr
        elif tree.tag == 'math':
            return '\\textit{%s}' % childstr
        elif tree.tag == 'indexed':
            term = Element('epytext', *tree.children, **tree.attribs)
            return linker.translate_indexterm(ParsedEpytextDocstring(term))
        elif tree.tag == 'bold':
            return '\\textbf{%s}' % childstr
        elif tree.tag == 'li':
            return indent*' ' + '\\item ' + childstr.lstrip()
        elif tree.tag == 'heading':
            return ' '*(indent-2) + '(section) %s\n\n' % childstr
        elif tree.tag == 'doctestblock':
            return doctest_to_latex(tree.children[0].strip())
        elif tree.tag == 'literalblock':
            return '\\begin{alltt}\n%s\\end{alltt}\n\n' % childstr
        elif tree.tag == 'fieldlist':
            return indent*' '+'{omitted fieldlist}\n'
        elif tree.tag == 'olist':
            return (' '*indent + '\\begin{enumerate}\n\n' + 
                    ' '*indent + '\\setlength{\\parskip}{0.5ex}\n' +
                    childstr +
                    ' '*indent + '\\end{enumerate}\n\n')
        elif tree.tag == 'ulist':
            return (' '*indent + '\\begin{itemize}\n' +
                    ' '*indent + '\\setlength{\\parskip}{0.6ex}\n' +
                    childstr +
                    ' '*indent + '\\end{itemize}\n\n')
        elif tree.tag == 'symbol':
            symbol = tree.children[0]
            return self.SYMBOL_TO_LATEX.get(symbol, '[%s]' % symbol)
        elif tree.tag == 'graph':
            return '(GRAPH)'
            #raise ValueError, 'graph not implemented yet for latex'
        else:
            # Assume that anything else can be passed through.
            return childstr

    _SUMMARY_RE = re.compile(r'(\s*[\w\W]*?\.)(\s|$)')

    def summary(self):
        if self._tree is None: return self, False
        tree = self._tree
        doc = Element('epytext')
    
        # Find the first paragraph.
        variables = tree.children
        while (len(variables) > 0) and (variables[0].tag != 'para'):
            if variables[0].tag in ('section', 'ulist', 'olist', 'li'):
                variables = variables[0].children
            else:
                variables = variables[1:]
    
        # Special case: if the docstring contains a single literal block,
        # then try extracting the summary from it.
        if (len(variables) == 0 and len(tree.children) == 1 and
            tree.children[0].tag == 'literalblock'):
            str = re.split(r'\n\s*(\n|$).*',
                           tree.children[0].children[0], 1)[0]
            variables = [Element('para')]
            variables[0].children.append(str)
    
        # If we didn't find a paragraph, return an empty epytext.
        if len(variables) == 0: return ParsedEpytextDocstring(doc), False
    
        # Is there anything else, excluding tags, after the first variable?
        long_docs = False
        for var in variables[1:]:
            if isinstance(var, Element) and var.tag == 'fieldlist':
                continue
            long_docs = True
            break
        
        # Extract the first sentence.
        parachildren = variables[0].children
        para = Element('para', inline=True)
        doc.children.append(para)
        for parachild in parachildren:
            if isinstance(parachild, basestring):
                m = self._SUMMARY_RE.match(parachild)
                if m:
                    para.children.append(m.group(1))
                    long_docs |= parachild is not parachildren[-1]
                    if not long_docs:
                        other = parachild[m.end():]
                        if other and not other.isspace():
                            long_docs = True
                    return ParsedEpytextDocstring(doc), long_docs
            para.children.append(parachild)

        return ParsedEpytextDocstring(doc), long_docs

    def split_fields(self, errors=None):
        if self._tree is None: return (self, ())
        tree = Element(self._tree.tag, *self._tree.children,
                       **self._tree.attribs)
        fields = []

        if (tree.children and
            tree.children[-1].tag == 'fieldlist' and
            tree.children[-1].children):
            field_nodes = tree.children[-1].children
            del tree.children[-1]

            for field in field_nodes:
                # Get the tag
                tag = field.children[0].children[0].lower()
                del field.children[0]

                # Get the argument.
                if field.children and field.children[0].tag == 'arg':
                    arg = field.children[0].children[0]
                    del field.children[0]
                else:
                    arg = None

                # Process the field.
                field.tag = 'epytext'
                fields.append(Field(tag, arg, ParsedEpytextDocstring(field)))

        # Save the remaining docstring as the description..
        if tree.children and tree.children[0].children:
            return ParsedEpytextDocstring(tree), fields
        else:
            return None, fields

    
    def index_terms(self):
        if self._terms is None:
            self._terms = []
            self._index_terms(self._tree, self._terms)
        return self._terms

    def _index_terms(self, tree, terms):
        if tree is None or isinstance(tree, basestring):
            return
        
        if tree.tag == 'indexed':
            term = Element('epytext', *tree.children, **tree.attribs)
            terms.append(ParsedEpytextDocstring(term))

        # Look for index items in child nodes.
        for child in tree.children:
            self._index_terms(child, terms)
