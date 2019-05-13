#!/usr/bin/python

# comment for a
import a  # trailing comment for normal import 
# comment for b
import b
# comment for c, d
import c  # trailing comment for c, d
import d
# comment for name1 and name3
# comment for name2
from mod import name1, name2, name3  # trailing comment for name1, name3; trailing comment for name2
# comment for star import
from mod2 import *

print(a, b, c, d, name1, name2, name3)
