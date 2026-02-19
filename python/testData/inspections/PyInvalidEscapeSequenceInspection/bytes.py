b1 = b"<warning descr="Invalid escape sequence '\.'">\.</warning>"

b2 = b"<warning descr="Invalid escape sequence '\z'">\z</warning>"

b3 = b"\a \b \f \n \r \t \v \\ \' \" \0 \x41 \u1234 \U00012345 \N{SNOWMAN}"

b4 = rb"\."

b5 = b"  <warning descr="Invalid escape sequence '\.'">\.</warning>  <warning descr="Invalid escape sequence '\z'">\z</warning>  "
