import sys
from epydoc.markup import DocstringLinker
from epydoc.markup.epytext import parse_docstring

try:
    src = sys.stdin.read()
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
        sys.stderr.write("Error parsing docstring:\n")
        for error in errors:
            sys.stderr.write(str(error) + "\n")
        sys.exit(1)

    sys.stdout.write(html)
    sys.stdout.flush()
except:
    exc_type, exc_value, exc_traceback = sys.exc_info()
    sys.stderr.write("Error calculating docstring: " + str(exc_value))
    sys.exit(1)
