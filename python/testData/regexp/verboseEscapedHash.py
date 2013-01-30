import re

urlparts = re.compile(
    r"""
        (?P<scheme>http|https)
        ://
        (?P<netloc>(?:(?!/|\?|\#)\S)*)
        /?
        (?P<path>(?:(?!\?|\#)\S)*)
        \??
        (?P<query>(?:(?!\#)\S)*)
        \#?
        (?P<fragment>[\S]*)
    """, re.VERBOSE
)
