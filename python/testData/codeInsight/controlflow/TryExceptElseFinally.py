d = dict()
try:
    v = d['key']
except KeyError:
    print 'element not found'
else:
    print 'element value {0}'.format(v)
finally:
    print 'excuting finally clause'