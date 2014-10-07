"""Skeleton for 're' stdlib module."""


def compile(pattern, flags=0):
    """Compile a regular expression pattern, returning a pattern object.

    :type pattern: bytes | unicode
    :type flags: int
    :rtype: __Regex
    """
    pass


def search(pattern, string, flags=0):
    """Scan through string looking for a match, and return a corresponding
    match instance. Return None if no position in the string matches.

    :type pattern: bytes | unicode | __Regex
    :type string: T <= bytes | unicode
    :type flags: int
    :rtype: __Match[T] | None
    """
    pass


def match(pattern, string, flags=0):
    """Matches zero or more characters at the beginning of the string.

    :type pattern: bytes | unicode | __Regex
    :type string: T <= bytes | unicode
    :type flags: int
    :rtype: __Match[T] | None
    """
    pass


def split(pattern, string, maxsplit=0, flags=0):
    """Split string by the occurrences of pattern.

    :type pattern: bytes | unicode | __Regex
    :type string: T <= bytes | unicode
    :type maxsplit: int
    :type flags: int
    :rtype: list[T]
    """
    pass


def findall(pattern, string, flags=0):
    """Return a list of all non-overlapping matches of pattern in string.

    :type pattern: bytes | unicode | __Regex
    :type string: T <= bytes | unicode
    :type flags: int
    :rtype: list[T]
    """
    pass


def finditer(pattern, string, flags=0):
    """Return an iterator over all non-overlapping matches for the pattern in
    string. For each match, the iterator returns a match object.

    :type pattern: bytes | unicode | __Regex
    :type string: T <= bytes | unicode
    :type flags: int
    :rtype: collections.Iterable[__Match[T]]
    """
    pass


def sub(pattern, repl, string, count=0, flags=0):
    """Return the string obtained by replacing the leftmost non-overlapping
    occurrences of pattern in string by the replacement repl.

    :type pattern: bytes | unicode | __Regex
    :type repl: bytes | unicode | collections.Callable
    :type string: T <= bytes | unicode
    :type count: int
    :type flags: int
    :rtype: T
    """
    pass


def subn(pattern, repl, string, count=0, flags=0):
    """Return the tuple (new_string, number_of_subs_made) found by replacing
    the leftmost non-overlapping occurrences of pattern with the
    replacement repl.

    :type pattern: bytes | unicode | __Regex
    :type repl: bytes | unicode | collections.Callable
    :type string: T <= bytes | unicode
    :type count: int
    :type flags: int
    :rtype: (T, int)
    """
    pass


def escape(string):
    """Escape all the characters in pattern except ASCII letters and numbers.

    :type string: T <= bytes | unicode
    :type: T
    """
    pass


class __Regex(object):
    """Mock class for a regular expression pattern object."""

    def __init__(self, flags, groups, groupindex, pattern):
        """Create a new pattern object.

        :type flags: int
        :type groups: int
        :type groupindex: dict[bytes | unicode, int]
        :type pattern: bytes | unicode
        """
        self.flags = flags
        self.groups = groups
        self.groupindex = groupindex
        self.pattern = pattern

    def search(self, string, pos=0, endpos=-1):
        """Scan through string looking for a match, and return a corresponding
        match instance. Return None if no position in the string matches.

        :type string: T <= bytes | unicode
        :type pos: int
        :type endpos: int
        :rtype: __Match[T] | None
        """
        pass

    def match(self, string, pos=0, endpos=-1):
        """Matches zero | more characters at the beginning of the string.

        :type string: T <= bytes | unicode
        :type pos: int
        :type endpos: int
        :rtype: __Match[T] | None
        """
        pass

    def split(self, string, maxsplit=0):
        """Split string by the occurrences of pattern.

        :type string: T <= bytes | unicode
        :type maxsplit: int
        :rtype: list[T]
        """
        pass

    def findall(self, string, pos=0, endpos=-1):
        """Return a list of all non-overlapping matches of pattern in string.

        :type string: T <= bytes | unicode
        :type pos: int
        :type endpos: int
        :rtype: list[T]
        """
        pass

    def finditer(self, string, pos=0, endpos=-1):
        """Return an iterator over all non-overlapping matches for the
        pattern in string. For each match, the iterator returns a
        match object.

        :type string: T <= bytes | unicode
        :type pos: int
        :type endpos: int
        :rtype: collections.Iterable[__Match[T]]
        """
        pass

    def sub(self, repl, string, count=0):
        """Return the string obtained by replacing the leftmost non-overlapping
        occurrences of pattern in string by the replacement repl.

        :type repl: bytes | unicode | collections.Callable
        :type string: T <= bytes | unicode
        :type count: int
        :rtype: T
        """
        pass

    def subn(self, repl, string, count=0):
        """Return the tuple (new_string, number_of_subs_made) found by replacing
        the leftmost non-overlapping occurrences of pattern with the
        replacement repl.

        :type repl: bytes | unicode | collections.Callable
        :type string: T <= bytes | unicode
        :type count: int
        :rtype: (T, int)
        """
        pass


class __Match(object):
    """Mock class for a match object."""

    def __init__(self, pos, endpos, lastindex, lastgroup, re, string):
        """Create a new match object.

        :type pos: int
        :type endpos: int
        :type lastindex: int | None
        :type lastgroup: int | bytes | unicode | None
        :type re: __Regex
        :type string: bytes | unicode
        :rtype: __Match[T]
        """
        self.pos = pos
        self.endpos = endpos
        self.lastindex = lastindex
        self.lastgroup = lastgroup
        self.re = re
        self.string = string

    def expand(self, template):
        """Return the string obtained by doing backslash substitution on the
        template string template.

        :type template: T
        :rtype: T
        """
        pass

    def group(self, *args):
        """Return one or more subgroups of the match.

        :rtype: T | tuple
        """
        pass

    def groups(self, default=None):
        """Return a tuple containing all the subgroups of the match, from 1 up
        to however many groups are in the pattern.

        :rtype: tuple
        """
        pass

    def groupdict(self, default=None):
        """Return a dictionary containing all the named subgroups of the match,
        keyed by the subgroup name.

        :rtype: dict[bytes | unicode, T]
        """
        pass

    def start(self, group=0):
        """Return the index of the start of the substring matched by group.

        :type group: int | bytes | unicode
        :rtype: int
        """
        pass

    def end(self, group=0):
        """Return the index of the end of the substring matched by group.

        :type group: int | bytes | unicode
        :rtype: int
        """
        pass

    def span(self, group=0):
        """Return a 2-tuple (start, end) for the substring matched by group.

        :type group: int | bytes | unicode
        :rtype: (int, int)
        """
        pass
