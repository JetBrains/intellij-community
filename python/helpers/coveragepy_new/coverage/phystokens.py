# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Better tokenizing for coverage.py."""

import ast
import keyword
import re
import token
import tokenize

from coverage import env
from coverage.misc import contract


def phys_tokens(toks):
    """Return all physical tokens, even line continuations.

    tokenize.generate_tokens() doesn't return a token for the backslash that
    continues lines.  This wrapper provides those tokens so that we can
    re-create a faithful representation of the original source.

    Returns the same values as generate_tokens()

    """
    last_line = None
    last_lineno = -1
    last_ttext = None
    for ttype, ttext, (slineno, scol), (elineno, ecol), ltext in toks:
        if last_lineno != elineno:
            if last_line and last_line.endswith("\\\n"):
                # We are at the beginning of a new line, and the last line
                # ended with a backslash.  We probably have to inject a
                # backslash token into the stream. Unfortunately, there's more
                # to figure out.  This code::
                #
                #   usage = """\
                #   HEY THERE
                #   """
                #
                # triggers this condition, but the token text is::
                #
                #   '"""\\\nHEY THERE\n"""'
                #
                # so we need to figure out if the backslash is already in the
                # string token or not.
                inject_backslash = True
                if last_ttext.endswith("\\"):
                    inject_backslash = False
                elif ttype == token.STRING:
                    if "\n" in ttext and ttext.split('\n', 1)[0][-1] == '\\':
                        # It's a multi-line string and the first line ends with
                        # a backslash, so we don't need to inject another.
                        inject_backslash = False
                if inject_backslash:
                    # Figure out what column the backslash is in.
                    ccol = len(last_line.split("\n")[-2]) - 1
                    # Yield the token, with a fake token type.
                    yield (
                        99999, "\\\n",
                        (slineno, ccol), (slineno, ccol+2),
                        last_line
                    )
            last_line = ltext
        if ttype not in (tokenize.NEWLINE, tokenize.NL):
            last_ttext = ttext
        yield ttype, ttext, (slineno, scol), (elineno, ecol), ltext
        last_lineno = elineno


class MatchCaseFinder(ast.NodeVisitor):
    """Helper for finding match/case lines."""
    def __init__(self, source):
        # This will be the set of line numbers that start match or case statements.
        self.match_case_lines = set()
        self.visit(ast.parse(source))

    def visit_Match(self, node):
        """Invoked by ast.NodeVisitor.visit"""
        self.match_case_lines.add(node.lineno)
        for case in node.cases:
            self.match_case_lines.add(case.pattern.lineno)
        self.generic_visit(node)


@contract(source='unicode')
def source_token_lines(source):
    """Generate a series of lines, one for each line in `source`.

    Each line is a list of pairs, each pair is a token::

        [('key', 'def'), ('ws', ' '), ('nam', 'hello'), ('op', '('), ... ]

    Each pair has a token class, and the token text.

    If you concatenate all the token texts, and then join them with newlines,
    you should have your original `source` back, with two differences:
    trailing whitespace is not preserved, and a final line with no newline
    is indistinguishable from a final line with a newline.

    """

    ws_tokens = {token.INDENT, token.DEDENT, token.NEWLINE, tokenize.NL}
    line = []
    col = 0

    source = source.expandtabs(8).replace('\r\n', '\n')
    tokgen = generate_tokens(source)

    if env.PYBEHAVIOR.soft_keywords:
        match_case_lines = MatchCaseFinder(source).match_case_lines

    for ttype, ttext, (sline, scol), (_, ecol), _ in phys_tokens(tokgen):
        mark_start = True
        for part in re.split('(\n)', ttext):
            if part == '\n':
                yield line
                line = []
                col = 0
                mark_end = False
            elif part == '':
                mark_end = False
            elif ttype in ws_tokens:
                mark_end = False
            else:
                if mark_start and scol > col:
                    line.append(("ws", " " * (scol - col)))
                    mark_start = False
                tok_class = tokenize.tok_name.get(ttype, 'xx').lower()[:3]
                if ttype == token.NAME:
                    if keyword.iskeyword(ttext):
                        # Hard keywords are always keywords.
                        tok_class = "key"
                    elif env.PYBEHAVIOR.soft_keywords and keyword.issoftkeyword(ttext):
                        # Soft keywords appear at the start of the line, on lines that start
                        # match or case statements.
                        if len(line) == 0:
                            is_start_of_line = True
                        elif (len(line) == 1) and line[0][0] == "ws":
                            is_start_of_line = True
                        else:
                            is_start_of_line = False
                        if is_start_of_line and sline in match_case_lines:
                            tok_class = "key"
                line.append((tok_class, part))
                mark_end = True
            scol = 0
        if mark_end:
            col = ecol

    if line:
        yield line


class CachedTokenizer:
    """A one-element cache around tokenize.generate_tokens.

    When reporting, coverage.py tokenizes files twice, once to find the
    structure of the file, and once to syntax-color it.  Tokenizing is
    expensive, and easily cached.

    This is a one-element cache so that our twice-in-a-row tokenizing doesn't
    actually tokenize twice.

    """
    def __init__(self):
        self.last_text = None
        self.last_tokens = None

    @contract(text='unicode')
    def generate_tokens(self, text):
        """A stand-in for `tokenize.generate_tokens`."""
        if text != self.last_text:
            self.last_text = text
            readline = iter(text.splitlines(True)).__next__
            try:
                self.last_tokens = list(tokenize.generate_tokens(readline))
            except:
                self.last_text = None
                raise
        return self.last_tokens

# Create our generate_tokens cache as a callable replacement function.
generate_tokens = CachedTokenizer().generate_tokens


COOKIE_RE = re.compile(r"^[ \t]*#.*coding[:=][ \t]*([-\w.]+)", flags=re.MULTILINE)

@contract(source='bytes')
def source_encoding(source):
    """Determine the encoding for `source`, according to PEP 263.

    `source` is a byte string: the text of the program.

    Returns a string, the name of the encoding.

    """
    readline = iter(source.splitlines(True)).__next__
    return tokenize.detect_encoding(readline)[0]


@contract(source='unicode')
def compile_unicode(source, filename, mode):
    """Just like the `compile` builtin, but works on any Unicode string.

    Python 2's compile() builtin has a stupid restriction: if the source string
    is Unicode, then it may not have a encoding declaration in it.  Why not?
    Who knows!  It also decodes to utf-8, and then tries to interpret those
    utf-8 bytes according to the encoding declaration.  Why? Who knows!

    This function neuters the coding declaration, and compiles it.

    """
    source = neuter_encoding_declaration(source)
    code = compile(source, filename, mode)
    return code


@contract(source='unicode', returns='unicode')
def neuter_encoding_declaration(source):
    """Return `source`, with any encoding declaration neutered."""
    if COOKIE_RE.search(source):
        source_lines = source.splitlines(True)
        for lineno in range(min(2, len(source_lines))):
            source_lines[lineno] = COOKIE_RE.sub("# (deleted declaration)", source_lines[lineno])
        source = "".join(source_lines)
    return source
