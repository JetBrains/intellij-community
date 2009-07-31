'#%(language)s has %(#)03d quote types.' % {'language': "Python", "#": 2}  #ok
'%d %s' % 5  #Too few arguments for format string
'Hello world' % 25 #Too many arguments for format string
"%(name)f(name)" % {'name': 23.2} #ok
"%()s" % {'': "name"} #ok
'test%(name)' % {'name': 23} #There are no format specifier character
'work%*d' % (2, 34) #ok
'work%(name)*d' % (12, 32) #Can't use '*' in formats when using a mapping
'%*.*d' % (2, 5, 5) #ok
'%*.*d' % (2, 4) #Too few arguments for format string
'%*.*d' % (2, 4, 5, 6) #Too many arguments for format string
'%**d' % (2, 5) #There are no format specifier character
'%(name1)s %(name2)s (name3) %s' % {'name1': 'a', 'name2': 'b', 'name3': 'c'} #Too few mapping keys
'%(name1s' % {'name1': 'a'} #Too few mapping keys
'%%%(name)ld' % {'name': 12} #ok
"%(name)f(name)" % 23.2 #Format requires a mapping
"%(name)f(name)" % (23.2) #Format requires a mapping
'%d%d' % {'name1': 2, 'name2': 3} #Format doesn't require a mapping
'%12.2f' % 2.74 #ok
'Hello world' % () #ok
'Hello world' % [] #ok
'Hello world' % {} #ok
'%d%d' % ((5), (5)) #ok
"%(name)d %(name)d" % {"name": 43} #ok
"%(name)d" % {'a': 4, "name": 5} #ok
'%% name %(name)c' % {'a': 4} #One of keys has no following argument
'%d %u %f %F %s %r' % (2, 3, 4.1, 4.0, "name", "str") #ok
'%d %s %f' % (4, 5, 6) #Unexpected type
'%d %s %f' % (4, 5, 6.1) #Unexpected type
'%d' % "name" #Unexpected type
m = {'language': "Python", "#": 2}
'#%(language)s has %(#)03d quote types.' % m  #ok
i = "test"
'%(name)s' % {'name': i}  #ok
'%s' % i  #ok
'%f' % i  #Unexpected type
