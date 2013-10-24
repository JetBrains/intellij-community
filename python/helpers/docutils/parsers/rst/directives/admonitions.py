# $Id: admonitions.py 5618 2008-07-28 08:37:32Z strank $
# Author: David Goodger <goodger@python.org>
# Copyright: This module has been placed in the public domain.

"""
Admonition directives.
"""

__docformat__ = 'reStructuredText'


from docutils.parsers.rst import Directive
from docutils.parsers.rst import states, directives
from docutils import nodes


class BaseAdmonition(Directive):

    required_arguments = 0
    optional_arguments = 0
    final_argument_whitespace = True
    option_spec = {}
    has_content = True

    node_class = None
    """Subclasses must set this to the appropriate admonition node class."""

    def run(self):
        self.assert_has_content()
        text = '\n'.join(self.content)
        admonition_node = self.node_class(text)
        if self.arguments:
            title_text = self.arguments[0]
            textnodes, messages = self.state.inline_text(title_text,
                                                         self.lineno)
            admonition_node += nodes.title(title_text, '', *textnodes)
            admonition_node += messages
            if 'class' in self.options:
                classes = self.options['class']
            else:
                classes = ['admonition-' + nodes.make_id(title_text)]
            admonition_node['classes'] += classes
        self.state.nested_parse(self.content, self.content_offset,
                                admonition_node)
        return [admonition_node]


class Admonition(BaseAdmonition):

    required_arguments = 1
    option_spec = {'class': directives.class_option}
    node_class = nodes.admonition


class Attention(BaseAdmonition):

    node_class = nodes.attention


class Caution(BaseAdmonition):

    node_class = nodes.caution


class Danger(BaseAdmonition):

    node_class = nodes.danger


class Error(BaseAdmonition):

    node_class = nodes.error


class Hint(BaseAdmonition):

    node_class = nodes.hint


class Important(BaseAdmonition):

    node_class = nodes.important


class Note(BaseAdmonition):

    node_class = nodes.note


class Tip(BaseAdmonition):

    node_class = nodes.tip


class Warning(BaseAdmonition):

    node_class = nodes.warning
