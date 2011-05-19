import sys
from docutils.core import publish_string
from epydoc.markup import DocstringLinker
from epydoc.markup.restructuredtext import ParsedRstDocstring, _EpydocHTMLTranslator, _DocumentPseudoWriter, _EpydocReader

class RestHTMLTranslator(_EpydocHTMLTranslator):
  def visit_field_name(self, node):
    atts = {}
    if self.in_docinfo:
        atts['class'] = 'docinfo-name'
    else:
        atts['class'] = 'field-name'

    self.context.append('')
    atts['align'] = "right"
    self.body.append(self.starttag(node, 'th', '', **atts))

class MyParsedRstDocstring(ParsedRstDocstring):
  def __init__(self, document):
    ParsedRstDocstring.__init__(self, document)

  def to_html(self, docstring_linker, directory=None,
            docindex=None, context=None, **options):
    visitor = RestHTMLTranslator(self._document, docstring_linker,
                                    directory, docindex, context)
    self._document.walkabout(visitor)
    return ''.join(visitor.body)

def parse_docstring(docstring, errors, **options):
    writer = _DocumentPseudoWriter()
    reader = _EpydocReader(errors) # Outputs errors to the list.
    publish_string(docstring, writer=writer, reader=reader,
                   settings_overrides={'report_level':10000,
                                       'halt_level':10000,
                                       'warning_stream':None})
    return MyParsedRstDocstring(writer.document)

try:
    src = "".join(sys.argv[1:])

    errors = []
    class EmptyLinker(DocstringLinker):
        def translate_indexterm(self, indexterm):
            return ""

        def translate_identifier_xref(self, identifier, label=None):
            return identifier

    docstring = parse_docstring(src, errors)
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
