import re
re.match(r'<error descr="Named Unicode characters are not allowed in this regex dialect">\N{LESS-THAN SIGN}</error>', '<')
re.match(r'<error descr="Named Unicode characters are not allowed in this regex dialect">\N{LESS-THAN SIG}</error>', '<')