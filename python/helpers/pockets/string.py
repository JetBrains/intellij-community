# -*- coding: utf-8 -*-
# Copyright (c) 2018 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""A pocket full of useful string manipulation tools!"""

from __future__ import absolute_import, print_function

import re

import six

from pockets.collections import is_listy, listify


__all__ = [
    "camel",
    "uncamel",
    "fieldify",
    "unfieldify",
    "sluggify",
    "splitcaps",
    "splitify",
    "UnicodeMixin",
]


# Default regular expression flags
if six.PY2:
    RE_FLAGS = re.L | re.M | re.U
else:
    RE_FLAGS = re.M | re.U

RE_NONWORD = re.compile(r"[\W_]+")

RE_SPLITCAPS = re.compile(
    # Clause 1
    r"[A-Z]+[^a-z]*"  # All non-lowercase beginning with a capital letter
    r"(?=[A-Z][^A-Z]*?[a-z]|$)"  # Followed by a capitalized word
    r"|"
    # Clause 2
    r"[A-Z][^A-Z]*?[a-z]+[^A-Z]*" r"|"  # Capitalized word
    # Clause 3
    r"[^A-Z]+",  # All non-uppercase
    RE_FLAGS,
)

RE_UNCAMEL = re.compile(
    r"("  # The whole expression is in a single group
    # Clause 1
    r"(?<=[^\sA-Z])"  # Preceded by neither a space nor a capital letter
    r"[A-Z]+[^a-z\s]*"  # All non-lowercase beginning with a capital letter
    r"(?=[A-Z][^A-Z\s]*?[a-z]|\s|$)"  # Followed by a capitalized word
    r"|"
    # Clause 2
    r"(?<=[^\s])"  # Preceded by a character that is not a space
    r"[A-Z][^A-Z\s]*?[a-z]+[^A-Z\s]*"  # Capitalized word
    r")",
    RE_FLAGS,
)

RE_WHITESPACE_GROUP = re.compile(r"(\s+)", RE_FLAGS)


def camel(
    s, sep="_", lower_initial=False, upper_segments=None, preserve_upper=False
):
    """
    Convert underscore_separated string (aka snake_case) to CamelCase.

    Works on full sentences as well as individual words:

    >>> camel("hello_world!")
    'HelloWorld!'
    >>> camel("Totally works as_expected, even_with_whitespace!")
    'Totally Works AsExpected, EvenWithWhitespace!'

    Args:
        sep (string, optional): Delineates segments of `s` that will be
            CamelCased. Defaults to an underscore "_".

            For example, if you want to CamelCase a dash separated word:

            >>> camel("xml-http-request", sep="-")
            'XmlHttpRequest'

        lower_initial (bool, int, or list, optional): If True, the initial
            character of each camelCased word will be lowercase. If False, the
            initial character of each CamelCased word will be uppercase.
            Defaults to False:

            >>> camel("http_request http_response")
            'HttpRequest HttpResponse'
            >>> camel("http_request http_response", lower_initial=True)
            'httpRequest httpResponse'

            Optionally, `lower_initial` can be an int or a list of ints,
            indicating which individual segments of each CamelCased word
            should start with a lowercase. Supports negative numbers to index
            segments from the right:

            >>> camel("xml_http_request", lower_initial=0)
            'xmlHttpRequest'
            >>> camel("xml_http_request", lower_initial=-1)
            'XmlHttprequest'
            >>> camel("xml_http_request", lower_initial=[0, 1])
            'xmlhttpRequest'

        upper_segments (int or list, optional): Indicates which segments of
           CamelCased words should be fully uppercased, instead of just
           capitalizing the first letter.

           Can be an int, indicating a single segment, or a list of ints,
           indicating multiple segments. Supports negative numbers to index
           segments from the right.

           `upper_segments` is helpful when dealing with acronyms:

            >>> camel("tcp_socket_id", upper_segments=0)
            'TCPSocketId'
            >>> camel("tcp_socket_id", upper_segments=[0, -1])
            'TCPSocketID'
            >>> camel("tcp_socket_id", upper_segments=[0, -1], lower_initial=1)
            'TCPsocketID'

        preserve_upper (bool): If True, existing uppercase characters will
            not be automatically lowercased. Defaults to False.

            >>> camel("xml_HTTP_reQuest")
            'XmlHttpRequest'
            >>> camel("xml_HTTP_reQuest", preserve_upper=True)
            'XmlHTTPReQuest'

    Returns:
        str: CamelCased version of `s`.

    """
    if isinstance(lower_initial, bool):
        lower_initial = [0] if lower_initial else []
    else:
        lower_initial = listify(lower_initial)
    upper_segments = listify(upper_segments)
    result = []
    for word in RE_WHITESPACE_GROUP.split(s):
        segments = [segment for segment in word.split(sep) if segment]
        count = len(segments)
        for i, segment in enumerate(segments):
            upper = i in upper_segments or (i - count) in upper_segments
            lower = i in lower_initial or (i - count) in lower_initial
            if upper and lower:
                if preserve_upper:
                    segment = segment[0] + segment[1:].upper()
                else:
                    segment = segment[0].lower() + segment[1:].upper()
            elif upper:
                segment = segment.upper()
            elif lower:
                if not preserve_upper:
                    segment = segment.lower()
            elif preserve_upper:
                segment = segment[0].upper() + segment[1:]
            else:
                segment = segment[0].upper() + segment[1:].lower()
            result.append(segment)

    return "".join(result)


def uncamel(s, sep="_"):
    """
    Convert CamelCase string to underscore_separated (aka snake_case).

    A CamelCase word is considered to be any uppercase letter followed by zero
    or more lowercase letters. Contiguous groups of uppercase letters – like
    you would find in an acronym – are also considered part of a single word:

    >>> uncamel("Request")
    'request'
    >>> uncamel("HTTP")
    'http'
    >>> uncamel("HTTPRequest")
    'http_request'
    >>> uncamel("xmlHTTPRequest")
    'xml_http_request'

    Works on full sentences as well as individual words:

    >>> uncamel("HelloWorld!")
    'hello_world!'
    >>> uncamel("Totally works AsExpected, EvenWithWhitespace!")
    'totally works as_expected, even_with_whitespace!'

    Args:
        sep (str, optional): String used to separate CamelCase words. Defaults
            to an underscore "_".

            For example, if you want dash separated words:

            >>> uncamel("XmlHttpRequest", sep="-")
            'xml-http-request'

    Returns:
        str: uncamel_cased version of `s`.

    """
    return RE_UNCAMEL.sub(r"{0}\1".format(sep), s).lower()


def fieldify(s, sep="_"):
    """
    Convert a string into a valid "field-like" variable name.

    Converts `s` from camel case to underscores, and replaces all spaces and
    non-word characters with `sep`:

    >>> fieldify('The XmlHTTPRequest Contained, "DATA..."')
    'the_xml_http_request_contained_data'

    Args:
        s (str): The string to fieldify.

        sep (str): The string to use as a word separator in the returned field.
            Defaults to '_'.

    Returns:
        str: The field version of `s`.

    """
    if not s:
        return ""
    return RE_NONWORD.sub(sep, uncamel(s)).strip(sep)


def unfieldify(s, sep="_"):
    """
    Makes a best effort to reverse the algorithm from `fieldify`.

    Replaces instances of `sep` in `s` with a space and converts the result to
    title case:

    >>> unfieldify('the_xml_http_request_contained_data')
    'The Xml Http Request Contained Data'

    Args:
        s (str): The string to fieldify.

        sep (str): The string to consider a word separator in `s`.
            Defaults to '_'.

    Returns:
        str: The unfieldified version of `s`.

    """
    if not s:
        return ""
    s = s.strip(r"{0} ".format(sep))
    return (" ".join([w for w in s.split(sep) if w])).title()


def sluggify(s, sep="-"):
    """
    Convert a string into a "slug" suitable for use in a URL.

    Converts `s` to lower case, and replaces all spaces and non-word
    characters with `sep`:

    >>> sluggify('The ANGRY Wizard Shouted, "HEY..."')
    'the-angry-wizard-shouted-hey'

    Args:
        s (str): The string to convert into a slug.

        sep (str): The string to use as a word separator in the slug.
            Defaults to '-'.

    Returns:
        str: The sluggify version of `s`.

    """
    if not s:
        return ""
    return RE_NONWORD.sub(sep, s).lower().strip(sep)


def splitcaps(s, pattern=None, maxsplit=None, flags=0):
    """
    Intelligently split a string on capitalized words.

    A capitalized word is considered to be any uppercase letter followed by
    zero or more lowercase letters. Contiguous groups of uppercase letters –
    like you would find in an acronym – are also considered part of a single
    word:

    >>> splitcaps("Request")
    ['Request']
    >>> splitcaps("HTTP")
    ['HTTP']
    >>> splitcaps("HTTPRequest")
    ['HTTP', 'Request']
    >>> splitcaps("HTTP/1.1Request")
    ['HTTP/1.1', 'Request']
    >>> splitcaps("xmlHTTPRequest")
    ['xml', 'HTTP', 'Request']

    If no capitalized words are found in `s`, the whole string is
    returned in a single element list:

    >>> splitcaps("")
    ['']
    >>> splitcaps("lower case words")
    ['lower case words']

    Does not split on whitespace by default. To also split
    on whitespace, pass "\\\\s+" for `pattern`:

    >>> splitcaps("Without whiteSpace pattern")
    ['Without white', 'Space pattern']
    >>> splitcaps("With whiteSpace pattern", pattern=r"\\s+")
    ['With', 'white', 'Space', 'pattern']
    >>> splitcaps("With whiteSpace group", pattern=r"(\\s+)")
    ['With', ' ', 'white', 'Space', ' ', 'group']

    Args:
        s (str): The string to split.

        pattern (str, optional): In addition to splitting on capital letters,
            also split by the occurrences of `pattern`. If capturing
            parentheses are used in `pattern`, then the text of all groups in
            `pattern` are also returned as part of the resulting list.
            Defaults to None.

        maxsplit (int, optional):  If maxsplit is not specified or -1, then
            there is no limit on the number of splits (all possible splits are
            made). If maxsplit is >= 0, at most maxsplit splits occur, and the
            remainder of the string is returned as the final element of the
            list.

        flags (int, optional): Flags to pass to the regular expression created
            using `pattern`. Ignored if `pattern` is not specified. Defaults
            to (re.LOCALE | re.MULTILINE | re.UNICODE).

    Returns:
        list: List of capitalized substrings in `s`.

    """
    if not maxsplit:
        if maxsplit == 0:
            return [s]
        else:
            maxsplit = -1

    if pattern:
        pattern_re = re.compile(pattern, flags or RE_FLAGS)
    else:
        pattern_re = None

    result = []
    post_maxsplit = []
    for m in RE_SPLITCAPS.finditer(s):
        if pattern_re:
            for segment in pattern_re.split(m.group()):
                if segment:
                    if maxsplit > 0 and len(result) >= maxsplit:
                        post_maxsplit.append(segment)
                    else:
                        result.append(segment)
        else:
            result.append(m.group())

        if maxsplit > 0 and len(result) >= maxsplit:
            if m.end() < len(s):
                post_maxsplit.append(s[m.end() :])
            post_maxsplit = "".join(post_maxsplit)
            if post_maxsplit:
                result.append(post_maxsplit)
            break

    return result if len(result) > 0 else [s]


def splitify(value, separator=",", strip=True, include_empty=False):
    """
    Convert a value to a list using a supercharged `split()`.

    If `value` is a string, it is split by `separator`. If `separator` is
    `None` or empty, no attempt to split is made, and `value` is returned as
    the only item in a list.

    If `strip` is `True`, then the split strings will be stripped of
    whitespace. If `strip` is a string, then the split strings will be
    stripped of the given string.

    If `include_empty` is `False`, then empty split strings will not be
    included in the returned list.

    If `value` is `None` an empty list is returned.

    If `value` is already "listy", it is returned as-is.

    If `value` is any other type, it is returned as the only item in a list.

    >>> splitify("first item, second item")
    ['first item', 'second item']
    >>> splitify("first path: second path: :skipped empty path", ":")
    ['first path', 'second path', 'skipped empty path']
    >>> splitify(["already", "split"])
    ['already', 'split']
    >>> splitify(None)
    []
    >>> splitify(1969)
    [1969]
    """
    if is_listy(value):
        return value

    if isinstance(value, str) and separator:
        parts = value.split(separator)
        if strip:
            strip = None if strip is True else strip
            parts = [s.strip(strip) for s in parts]
        return [s for s in parts if include_empty or s]
    return listify(value)


class UnicodeMixin(object):
    """
    Mixin class to define proper __str__/__unicode__ methods in Python 2 or 3.

    Originally found on the `Porting Python 2 Code to Python 3 HOWTO`_.

    .. _Porting Python 2 Code to Python 3 HOWTO:
       https://docs.python.org/3.3/howto/pyporting.html

    """

    if six.PY2:

        def __str__(self):
            return self.__unicode__().encode("utf8")

    else:

        def __str__(self):
            return self.__unicode__()
