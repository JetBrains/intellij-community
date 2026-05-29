x = 1
match x:
    case 1:
        <error descr="lazy imports must appear at module level">lazy</error> import os
    case _:
        <error descr="lazy imports must appear at module level">lazy</error> from os.path import join
