import sys

import epydoc.markup.epytext
from epydoc.markup import DocstringLinker
from epydoc.markup.epytext import parse_docstring, ParseError, _colorize
from rest_formatter import read_safe, print_safe


def _add_para(doc, para_token, stack, indent_stack, errors):
    """Colorize the given paragraph, and add it to the DOM tree."""
    para = _colorize(doc, para_token, errors)
    if para_token.inline:
        para.attribs['inline'] = True
    stack[-1].children.append(para)


epydoc.markup.epytext._add_para = _add_para
ParseError.is_fatal = lambda self: False

src = read_safe()
errors = []


class EmptyLinker(DocstringLinker):
    def translate_indexterm(self, indexterm):
        return ""

    def translate_identifier_xref(self, identifier, label=None):
        return identifier


docstring = parse_docstring(src, errors)
docstring, fields = docstring.split_fields()
html = docstring.to_html(EmptyLinker())

if errors and not html:
    print_safe(u'Error parsing docstring:\n', error=True)
    for error in errors:
        # This script is run only with Python 2 interpreter
        print_safe(unicode(error) + "\n", error=True)
    sys.exit(1)

print_safe(html)
