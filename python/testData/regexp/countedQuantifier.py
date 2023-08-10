import re

re.compile(r'a{<error descr="Repetition value too large">4294967295</error>}')
re.compile(r'a{4294967294}')
re.compile(r'a{<error descr="Repetition value too large">4294967295</error>,1}')
re.compile(r'a{1,<error descr="Repetition value too large">4294967295</error>}')
re.compile(r'a{<error descr="Illegal repetition range (min > max)">2,1</error>}')
re.compile(r'a{1,2}<error descr="Nested quantifier in regexp">+</error>')
re.compile(r'a<weak_warning descr="'{,}' can be simplified to '*'">{,}</weak_warning>')