f1 = f"<warning descr="Invalid escape sequence '\.'">\.</warning>"

f2 = f"\a \b \f \n \r \t \v \\ \' \" \0 \x41 \u1234 \U00012345 \N{SNOWMAN}"

f3 = f"foo {1} <warning descr="Invalid escape sequence '\ '">\ </warning>"

f4 = f"foo { f'<warning descr="Invalid escape sequence '\.'">\.</warning>' }"

f5 = f"level1 { f'level2 { f"level3 <warning descr="Invalid escape sequence '\.'">\.</warning>" }' } <warning descr="Invalid escape sequence '\.'">\.</warning>"

f6 = f"valid \n invalid <warning descr="Invalid escape sequence '\.'">\.</warning> valid \t"

f7 = f"{{ <warning descr="Invalid escape sequence '\.'">\.</warning> }}"

f8 = f"<warning descr="Invalid escape sequence '\{'">\{</warning>{"

f9 = f"""
  <warning descr="Invalid escape sequence '\.'">\.</warning>
  {1}
  valid \n
  <warning descr="Invalid escape sequence '\z'">\z</warning>
"""

f10 = f"text <warning descr="Invalid escape sequence '\.'">\.</warning> { '\n' } text"

f11 = f"list {[1, 2]} set { {1, 2} } dict { {'k': 'v'} } <warning descr="Invalid escape sequence '\.'">\.</warning>"

# \n and \x are escape sequences, while \z is not:
f12 = f"expression { 1 + \
n } <warning descr="Invalid escape sequence '\.'">\.</warning>"

f13 = f"nested { f'inner { 1 + \
x }' } "

f16 = f"expression { 1 + \
z } <warning descr="Invalid escape sequence '\.'">\.</warning>"

f14 = f" { '\n' } { '<warning descr="Invalid escape sequence '\.'">\.</warning>' } "

f15 = f" { {'key': '\n', 'val': '<warning descr="Invalid escape sequence '\.'">\.</warning>'} } "

rf1 = rf"\."
rf2 = rf"foo {1} \."
rf3 = fr"foo {1} \."
