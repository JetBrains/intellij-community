from module import foo
from module import foo, bar
from module import foo, bar,
#                            | margin
from module import foo, bar, baz
from module import foo, \
    bar
from module import foo, \
    bar,

from module import foo, \
    bar  # comment

from module import (foo,
                    bar)

from module import (foo,
                    bar,)

from module import (
    foo,
    bar # comment
)