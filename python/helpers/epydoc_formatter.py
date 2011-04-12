import sys
from epydoc.markup import DocstringLinker
from epydoc.markup.epytext import parse_docstring

src = sys.stdin.read()
errors = []

class EmptyLinker(DocstringLinker):
    def translate_indexterm(self, indexterm):
        return ""

    def translate_identifier_xref(self, identifier, label=None):
        return identifier

docstring = parse_docstring(src, errors)
sys.stdout.write(docstring.to_html(EmptyLinker()))

