import sys
import os.path
for x in sys.path:
    if x != os.path.dirname(sys.argv [0]) and x != '.': sys.stdout.write(x+chr(10))