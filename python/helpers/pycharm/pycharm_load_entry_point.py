import os, sys
from pkg_resources import load_entry_point

if __name__ == '__main__':
  dist = os.environ.get("PYCHARM_EP_DIST")
  name = os.environ.get("PYCHARM_EP_NAME")
  sys.exit(
    load_entry_point(dist, "console_scripts", name)()
  )
