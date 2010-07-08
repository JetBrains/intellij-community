status = None
try:
  status = open('/proc/self/status', 'r')
finally:
  if status is not None:
    status.close()