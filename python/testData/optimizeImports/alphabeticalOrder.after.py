from datetime import timedelta
import sys

import a
from a import C1
import b
from b import func
import foo # broken
import z
from
import # broken

print(z, b, a, C1, func, sys, abc, foo, timedelta)