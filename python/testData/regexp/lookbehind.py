import re

re.compile(r"(?<!a|b)");
re.compile(r"(?<!<error descr="Alternation alternatives need to have the same length inside lookbehind">a|bc</error>)");
re.compile(r"(?<!a{3}})");
re.compile(r"(?<!a{3,3}})");
re.compile(r"(?<!a<error descr="Unequal min and max in counted quantifier not allowed inside lookbehind">{3,4}</error>})");
re.compile(r"(?<!a|b{1})")
re.compile(r"(?<!abcd|(ab){2})")
re.compile(r"(?<!abcde|x(ab){2})")
re.compile(r"(?<!a(ab|cd)|xyz)")
re.compile(r'(?<!aa|b[cd]|ee)str')