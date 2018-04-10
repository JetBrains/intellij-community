while True:
  try:
    print "a"
  finally:
    <error descr="'continue' not supported inside 'finally' clause">continue</error>

for x in [1, 2, 3]:
    pass
else:
    <error descr="'continue' outside loop">continue</error>

while True:
    pass
else:
    <error descr="'continue' outside loop">continue</error>