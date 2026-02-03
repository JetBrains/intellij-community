# expected default bytes
data = (
  r"\x" # ok
  r"\Q" # ok
  r"\n"
  <info descr="null">ur"<info descr="null">\u0401</info>"</info>
  <info descr="null">ur"<error descr="Invalid escape sequence">\u040z</error>"</info> # fail
)
