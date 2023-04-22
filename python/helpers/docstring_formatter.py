import argparse
import json
import os
import re
import sys
import textwrap

import six
from six import text_type, u

ENCODING = 'utf-8'

# regexp from sphinxcontrib/napoleon/docstring.py:35 and py2only/docutils/parsers/rst/states.py:1107
TAGS_START = re.compile(
    r'(\.\. \S+::)|:(?![: ])([^:\\]|\\.|:(?!([ `]|$)))*(?<! ):( +|$)')


def format_fragments(fragments_list):
    formatted_fragments = []

    for element in fragments_list:
        formatted = format_rest(element['myDescription'])
        if formatted.startswith('<p>') and formatted.endswith('</p>\n'):
            formatted = formatted[len('<p>'):][:-len('</p>\n')]
        formatted_fragments.append({
            'myName': element['myName'],
            'myDescription': formatted,
            'myFragmentType': element['myFragmentType']
        })

    return formatted_fragments


def format_rest(docstring):
    from docutils import nodes
    from docutils.core import publish_string
    from docutils.frontend import OptionParser
    from docutils.nodes import Text, field_body, field_name, SkipNode
    from docutils.parsers.rst import directives
    from docutils.parsers.rst.directives.admonitions import BaseAdmonition
    from docutils.writers import Writer
    from docutils.writers.html4css1 import HTMLTranslator, Writer as HTMLWriter

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
            self.body.append(self.starttag(node, 'h4', '', CLASS='rubric'))

        def depart_rubric(self, node):
            self.body.append('</h4>\n')

        def visit_note(self, node):
            self.body.append('<h4 class="heading">Note</h4>\n')

        def depart_note(self, node):
            pass

        def visit_seealso(self, node):
            self.body.append('<h4 class="heading">See Also</h4>\n')

        def depart_seealso(self, node):
            pass

        def visit_field_list(self, node):
            raise SkipNode

        def unknown_visit(self, node):
            """ Ignore unknown nodes """

        def unknown_departure(self, node):
            """ Ignore unknown nodes """

        def visit_problematic(self, node):
            # Don't insert hyperlinks to nowhere for e.g. unclosed asterisks
            if not self._is_text_wrapper(node):
                return HTMLTranslator.visit_problematic(self, node)

            directive, text = self._strip_markup(node.astext())
            if directive and directive[1:-1] in ('exc', 'class'):
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

    class _DocumentPseudoWriter(Writer):
        def __init__(self):
            self.document = None
            Writer.__init__(self)

        def translate(self):
            self.output = ''

    writer = _DocumentPseudoWriter()
    docstring = add_blank_line_before_first_tag(docstring)
    publish_string(docstring, writer=writer, settings_overrides={'report_level': 10000,
                                                                 'halt_level': 10000,
                                                                 'warning_stream': None,
                                                                 'docinfo_xform': False})
    document = writer.document
    document.settings.xml_declaration = None
    visitor = RestHTMLTranslator(document)
    document.walkabout(visitor)
    return u('').join(visitor.body)


def add_blank_line_before_first_tag(docstring):
    input_lines = docstring.splitlines()
    for i, line in enumerate(input_lines):
        if TAGS_START.match(line):
            if i > 0 and not input_lines[i - 1].isspace():
                input_lines.insert(i, '')
            break
    return '\n'.join(input_lines)


def format_google(docstring):
    from sphinxcontrib.napoleon import GoogleDocstring
    transformed = text_type(GoogleDocstring(textwrap.dedent(docstring)))
    return format_rest(transformed)


def format_numpy(docstring):
    from sphinxcontrib.napoleon import NumpyDocstring
    transformed = text_type(NumpyDocstring(textwrap.dedent(docstring)))
    return format_rest(transformed)


def format_epytext(docstring):
    if six.PY3:
        return u('Epydoc is not compatible with Python 3 interpreter')

    import epydoc.markup.epytext
    from epydoc.markup import DocstringLinker
    from epydoc.markup.epytext import parse_docstring, ParseError, _colorize

    def _add_para(doc, para_token, stack, indent_stack, errors):
        """Colorize the given paragraph, and add it to the DOM tree."""
        para = _colorize(doc, para_token, errors)
        if para_token.inline:
            para.attribs['inline'] = True
        stack[-1].children.append(para)

    epydoc.markup.epytext._add_para = _add_para
    ParseError.is_fatal = lambda self: False

    errors = []

    class EmptyLinker(DocstringLinker):
        def translate_indexterm(self, indexterm):
            return ""

        def translate_identifier_xref(self, identifier, label=None):
            return identifier

    docstring = parse_docstring(docstring, errors)
    docstring, fields = docstring.split_fields()
    html = docstring.to_html(EmptyLinker())

    if errors and not html:
        # It's not possible to recover original stacktraces of the errors
        error_lines = '\n'.join(text_type(e) for e in errors)
        raise Exception('Error parsing docstring. Probable causes:\n' + error_lines)

    return html


def format_body(docstring_format, input_body):
    formatter = {
        'rest': format_rest,
        'google': format_google,
        'numpy': format_numpy,
        'epytext': format_epytext
    }.get(docstring_format, format_rest)
    return formatter(input_body)


def main():
    _stdin = os.fdopen(sys.stdin.fileno(), 'rb')
    _stdout = os.fdopen(sys.stdout.fileno(), 'wb')
    _stderr = os.fdopen(sys.stderr.fileno(), 'wb')

    def read_safe():
        return _stdin.read().decode(ENCODING)

    def print_safe(s, error=False):
        stream = _stderr if error else _stdout
        stream.write(s.encode(ENCODING))
        stream.flush()

    # Remove existing Sphinx extensions registered via
    # sphinxcontrib setuptools namespace package, as they
    # conflict with sphinxcontrib.napoleon that we bundle.
    sys.modules.pop('sphinxcontrib', None)

    parser = argparse.ArgumentParser()
    parser.add_argument('--format', default='rest', nargs='?', const='rest')
    parser.add_argument('--fragments', action="store_true")
    parser.add_argument('--input', type=argparse.FileType('rb'), default=_stdin, nargs='?', const=_stdin)
    args = parser.parse_args()

    if args.input:
        try:
            text = args.input.read().decode(ENCODING)
        finally:
            args.input.close()
    else:
        text = read_safe()

    try:
        if args.fragments:
            input_json = json.loads(text)
            formatted_body = format_body(args.format, input_json['body'])
            formatted_fragments = format_fragments(input_json['fragments'])
            print_safe(json.dumps({
                'body': formatted_body,
                'fragments': formatted_fragments,
            }, ensure_ascii=False, separators=(',', ':'), sort_keys=True))
        else:
            print_safe(format_body(args.format, text))
    except ImportError:
        print_safe('sys.path = %s\n\n' % sys.path, error=True)
        raise


if __name__ == '__main__':
    main()
