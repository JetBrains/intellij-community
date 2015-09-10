from __future__ import absolute_import
from __future__ import unicode_literals

import sys
from datetime import timedelta

import a
import b
import foo # broken
import z
import # broken
from a import C1
from alphabet import *
from alphabet import A
from alphabet import B, A
from alphabet import C
from alphabet import D
from b import func
from

print(z, b, a, C1, func, sys, abc, foo, timedelta, A, B, C, D)