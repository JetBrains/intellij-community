from __future__ import unicode_literals
from __future__ import absolute_import

import z
import b
import a
from a import C1
from alphabet import D
from alphabet import A
from b import func
from import foo # broken
import sys
from . import m1
from datetime import timedelta
import # broken
from alphabet import *
from .. import m2
from alphabet import C
from alphabet import B, A
from .pkg import m3
from . import m4, m5

print(z, b, a, C1, func, sys, abc, foo, timedelta, A, B, C, D, m1, m2, m3, m4, m5)