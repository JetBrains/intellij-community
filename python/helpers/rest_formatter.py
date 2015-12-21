import re
import sys

from docutils import nodes
from docutils.core import publish_string
from docutils.frontend import OptionParser
from docutils.nodes import Text, field_body, field_name, rubric
from docutils.parsers.rst import directives
from docutils.parsers.rst.directives.admonitions import BaseAdmonition
from docutils.writers.html4css1 import HTMLTranslator, Writer as HTMLWriter
from docutils.writers import Writer


# Copied from the Sphinx' sources. Docutils doesn't handle "seealso" directives by default.
class seealso(nodes.Admonition, nodes.Element):
    """Custom "see also" admonition."""


class SeeAlso(BaseAdmonition):
    """
    An admonition mentioning things to look at as reference.
    """
    node_class = seealso

directives.register_directive('seealso', SeeAlso)


class RestHTMLTranslator(HTMLTranslator):
    settings = None

    def __init__(self, document):
        # Copied from epydoc.markup.restructuredtext._EpydocHTMLTranslator
        if self.settings is None:
            settings = OptionParser([HTMLWriter()]).get_default_values()
            self.__class__.settings = settings
        document.settings = self.settings

        HTMLTranslator.__init__(self, document)

    def visit_document(self, node):
        pass

    def depart_document(self, node):
        pass

    def visit_docinfo(self, node):
        pass

    def depart_docinfo(self, node):
        pass

    def unimplemented_visit(self, node):
        pass

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
            type_string = parent_text[index + len("type ")]
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

        # For headings, use class="heading"
        if re.match(r'^h\d+$', tagname):
            attributes['class'] = ' '.join([attributes.get('class', ''), 'heading']).strip()
        return HTMLTranslator.starttag(self, node, tagname, suffix, **attributes)

    def visit_rubric(self, node):
        self.body.append(self.starttag(node, 'h1', '', CLASS='rubric'))

    def depart_rubric(self, node):
        self.body.append('</h1>\n')

    def visit_note(self, node):
        self.body.append('<h1 class="heading">Note</h1>\n')

    def depart_note(self, node):
        pass

    def visit_seealso(self, node):
        self.body.append('<h1 class="heading">See Also</h1>\n')

    def depart_seealso(self, node):
        pass

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
                param_name = rawsource[index + len("param "):]
                param_type = None
                parts = param_name.rsplit(None, 1)
                if len(parts) == 2:
                    param_type, param_name = parts
                # Strip leading escaped asterisks for vararg parameters in Google code style docstrings
                param_name = re.sub(r'\\\*', '*', param_name)
                child.children[0] = Text(param_name)
                fields[param_name] = n
                if param_type:
                    n.type = param_type
            if rawsource == "return":
                fields["return"] = n

        for n in node.children:
            if len(n.children) < 2:
                continue
            field_name, field_body = n.children[0], n.children[1]
            rawsource = field_name.rawsource
            if rawsource.startswith("type "):
                index = rawsource.index("type ")
                name = re.sub(r'\\\*', '*', rawsource[index + len("type "):])
                if name in fields:
                    fields[name].type = self._strip_markup(field_body.astext())[1]
                    node.children.remove(n)
            if rawsource == "rtype":
                if "return" in fields:
                    fields["return"].type = self._strip_markup(field_body.astext())[1]
                    node.children.remove(n)

        HTMLTranslator.visit_field_list(self, node)

    def unknown_visit(self, node):
        """ Ignore unknown nodes """

    def unknown_departure(self, node):
        """ Ignore unknown nodes """

    def visit_problematic(self, node):
        # Don't insert hyperlinks to nowhere for e.g. unclosed asterisks
        if not self._is_text_wrapper(node):
            return HTMLTranslator.visit_problematic(self, node)

        directive, text = self._strip_markup(node.astext())
        if directive[1:-1] in ('exc', 'class'):
            self.body.append(self.starttag(node, 'a', '', href='psi_element://#typename#' + text))
            self.body.append(text)
            self.body.append('</a>')
        else:
            self.body.append(text)
        raise nodes.SkipNode

    @staticmethod
    def _strip_markup(text):
        m = re.match(r'(:\w+)?(:\S+:)?`(.+?)`', text)
        if m:
            _, directive, trimmed = m.groups('')
            return directive, trimmed
        return None, text

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


def format_docstring(docstring):
    class _DocumentPseudoWriter(Writer):
        def __init__(self):
            self.document = None
            Writer.__init__(self)

        def translate(self):
            self.output = ''

    writer = _DocumentPseudoWriter()
    publish_string(docstring, writer=writer, settings_overrides={'report_level': 10000,
                                                                 'halt_level': 10000,
                                                                 'warning_stream': None,
                                                                 'docinfo_xform': False})
    document = writer.document
    document.settings.xml_declaration = None
    visitor = RestHTMLTranslator(document)
    document.walkabout(visitor)
    return ''.join(visitor.body)


def main(text=None):
    src = sys.stdin.read() if text is None else text

    html = format_docstring(src)

    sys.stdout.write(html)
    sys.stdout.flush()


if __name__ == '__main__':
    main()
