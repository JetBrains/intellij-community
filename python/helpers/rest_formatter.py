import sys
from docutils.core import publish_string
from docutils import nodes
from docutils.nodes import Text
from docutils.writers.html4css1 import HTMLTranslator
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

  def visit_field_body(self, node):
    self.body.append(self.starttag(node, 'td', '', CLASS='field-body'))
    parent_text = node.parent[0][0].astext()
    if hasattr(node.parent, "type"):
      self.body.append("(")
      self.body.append(self.starttag(node, 'a', '',
                                     **{"href": 'psi_element://#typename#' + node.parent.type}))
      self.body.append(node.parent.type)
      self.body.append("</a>")
      self.body.append(") ")
    elif parent_text.startswith("type "):
      index = parent_text.index("type ")
      type_string = parent_text[index + 5]
      self.body.append(self.starttag(node, 'a', '',
                                     **{"href": 'psi_element://#typename#' + type_string}))
    elif parent_text.startswith("rtype"):
      type_string = node.children[0][0].astext()
      self.body.append(self.starttag(node, 'a', '',
                                     **{"href": 'psi_element://#typename#' + type_string}))

    self.set_class_on_child(node, 'first', 0)
    field = node.parent
    if (self.compact_field_list or
          isinstance(field.parent, nodes.docinfo) or
            field.parent.index(field) == len(field.parent) - 1):
      # If we are in a compact list, the docinfo, or if this is
      # the last field of the field list, do not add vertical
      # space after last element.
      self.set_class_on_child(node, 'last', -1)

  def depart_field_body(self, node):
    if node.parent[0][0].astext().startswith("type "):
      self.body.append("</a>")
    HTMLTranslator.depart_field_body(self, node)


  def visit_field_list(self, node):
    fields = {}
    for n in node.children:
      if len(n.children) == 0: continue
      child = n.children[0]
      rawsource = child.rawsource
      if rawsource.startswith("param "):
        index = rawsource.index("param ")
        if len(child.children) == 0: continue
        child.children[0] = Text(rawsource[index + 6:])
        fields[rawsource[index + 6:]] = n
      if rawsource == "return":
        fields["return"] = n

    for n in node.children:
      if len(n.children) == 0: continue
      child = n.children[0]
      rawsource = child.rawsource
      if rawsource.startswith("type "):
        index = rawsource.index("type ")
        name = rawsource[index + 5:]
        if fields.has_key(name):
          fields[name].type = n.children[1][0][0]
          node.children.remove(n)
      if rawsource == "rtype":
        if fields.has_key("return"):
          fields["return"].type = n.children[1][0][0]
          node.children.remove(n)

    HTMLTranslator.visit_field_list(self, node)


  def unknown_visit(self, node):
    """ Ignore unknown nodes """

  def unknown_departure(self, node):
    """ Ignore unknown nodes """

  def visit_block_quote(self, node):
    self.body.append(self.emptytag(node, "br"))

  def depart_block_quote(self, node):
    pass

  def visit_literal(self, node):
    """Process text to prevent tokens from wrapping."""
    self.body.append(
      self.starttag(node, 'tt', '', CLASS='docutils literal'))
    text = node.astext()
    for token in self.words_and_spaces.findall(text):
      if token.strip():
        self.body.append('<code>%s</code>'
                         % self.encode(token))
      elif token in ('\n', ' '):
        # Allow breaks at whitespace:
        self.body.append(token)
      else:
        # Protect runs of multiple spaces; the last space can wrap:
        self.body.append('&nbsp;' * (len(token) - 1) + ' ')
    self.body.append('</tt>')
    raise nodes.SkipNode


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
                 settings_overrides={'report_level': 10000,
                                     'halt_level': 10000,
                                     'warning_stream': None})
  return MyParsedRstDocstring(writer.document)


try:
  src = sys.stdin.read()

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
