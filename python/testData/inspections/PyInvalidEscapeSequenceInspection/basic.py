s1 = "<warning descr="Invalid escape sequence '\.'">\.</warning>"

s2 = "<warning descr="Invalid escape sequence '\z'">\z</warning>"

s3 = "\a \b \f \n \r \t \v \\ \' \" \0 \x41 \u1234 \U00012345 \N{SNOWMAN}"

s4 = r"\."

s5 = "  <warning descr="Invalid escape sequence '\.'">\.</warning>  <warning descr="Invalid escape sequence '\z'">\z</warning>  "
