__author__ = 'catherine'

if __name__ == "__main__":
  try:
    from sphinx import cmdline
  except:
    raise NameError("Cannot find sphinx in selected interpreter.")

  import sys
  cmdline.main(sys.argv)