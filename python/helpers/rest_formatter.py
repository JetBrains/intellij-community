import re
import sys

from docutils import nodes
from docutils.core import publish_string
from docutils.nodes import Text, field_body, field_name, rubric
from docutils.writers.html4css1 import HTMLTranslator
from epydoc.markup import DocstringLinker
from epydoc.markup.restructuredtext import ParsedRstDocstring, _EpydocHTMLTranslator, \
    _DocumentPseudoWriter, _EpydocReader


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
                                           href='psi_element://#typename#' + node.parent.type))
            self.body.append(node.parent.type)
            self.body.append("</a>")
            self.body.append(") ")
        elif parent_text.startswith("type "):
            index = parent_text.index("type ")
            type_string = parent_text[index + 5]
            self.body.append(self.starttag(node, 'a', '',
                                           href='psi_element://#typename#' + type_string))
        elif parent_text.startswith("rtype"):
            type_string = node.children[0][0].astext()
            self.body.append(self.starttag(node, 'a', '',
                                           href='psi_element://#typename#' + type_string))

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
            # For some reason additional classes in bullet list make it render poorly.
            # Such lists are used to render multiple return values in Numpy docstrings by Napoleon.
            if tagname == 'ul' and isinstance(node.parent, field_body):
                attr_dict.pop('class', None)
                attr_dict.pop('classes', None)
                continue

            for (key, val) in attr_dict.items():
                # Prefix all CSS classes with "rst-"; and prefix all
                # names with "rst-" to avoid conflicts.
                if key.lower() in ('class', 'id', 'name'):
                    attr_dict[key] = 'rst-%s' % val
                elif key.lower() in ('classes', 'ids', 'names'):
                    attr_dict[key] = ['rst-%s' % cls for cls in val]
                elif key.lower() == 'href':
                    if attr_dict[key][:1] == '#':
                        attr_dict[key] = '#rst-%s' % attr_dict[key][1:]

        if tagname == 'th' and isinstance(node, field_name):
            attributes['valign'] = 'top'

        # Render rubric start as HTML header
        if tagname == 'p' and isinstance(node, rubric):
            tagname = 'h1'

        # For headings, use class="heading"
        if re.match(r'^h\d+$', tagname):
            attributes['class'] = ' '.join([attributes.get('class', ''), 'heading']).strip()
        return HTMLTranslator.starttag(self, node, tagname, suffix, **attributes)

    def visit_field_list(self, node):
        fields = {}
        for n in node.children:
            if not n.children:
                continue
            child = n.children[0]
            rawsource = child.rawsource
            if rawsource.startswith("param "):
                index = rawsource.index("param ")
                if not child.children:
                    continue
                # Strip leading escaped asterisks for vararg parameters in Google code style docstrings
                trimmed_name = re.sub(r'\\\*', '*', rawsource[index + 6:])
                child.children[0] = Text(trimmed_name)
                fields[trimmed_name] = n
            if rawsource == "return":
                fields["return"] = n

        for n in node.children:
            if len(n.children) < 2:
                continue
            field_name, field_body = n.children[0], n.children[1]
            rawsource = field_name.rawsource
            if rawsource.startswith("type "):
                index = rawsource.index("type ")
                name = re.sub(r'\\\*', '*', rawsource[index + 5:])
                if name in fields:
                    fields[name].type = field_body[0][0] if field_body.children else ''
                    node.children.remove(n)
            if rawsource == "rtype":
                if "return" in fields:
                    fields["return"].type = field_body[0][0] if field_body.children else ''
                    node.children.remove(n)

        HTMLTranslator.visit_field_list(self, node)

    def unknown_visit(self, node):
        """ Ignore unknown nodes """

    def unknown_departure(self, node):
        """ Ignore unknown nodes """

    def visit_problematic(self, node):
        """Don't insert hyperlinks to nowhere for e.g. unclosed asterisks."""
        if not self._is_text_wrapper(node):
            return HTMLTranslator.visit_problematic(self, node)

        node_text = node.astext()
        m = re.match(r'(:\w+)?(:\S+:)?`(.+?)`', node_text)
        if m:
            _, directive, text = m.groups('')
            if directive[1:-1] == 'exc':
                self.body.append(self.starttag(node, 'a', '', href = 'psi_element://#typename#' + text))
                self.body.append(text)
                self.body.append('</a>')
            else:
                self.body.append(text)
        else:
            self.body.append(node_text)
        raise nodes.SkipNode

    def depart_problematic(self, node):
        if not self._is_text_wrapper(node):
            return HTMLTranslator.depart_problematic(self, node)

    def visit_Text(self, node):
        text = node.astext()
        encoded = self.encode(text)
        if not isinstance(node.parent, (nodes.literal, nodes.literal_block)):
            encoded = encoded.replace('---', '&mdash;').replace('--', '&ndash;')
        if self.in_mailto and self.settings.cloak_email_addresses:
            encoded = self.cloak_email(encoded)
        self.body.append(encoded)

    def _is_text_wrapper(self, node):
        return len(node.children) == 1 and isinstance(node.children[0], Text)

    def visit_block_quote(self, node):
        self.body.append(self.emptytag(node, "br"))

    def depart_block_quote(self, node):
        pass

    def visit_literal(self, node):
        """Process text to prevent tokens from wrapping."""
        self.body.append(self.starttag(node, 'tt', '', CLASS='docutils literal'))
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
    reader = _EpydocReader(errors)  # Outputs errors to the list.
    publish_string(docstring, writer=writer, reader=reader,
                   settings_overrides={'report_level': 10000,
                                       'halt_level': 10000,
                                       'warning_stream': None})
    return MyParsedRstDocstring(writer.document)


def main(text=None):
    src = sys.stdin.read() if text is None else text

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

if __name__ == '__main__':
    main()
