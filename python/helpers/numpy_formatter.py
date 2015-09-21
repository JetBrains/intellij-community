import sys

from sphinxcontrib.napoleon.docstring import NumpyDocstring

import rest_formatter


def main(text=None):
  try:
    src = sys.stdin.read() if text is None else text
    import textwrap
    rest_formatter.main(str(NumpyDocstring(textwrap.dedent(src))))
  except:
    exc_type, exc_value, exc_traceback = sys.exc_info()
    sys.stderr.write("Error calculating docstring: " + str(exc_value))


if __name__ == '__main__':
  main()
