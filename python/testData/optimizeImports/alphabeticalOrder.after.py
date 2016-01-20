from __future__ import absolute_import
from __future__ import unicode_literals

import sys
from datetime import timedelta

import # broken
import a
import b
import foo # broken
import z
from
from a import C1
from alphabet import *
from alphabet import B, A
from alphabet import C
from alphabet import D
from b import func
from . import m1
from . import m4, m5
from .pkg import m3
from .. import m2

print(z, b, a, C1, func, sys, abc, foo, timedelta, A, B, C, D, m1, m2, m3, m4, m5)