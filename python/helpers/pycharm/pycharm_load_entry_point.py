import os, sys
from pkg_resources import load_entry_point

if __name__ == '__main__':
  dist = os.environ.get("PYCHARM_EP_DIST")
  name = os.environ.get("PYCHARM_EP_NAME")
  if dist == "ipython" and name == "ipython":
    from IPython import start_ipython
    f = start_ipython
  else:
    f = load_entry_point(dist, "console_scripts", name)
  sys.exit(f())
