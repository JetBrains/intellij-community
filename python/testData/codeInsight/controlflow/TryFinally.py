status = None
try:
  status = open('/proc/self/status', 'r')
finally:
  if status is not None:
    print('opened: %r' % status)
status.close()
