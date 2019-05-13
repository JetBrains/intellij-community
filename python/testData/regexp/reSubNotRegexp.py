import re
r = re.compile(r'[123]')
r.sub('?', '1234')