__author__ = 'catherine'

if __name__ == "__main__":
  try:
      from docutils.core import publish_cmdline
  except:
    raise NameError("Cannot find docutils in selected interpreter.")

  import sys
  command = sys.argv[1]
  args = sys.argv[2:]

  COMMANDS = {"rst2html": "html", "rst2latex" : "latex",
      "rst2pseudoxml" : "pseudoxml", "rst2s5" : "s5", "rst2xml" : "xml"}

  if command == "rst2odt":
    from docutils.writers.odf_odt import Writer, Reader
    writer = Writer()
    reader = Reader()
    publish_cmdline(reader=reader, writer=writer, argv=args)
  elif command == "rstpep2html":
    publish_cmdline(reader_name='pep', writer_name='pep_html', argv=args)
  else:
    publish_cmdline(writer_name=COMMANDS[command], argv=args)