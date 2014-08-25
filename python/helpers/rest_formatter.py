import sys
import re
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

  def visit_reference(self, node):
    atts = {}
    if 'refuri' in node:
      atts['href'] = node['refuri']
      if self.settings.cloak_email_addresses and atts['href'].startswith('mailto:'):
        atts['href'] = self.cloak_mailto(atts['href'])
        self.in_mailto = True
      # atts['class'] += ' external'
    else:
      assert 'refid' in node, 'References must have "refuri" or "refid" attribute.'
      atts['href'] = '#' + node['refid']
      atts['class'] += ' internal'
    if not isinstance(node.parent, nodes.TextElement):
      assert len(node) == 1 and isinstance(node[0], nodes.image)
      atts['class'] += ' image-reference'
    self.body.append(self.starttag(node, 'a', '', **atts))

  def starttag(self, node, tagname, suffix='\n', **attributes):
    attr_dicts = [attributes]
    if isinstance(node, nodes.Node):
        attr_dicts.append(node.attributes)
    if isinstance(node, dict):
        attr_dicts.append(node)
    # Munge each attribute dictionary.  Unfortunately, we need to
    # iterate through attributes one at a time because some
    # versions of docutils don't case-normalize attributes.
    for attr_dict in attr_dicts:
        for (key, val) in attr_dict.items():
            # Prefix all CSS classes with "rst-"; and prefix all
            # names with "rst-" to avoid conflicts.
            if key.lower() in ('class', 'id', 'name'):
                attr_dict[key] = 'rst-%s' % val
            elif key.lower() in ('classes', 'ids', 'names'):
                attr_dict[key] = ['rst-%s' % cls for cls in val]
            elif key.lower() == 'href':
                if attr_dict[key][:1]=='#':
                    attr_dict[key] = '#rst-%s' % attr_dict[key][1:]
                else:
                    pass
    # For headings, use class="heading"
    if re.match(r'^h\d+$', tagname):
        attributes['class'] = ' '.join([attributes.get('class',''),
                                        'heading']).strip()

    return HTMLTranslator.starttag(self, node, tagname, suffix,
                                       **attributes)


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
