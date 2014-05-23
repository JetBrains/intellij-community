import sys
from pkg_resources import load_entry_point

if __name__ == '__main__':
  name = sys.argv.pop()
  dist = sys.argv.pop()
  sys.exit(
    load_entry_point(dist, "console_scripts", name)()
  )
