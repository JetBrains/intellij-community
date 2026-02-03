import re

some_re = re.compile(r"""
(?P<abc>abc)
(?P<name>
  (?(abc)yes|noo)
)
""", re.VERBOSE)