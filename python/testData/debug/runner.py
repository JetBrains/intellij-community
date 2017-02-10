import runpy
import sys

print(sys.argv)

x = 1
program = sys.argv[1]
sys.argv = sys.argv[1:]


runpy.run_path(program)