#!/usr/bin/python

# comment for b
import b
# comment for a
import a  # trailing comment for normal import 

# comment for c, d
import d, c

# comment for name2
from mod import name2
# comment for name1 and name3
from mod import name1, name3  # trailing comment for "from" import

# comment for star import
from mod2 import *

print(a, b, c, d, name1, name2, name3)
