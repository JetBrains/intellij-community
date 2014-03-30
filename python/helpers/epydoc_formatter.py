import sys
from epydoc.markup import DocstringLinker
from epydoc.markup.epytext import parse_docstring, ParseError, _colorize
import epydoc.markup.epytext

def _add_para(doc, para_token, stack, indent_stack, errors):
  """Colorize the given paragraph, and add it to the DOM tree."""
  para = _colorize(doc, para_token, errors)
  if para_token.inline:
    para.attribs['inline'] = True
  stack[-1].children.append(para)

epydoc.markup.epytext._add_para = _add_para

def is_fatal():
  return False

ParseError.is_fatal = is_fatal

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
