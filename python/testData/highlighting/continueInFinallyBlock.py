while True:
  try:
    print "a"
  finally:
    <error descr="'continue' not supported inside 'finally' clause">continue</error>