import re

re.compile('[a-z]')
re.compile('[a-<error descr="'\w' not allowed as end of range">\\w</error>]')
re.compile('[<error descr="'\w' not allowed as start of range">\\w</error>-z]')