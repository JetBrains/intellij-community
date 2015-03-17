import root                         # add root
from . import module2a              # add pkg1.pkg2.module2a
from pkg1 import module1a           # add pkg1.module1a
from .module2b import C1            # add pkg1.pkg2.module2b.C1
from pkg1.module1b import B1        # add pkg1.module1b.B1
from pkg1.pkg2.module2b import *    # add pkg1.pkg2.module2b
from pkg1.pkg2 import               # add pkg1.pkg2
